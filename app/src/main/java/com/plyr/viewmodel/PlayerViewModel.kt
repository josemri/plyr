package com.plyr.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.plyr.database.TrackEntity
import com.plyr.network.YouTubeManager
import com.plyr.utils.Config
import com.plyr.utils.DownloadedAudioStore
import com.plyr.utils.Translations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import androidx.annotation.OptIn

/**
 * Reproductor de audio.
 *
 * La cola (`queue` + `currentIndex`) es la única fuente de verdad. ExoPlayer
 * mantiene una **ventana deslizante** de la cola ya resuelta (la canción actual
 * y las siguientes), de forma que el salto a la siguiente canción no depende de
 * una carga en segundo plano indeterminada: el item destino ya está preparado
 * cuando el actual termina.
 *
 * Toda transición pasa por [QueueIndex], que decide el índice destino según el
 * modo de repetición y devuelve `null` cuando la cola ha terminado.
 *
 * Los índices encolados nunca se aplican si la cola cambió mientras se
 * resolvían sus URLs: [generation] se incrementa en cada cambio y los resultados
 * obsoletos se descartan.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Canciones resueltas y preparadas por delante de la actual. */
        const val WINDOW_AHEAD = 2

        /** Extracciones de URL simultáneas al rellenar la ventana. */
        const val RESOLVE_CONCURRENCY = 2

        /** Fallos consecutivos tolerados antes de detener la cola. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /** Canciones que se pueden saltar por no resolverse antes de parar. */
        const val MAX_RESOLUTION_SKIPS = 5

        /**
         * Items que se dejan preparados detrás de la canción actual.
         *
         * Con uno basta para que `<<` sea un salto inmediato, y el coste es un
         * item resuelto de más: la ventana crece como mucho en uno respecto a
         * antes.
         */
        const val KEEP_BEHIND = 1

        /**
         * Intentos seguidos para rellenar un tramo de la ventana.
         *
         * Con uno solo, una caída puntual de red dejaba la ventana sin item
         * siguiente y no había nada que la volviera a intentar hasta la
         * siguiente transición (B57).
         */
        const val MAX_FILL_ATTEMPTS = 2

        /** Espera entre intentos de relleno fallidos. */
        const val FILL_RETRY_DELAY_MS = 400L

        /** Profundidad máxima al recorrer la cadena de causas de un error. */
        const val MAX_CAUSE_DEPTH = 5
    }

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _currentTitle = MutableLiveData<String?>()
    val currentTitle: LiveData<String?> = _currentTitle

    private val _currentPlaylist = MutableLiveData<List<TrackEntity>?>()
    val currentPlaylist: LiveData<List<TrackEntity>?> = _currentPlaylist

    private val _currentTrackIndex = MutableLiveData(0)
    val currentTrackIndex: LiveData<Int> = _currentTrackIndex

    private val _currentTrack = MutableLiveData<TrackEntity?>()
    val currentTrack: LiveData<TrackEntity?> = _currentTrack

    var onMediaSessionUpdate: ((ExoPlayer) -> Unit)? = null

    // --- Estado autoritativo (hilo principal) ---

    private var queue: List<TrackEntity> = emptyList()
    private var currentIndex: Int = -1

    /** Estado de la ventana deslizante. */
    private var windowState: WindowState = WindowState.EMPTY

    /** Índice de cola del primer item de la ventana que tiene ExoPlayer. */
    private val windowStart: Int
        get() = windowState.startIndex

    /** Se incrementa en cada cambio de cola; invalida resoluciones en vuelo. */
    private val generation: Int
        get() = windowState.generation

    private var consecutiveFailures: Int = 0

    /**
     * Transiciones en vuelo: una operación que va a mover la cola y el
     * reproductor (`playIndex`, `startAt`) entre que empieza y termina.
     *
     * Mientras hay una, un salto todavía no se puede calcular: la cola aún no
     * se ha movido. Antes se descartaba en silencio, y como la sesión ya le
     * había devuelto `RESULT_INFO_SKIPPED` al sistema, no lo hacía nadie
     * (B59). Ahora se apunta en [pendingSkips] y se aplica al terminar.
     *
     * Mecánica de tokens igual que la de [loading]: solo quien empieza la
     * transición la retira, y `supersede()` se lleva por delante las de una
     * operación anterior cuando otra se hace cargo. Un `Boolean` puesto a
     * `false` desde fuera de su propia transición habría soltado el candado
     * mientras otra seguía trabajando, y habría vuelto a perder saltos.
     */
    private val transitions = LoadingState()

    /** Si hay alguna transición empezada y no terminada. */
    private val transitionInFlight: Boolean
        get() = transitions.isLoading

    private fun beginTransition(): Int = transitions.begin()

    private fun endTransition(token: Int) = transitions.end(token)

    /**
     * Saltos que llegaron mientras había una transición en vuelo. Ver
     * [PendingSkips]: la sesión ya los había dado por atendidos, así que
     * soltarlos era no hacerlos nunca (B59).
     */
    private val pendingSkips = PendingSkips()

    /**
     * Operaciones de carga en vuelo. Ver [LoadingState].
     *
     * Antes era un `Boolean` suelto que solo cerraba quien lo había puesto y
     * solo si su `generation` seguía vigente: cualquier `generation++` ajeno
     * mientras se resolvía lo dejaba clavado en `true`, y con él el spinner y
     * todos los controles deshabilitados (B58).
     */
    private val loading = LoadingState()
    private var currentVideoId: String? = null
    private var queueRepeatMode: String = Config.REPEAT_MODE_OFF

    /**
     * Video de YouTube realmente usado por cada pista, indexado por `track.id`.
     *
     * Para las pistas resueltas por búsqueda, `track.youtubeVideoId` es `null`,
     * así que aquí es donde queda el id que sirvió la URL que se acaba de
     * caducar: `invalidate()` no llegaba a llamarse y el reintento recibía de la
     * caché la misma URL muerta (B4).
     */
    private val resolvedVideoId = ConcurrentHashMap<String, String>()

    /**
     * Relleno de ventana en curso, si lo hay.
     *
     * `growWindow()` se llama varias veces por transición (al saltar y al entrar
     * en el item) y además al encolar. Antes, cada llamada cancelaba la anterior
     * y relanzaba el mismo trabajo (B5, y luego B57): cancelar se llevaba por
     * delante la extracción que iba justo por la canción siguiente y, como
     * `YouTubeManager` repartía ese `null` entre quienes esperaban, el relleno
     * nuevo la daba por perdida y no añadía nada a la ventana.
     *
     * Ahora solo se lanza uno si no hay otro vivo, y el que está en marcha no se
     * toca: su bucle vuelve a mirar al terminar qué queda por rellenar, así que
     * el trabajo ya hecho sigue valiendo y el tramo nuevo se recoge después.
     */
    private var prefetchJob: Job? = null

    /**
     * Resolución de una canción pedida por la UI ([playTrack]). Vive en el
     * scope del ViewModel, no en el de la pantalla que la pidió: navegar
     * fuera no debe cancelarla (B60).
     */
    private var playbackJob: Job? = null

    // ------------------------------------------------------------------ //
    // Ciclo de vida del reproductor
    // ------------------------------------------------------------------ //

    fun initializePlayer() {
        if (_exoPlayer == null) {
            queueRepeatMode = Config.getRepeatMode(getApplication())
            _exoPlayer = buildPlayer()
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(getApplication())
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build().apply {
                // El salto entre canciones lo decide [QueueIndex], no ExoPlayer.
                // Solo el modo "repetir uno" se delega, porque así el repeat es
                // continuo y sin cortes.
                repeatMode = if (queueRepeatMode == Config.REPEAT_MODE_ONE) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateLoadingState()
                        if (playbackState == Player.STATE_ENDED) {
                            onTrackEnded()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updateLoadingState()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (mediaItem == null || this@apply.mediaItemCount == 0) return
                        syncIndexFromWindow()
                        publishCurrentTrack()
                        // Un item arranca a reproducirse: el error anterior era
                        // de la pista que ya no suena, así que se retira. Sin
                        // esto el mensaje se quedaba pegado y los controles
                        // quedaban deshabilitados para siempre (B6).
                        _error.publish(null)
                        onMediaSessionUpdate?.invoke(this@apply)

                        // Transición automática: es el momento de reponer la
                        // ventana para que la siguiente canción esté lista.
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            consecutiveFailures = 0
                            growWindow()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        updateLoadingState()
                        onItemUnplayable(error)
                    }
                })
            }

    override fun onCleared() {
        prefetchJob?.cancel()
        prefetchJob = null
        _exoPlayer?.release()
        _exoPlayer = null
        super.onCleared()
    }

    // ------------------------------------------------------------------ //
    // Cola
    // ------------------------------------------------------------------ //

    /**
     * Define la cola a reproducir y la posición de inicio.
     * Las resoluciones en vuelo se invalidan: sus resultados ya no aplicarían
     * sobre la cola nueva.
     */
    fun setCurrentPlaylist(playlist: List<TrackEntity>, startIndex: Int = 0) {
        invalidateLoads()
        prefetchJob?.cancel()
        prefetchJob = null

        queue = playlist
        _currentPlaylist.publish(playlist)

        if (playlist.isEmpty()) {
            currentIndex = -1
            windowState = windowState.reset()
            _currentTrackIndex.publish(0)
            _currentTrack.publish(null)
            _currentTitle.publish(null)
            return
        }

        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        windowState = windowState.anchorAt(currentIndex)
        publishCurrentTrack()
    }

    fun pausePlayer() = _exoPlayer?.pause()

    @OptIn(UnstableApi::class)
    fun playPlayer() {
        val player = _exoPlayer ?: return
        val action = IdlePlayback.decide(
            isIdle = player.playbackState == Player.STATE_IDLE,
            mediaItemCount = player.mediaItemCount,
            queueSize = queue.size,
            currentIndex = currentIndex,
        )
        when (action) {
            IdlePlayback.Action.Resume -> player.play()
            IdlePlayback.Action.PrepareAndPlay -> {
                player.prepare()
                player.play()
            }
            is IdlePlayback.Action.RestartAt -> {
                // Tras stopAtQueueEnd (B63) el player está en IDLE sin items y la
                // UI apunta a una canción que "debería" sonarse: arrancamos desde
                // el ancla (currentIndex si es válido, o 0) reconstruyendo la ventana.
                playIndex((action as IdlePlayback.Action.RestartAt).index)
            }
            IdlePlayback.Action.Nothing -> Unit
        }
    }

    fun navigateToNext() {
        // Mientras dura una transición la cola todavía no se ha movido, así
        // que el salto no se puede calcular todavía. Pero descartarlo en
        // silencio era descartarlo para siempre: la sesión ya le había
        // devuelto `RESULT_INFO_SKIPPED` al sistema (B59). Se apunta y se
        // aplica cuando la transición termine, con el índice ya actualizado.
        if (transitionInFlight) {
            pendingSkips.request(PendingSkips.Direction.FORWARDS)
            return
        }
        val target = QueueIndex.nextIndex(currentIndex, queue.size, queueRepeatMode) ?: return
        playIndex(target, backwards = false)
    }

    /**
     * Si la cola tiene una canción después de la actual (con "repetir todo"
     * también la tiene estando en la última, porque da la vuelta).
     *
     * Es la respuesta que decide si existe el `>>` de la notificación (B57). La
     * ventana de ExoPlayer no puede darla: es una porción de la cola que se
     * recorta por delante y que a veces ni llega a rellenarse, y el `>>` se
     * escondía cada vez que la canción actual quedaba al final de la ventana,
     * aunque quedaran canciones por delante en la cola.
     */
    fun hasNextInQueue(): Boolean =
        QueueIndex.nextIndex(currentIndex, queue.size, queueRepeatMode) != null

    fun navigateToPrevious() {
        // Igual que en `navigateToNext`: mientras hay una transición en vuelo
        // se apunta en vez de soltarla (B59), y al aplicarla se vuelve a mirar
        // la posición real, que entretanto ha cambiado.
        if (transitionInFlight) {
            pendingSkips.request(PendingSkips.Direction.BACKWARDS)
            return
        }
        val position = _exoPlayer?.currentPosition ?: 0L
        val target = QueueIndex.previousIndex(currentIndex, queue.size, position, queueRepeatMode)
        if (target == null) {
            // No hay canción anterior: en la primera de la cola, y antes de los
            // 3 s, el botón se quedaba sin hacer nada. Reiniciar la canción
            // actual es lo que hacen el resto de reproductores, y deja el botón
            // con una respuesta en vez de aparentar que está roto (B49).
            _exoPlayer?.seekTo(0L)
            return
        }
        if (target == currentIndex) {
            _exoPlayer?.seekTo(0L)
            return
        }
        playIndex(target, backwards = target < currentIndex)
    }

    /**
     * Aplica los saltos que se apuntaron mientras había una transición en
     * vuelo (B59), en el orden en que llegaron.
     *
     * El bucle se detiene en cuanto uno de ellos pone en marcha otra
     * transición: esta volverá a llamar aquí cuando termine. Como cada vuelta
     * consume una petición y nada puede añadir mientras tanto —todo corre
     * síncrono en el hilo principal—, termina siempre.
     */
    private fun drainPendingSkips() {
        while (!transitionInFlight) {
            when (pendingSkips.poll()) {
                null -> return
                PendingSkips.Direction.FORWARDS -> navigateToNext()
                PendingSkips.Direction.BACKWARDS -> navigateToPrevious()
            }
        }
    }

    fun updateRepeatMode() {
        val mode = Config.getRepeatMode(getApplication())
        queueRepeatMode = mode
        _exoPlayer?.repeatMode = if (mode == Config.REPEAT_MODE_ONE) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
        growWindow()
    }

    /**
     * Añade una pista al final de la cola.
     *
     * Si ya hay una cola en reproducción, la pista entra en la ventana para
     * que suene a continuación. Si no, solo queda en la lista hasta que se
     * empiece a reproducir algo.
     */
    fun addToQueue(track: TrackEntity) {
        val wasPlaying = queue.isNotEmpty()
        val base = if (wasPlaying) queue else (_currentPlaylist.value ?: emptyList())
        val updated = base + track

        queue = updated
        _currentPlaylist.publish(updated)

        if (wasPlaying) {
            // Encolar al final no desplaza ningún índice, así que no invalida
            // nada: una resolución en vuelo sigue siendo válida y el salto que
            // el usuario estaba pidiendo se aplica al terminar. Antes aquí había
            // un `generation++` que se lo tragaba en silencio y dejaba huérfano
            // el estado de carga (B58).
            prefetchJob?.cancel()
            prefetchJob = null
            growWindow()
        }
    }

    /**
     * Limpia el estado del reproductor: cancela las cargas en curso, para la
     * reproducción y vacía la cola. No borra la lista mostrada en la UI, para
     * evitar parpadeos cuando quien llama va a definir una cola nueva.
     */
    fun clearPlayerState() {
        prefetchJob?.cancel()
        prefetchJob = null
        // Una resolución en vuelo apuntaría a un estado que ya no existe:
        // se cancela con ella (B60). El candado lo retira su propio `finally`.
        playbackJob?.cancel()
        playbackJob = null
        // `invalidateLoads()` también retira el candado de cualquier
        // transición en vuelo: esta operación se hace cargo de todo (B59).
        invalidateLoads()

        consecutiveFailures = 0
        currentVideoId = null
        resolvedVideoId.clear()
            windowState = windowState.reset()

        _exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }

        queue = emptyList()
        currentIndex = -1

        _isLoading.publish(false)
        _error.publish(null)
    }

    // ------------------------------------------------------------------ //
    // Reproducción
    // ------------------------------------------------------------------ //

    /**
     * Resuelve la URL de audio de [track] y empieza a reproducirla, en el
     * scope del propio ViewModel.
     *
     * Antes cada pantalla lanzaba [loadAudioFromTrack] en su
     * `rememberCoroutineScope()`: si la pantalla salía de composición
     * mientras la resolución esperaba red, la corrutina se cancelaba y el
     * `catch (_: Exception)` de la llamada se tragaba la
     * `CancellationException` — la canción pedida no sonaba y no había
     * error (B60). Esta función es la única puerta de entrada ahora.
     *
     * @param onFinished se invoca cuando la carga termina, **también si se
     *   cancela**, para que la pantalla apague su estado de "iniciando".
     */
    fun playTrack(track: TrackEntity, onFinished: (() -> Unit)? = null) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            try {
                loadAudioFromTrack(track)
            } finally {
                onFinished?.invoke()
            }
        }
    }

    /**
     * Cancela una resolución en vuelo sin tocar la cola: es el "stop" de la
     * UI (antes cancelaba los jobs que la pantalla tenía a mano).
     */
    fun cancelPendingPlayback() {
        playbackJob?.cancel()
        playbackJob = null
    }

    /**
     * Resuelve la URL de audio de [track] y empieza a reproducirla.
     *
     * La posición en la cola se deduce de la que ya fijó [setCurrentPlaylist];
     * solo si la pista no pertenece a la cola actual, se añade al final para no
     * perder la lista en curso.
     */
    private suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
        val index = when {
            currentIndex in queue.indices && queue[currentIndex] == track -> currentIndex
            else -> queue.indexOfFirst { it == track }
        }

        if (index !in queue.indices) {
            queue = queue + track
            _currentPlaylist.publish(queue)
            return@withContext startAt(queue.size - 1)
        }

        currentIndex = index
        startAt(index)
    }

    /**
     * Carga inicial de [index]: resuelve su URL y la reproduce.
     * A partir de aquí, [growWindow] deja preparadas las siguientes.
     */
    private suspend fun startAt(index: Int): Boolean {
        if (index !in queue.indices) return false

        initializePlayer()
        val player = _exoPlayer ?: return false

        invalidateLoads()
        val gen = generation
        prefetchJob?.cancel()
        prefetchJob = null

        // Empezar a reproducir una canción es una transición como cualquier
        // otra: mientras dura, la cola todavía no se ha movido y un salto se
        // apunta en vez de aplicarse (B59). `invalidateLoads()` acababa de
        // retirar las anteriores, así que este candado es solo suyo.
        val transitionToken = beginTransition()
        val token = beginLoading()
        try {
            _error.publish(null)

            val track = queue[index]
            val knownId = track.youtubeVideoId ?: resolvedVideoId[track.id]
            val videoId = withContext(Dispatchers.IO) {
                YouTubeManager.resolveVideoId(track.name, track.artists, knownId)
            }
            val sourceUri = withContext(Dispatchers.IO) { resolveSourceUri(videoId) }

            // La cola cambió mientras se resolvía: el resultado ya no vale.
            if (gen != generation) return false

            if (videoId == null || sourceUri == null) {
                _error.publish(Translations.get(getApplication(), "error_obtaining_audio"))
                return false
            }

            windowState = windowState.anchorAt(index)
            resolvedVideoId[track.id] = videoId
            setCurrentIndex(index)
            currentVideoId = videoId

            player.setMediaItem(createMediaItem(track, sourceUri, index))
            player.prepare()
            player.play()

            onMediaSessionUpdate?.invoke(player)
            growWindow()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val prefix = Translations.get(getApplication(), "error_prefix")
            _error.publish(prefix + (e.message ?: ""))
            return false
        } finally {
            // El token se retira sea cual sea la `generation`: si esta carga
            // fue invalidada por otra operación, esta retirada no toca a nadie.
            // Lo que no podía quedar es el estado clavado en `true`, que era lo
            // que dejaba el spinner y los controles muertos (B58). El candado
            // se retira aquí también, sea o no suyo ya: mientras esté puesto,
            // los saltos se apuntan y no se pierden (B59).
            endTransition(transitionToken)
            drainPendingSkips()
            endLoading(token)
        }
    }

    /**
     * Salta a [target]. Si ya está en la ventana, el salto es inmediato y sin
     * cortes; si no, se resuelve y se reconstruye la ventana alrededor de él.
     *
     * @param reResolve fuerza a resolver de nuevo en vez de reutilizar el item
     *   ya preparado. Se usa cuando la URL caducó: un `seekTo` reintentaría con
     *   la misma URL y volvería a fallar.
     * @param backwards sentido del salto. Solo importa cuando la canción destino
     *   no se puede resolver: el reintento tiene que buscar en el mismo sentido
     *   que el salto, o un `<<` acabaría sonando una canción de delante (B49).
     */
    private fun playIndex(
        target: Int,
        reResolve: Boolean = false,
        backwards: Boolean = target < currentIndex,
    ) {
        if (transitionInFlight) return
        val player = _exoPlayer ?: return
        if (target !in queue.indices) {
            stopAtQueueEnd()
            return
        }

        val windowPosition = target - windowStart
        if (!reResolve && windowPosition in 0 until player.mediaItemCount) {
            setCurrentIndex(target)
            player.seekTo(windowPosition, C.TIME_UNSET)
            player.play()
            growWindow()
            return
        }

        // El candado se adquiere aquí mismo, no dentro de la corrutina: en
        // cuanto `playIndex` decide mover la cola, los saltos tienen que
        // empezar a apuntarse (B59).
        val transitionToken = beginTransition()
        val gen = generation

        viewModelScope.launch {
            // Como en startAt: la re-resolución también es carga. Sin esto la UI
            // dejaba de mostrar el spinner y los controles se reactivaban a
            // mitad de la resolución (B36).
            val token = beginLoading()
            try {
                _error.publish(null)

                var candidate = target
                var skipped = 0
                var resolved: List<ResolvedItem> = emptyList()
                var giveUp = false

                while (resolved.isEmpty()) {
                    val endExclusive = minOf(candidate + WINDOW_AHEAD, queue.size - 1) + 1
                    resolved = resolveItems(candidate, endExclusive, gen, forceRefresh = reResolve)
                    if (resolved.isNotEmpty()) break

                    // Ninguna se pudo resolver: se prueban las siguientes antes de
                    // rendirse, y solo un número acotado de saltos. Hacia atrás se
                    // busca hacia atrás: si no, el `<<` acababa sonando una
                    // canción distinta hacia delante (B49).
                    val following = if (skipped < MAX_RESOLUTION_SKIPS) {
                        QueueIndex.retryIndexFor(candidate, queue.size, queueRepeatMode, backwards)
                    } else {
                        null
                    }
                    if (following == null) {
                        giveUp = true
                        break
                    }
                    candidate = following
                    skipped++
                }

                // El candado se retira en el `finally`, no aquí: mientras se
                // aplica el resultado la cola sigue moviéndose.
                if (gen != generation || _exoPlayer !== player) return@launch

                if (giveUp) {
                    _error.publish(Translations.get(getApplication(), "error_obtaining_audio"))
                    stopAtQueueEnd()
                    return@launch
                }

                // La ventana arranca en la primera canción que sí se resolvió, de
                // modo que posición del reproductor e índice de la cola coinciden.
                // `resolved` puede tener huecos (los que no se resolvieron se
                // descartan conservando el índice original): cargarlos tal cual
                // rompería la contigüedad de la ventana y a partir de ahí
                // `growWindow`/`currentIndex` operarían sobre índices equivocados
                // (B62). Se recorta en el primer hueco; lo recortado se vuelve a
                // pedir en el `growWindow` de justo debajo.
                val prefix = QueueIndex.contiguousPrefixLength(resolved.map { it.index })
                val start = resolved.first().index
                windowState = windowState.anchorAt(start)
                setCurrentIndex(start)
                player.setMediaItems(resolved.take(prefix).map { it.mediaItem }, 0, C.TIME_UNSET)
                player.prepare()
                player.play()
                onMediaSessionUpdate?.invoke(player)
                growWindow()
            } finally {
                // El token se retira sea cual sea la `generation`. Antes el
                // cierre era condicional (`if (gen == generation)`) y quien
                // invalidaba la resolución no cerraba nada por su cuenta, así
                // que un `generation++` ajeno dejaba el estado en `true` para
                // siempre: spinner para siempre y controles muertos (B58).
                // El candado se retira aquí, en el `finally`, y solo por su
                // token: si se soltara a mitad del cuerpo, un error o una
                // cancelación lo dejaría puesto y a partir de ahí todos los
                // saltos se caerían en silencio (B59).
                endTransition(transitionToken)

                // Los saltos que llegaron mientras se resolvía se aplican ya,
                // con el índice ya movido. Si alguno pone en marcha otra
                // transición, esta volverá a llamar aquí al terminar.
                drainPendingSkips()

                endLoading(token)
            }
        }
    }

    /**
     * Extiende la ventana con las siguientes canciones aún no preparadas, para
     * que la transición sea inmediata. Se llama tras cada transición.
     *
     * Solo corre un relleno a la vez y **nunca se cancela** para lanzar otro
     * (B57): cancelar se llevaba por delante la extracción que iba justo por la
     * canción siguiente, y relanzar el mismo trabajo mientras el usuario estaba
     * saltando era justo cuando más falta hacía. Las llamadas que llegan mientras
     * tanto se recogen al terminar, porque el bucle vuelve a mirar qué falta.
     */
    private fun growWindow() {
        if (_exoPlayer == null) return
        if (queue.isEmpty() || currentIndex !in queue.indices) return

        trimWindow()

        // Un relleno en marcha no se cancela para lanzar otro: su bucle recoge
        // lo que falte cuando termine. Cancelarlo era lo que rompía el `>>`.
        if (prefetchJob?.isActive == true) return
        if (fillTarget() == null) return

        val gen = generation
        prefetchJob = viewModelScope.launch {
            fillWindow(gen)
        }
    }

    /**
     * Tramo de cola que falta por preparar, o `null` si la ventana ya cubre lo
     * que se pide (la cola entera, o la actual más [WINDOW_AHEAD]).
     *
     * El tramo se acota a [WINDOW_AHEAD] items por llamada: cambiar a "repetir
     * todo" estando en la última canción pediría resolver la cola entera de
     * golpe.
     */
    private fun fillTarget(): FillTarget? {
        val player = _exoPlayer ?: return null
        if (queue.isEmpty() || currentIndex !in queue.indices) return null

        val lastCovered = windowStart + player.mediaItemCount - 1
        val wanted = minOf(currentIndex + WINDOW_AHEAD, queue.size - 1)
        if (wanted - lastCovered <= 0) return null

        val from = lastCovered + 1
        val lastNew = minOf(wanted, from + WINDOW_AHEAD - 1)
        return if (from > lastNew) null else FillTarget(from, lastNew)
    }

    /**
     * Rellena la ventana vuelta a vuelta hasta que no quede nada que pedir.
     *
     * El tramo se recalcula al principio de cada iteración, así que los saltos
     * que lleguen mientras se resuelve se atienden en esta misma corrida y el
     * trabajo ya hecho (las URLs ya extraídas) sigue valiendo: antes, cada
     * salto cancelaba el relleno anterior y empezaba de cero.
     *
     * Si un tramo no se puede resolver se reintenta [MAX_FILL_ATTEMPTS] veces y
     * entonces se rinde, en vez de quedarse en silencio sin decir nada.
     */
    private suspend fun fillWindow(gen: Int) {
        var attempt = 1
        while (gen == generation) {
            val target = fillTarget() ?: return
            val resolved = resolveItems(target.from, target.lastNew + 1, gen)
            if (gen != generation) return

            // Solo se añaden los items desde `from` sin huecos: si uno falla, la
            // ventana debe dejar de crecer ahí o la posición de ExoPlayer
            // dejaría de corresponder con el índice de la cola.
            var expected = target.from
            val contiguous = ArrayList<MediaItem>(resolved.size)
            for (item in resolved) {
                if (item.index != expected) break
                contiguous.add(item.mediaItem)
                expected++
            }

            if (contiguous.isEmpty()) {
                if (attempt >= MAX_FILL_ATTEMPTS) return
                attempt++
                delay(FILL_RETRY_DELAY_MS)
                continue
            }

            // Entre que se calculó el tramo y aquí la ventana pudo cambiar de
            // otra forma (p. ej. vaciándose al llegar al final de la cola).
            // Añadir en ese caso dejaría la posición de ExoPlayer desfasada
            // respecto a la cola; se reevalúa en la siguiente vuelta.
            val player = _exoPlayer ?: return
            if (windowStart + player.mediaItemCount != target.from) return

            attempt = 1
            player.addMediaItems(contiguous)
            trimWindow()
        }
    }

    /**
     * Descarta de la ventana los items ya superados para que no crezca sin
     * límite. Nunca se elimina el item en reproducción.
     */
    /**
     * Recorta por delante lo que ya no puede volver a usarse, dejando
     * [KEEP_BEHIND] items detrás del actual.
     *
     * Antes no dejaba ninguno, y eso rompía el `<<`: con la ventana empezando
     * justo en la canción actual, el anterior nunca estaba preparado, así que
     * cada `<<` caía en el camino asíncrono (resolver por red). Durante la
     * resolución la app se marca como cargando, los tres botones se desactivan y
     * un segundo toque se pierde en silencio. Con un item detrás, el salto
     * habitual es un `seekTo` inmediato y sin cortes (B49).
     */
    private fun trimWindow() {
        val player = _exoPlayer ?: return
        val removable = player.currentMediaItemIndex - KEEP_BEHIND
        if (removable <= 0 || removable >= player.mediaItemCount) return

        player.removeMediaItems(0, removable)
        windowState = windowState.shiftForward(removable)
    }

    /**
     * Resuelve las URLs de `queue[from until toExclusive]`, en paralelo y con
     * poca concurrencia. Solo devuelve items cuyo índice se conoce, y nunca si
     * la cola cambió durante la resolución: un resultado obsoleto no debe
     * aplicarse. Los índices ausentes se ignoran (huecos), no se comprimen.
     *
     * [forceRefresh] salta la caché de URLs: lo usa el reintento tras una URL
     * caducada, que si no volvería a recibir la misma URL muerta (B4).
     */
    private suspend fun resolveItems(
        from: Int,
        toExclusive: Int,
        gen: Int,
        forceRefresh: Boolean = false
    ): List<ResolvedItem> {
        val start = from.coerceAtLeast(0)
        val end = toExclusive.coerceAtMost(queue.size)
        if (start >= end) return emptyList()

        val tracks = queue.subList(start, end).toList()

        val resolved = coroutineScope {
            tracks.chunked(RESOLVE_CONCURRENCY)
                .map { chunk ->
                    async(Dispatchers.IO) {
                        chunk.map { track ->
                            // Si la pista ya se resolvió antes (por búsqueda),
                            // se reutiliza ese video en vez de volver a buscarlo.
                            val knownId = track.youtubeVideoId ?: resolvedVideoId[track.id]
                            val videoId = YouTubeManager.resolveVideoId(
                                track.name,
                                track.artists,
                                knownId
                            )
                            if (videoId != null) resolvedVideoId[track.id] = videoId
                            // Preferir el fichero descargado: sin red y sin que
                            // le afecte la caducidad de la URL de googlevideo.
                            val sourceUri = videoId?.let { id ->
                                DownloadedAudioStore.localUri(getApplication(), id)
                                    ?: YouTubeManager.getAudioUrl(id, forceRefresh = forceRefresh)?.let(Uri::parse)
                            }
                            videoId to sourceUri
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

        if (gen != generation) return emptyList()

        return resolved.mapIndexedNotNull { i, (_, uri) ->
            if (uri == null) null else ResolvedItem(start + i, createMediaItem(tracks[i], uri, start + i))
        }
    }

    // ------------------------------------------------------------------ //
    // Transiciones y errores
    // ------------------------------------------------------------------ //

    /**
     * Una canción ha terminado. Decide a dónde ir con [QueueIndex] y salta.
     *
     * Es la transición que antes no existía: sin ella, cuando el reproductor se
     * quedaba sin item siguiente la reproducción se detenía en silencio.
     */
    private fun onTrackEnded() {
        if (transitionInFlight) return

        val target = QueueIndex.onTrackEnded(currentIndex, queue.size, queueRepeatMode)
        if (target == null) {
            stopAtQueueEnd()
            return
        }
        // El final de una canción siempre avanza, también cuando "repetir una"
        // deja el destino en la misma posición.
        playIndex(target, backwards = false)
    }

    /**
     * Un item no se pudo reproducir. Se salta a la siguiente en lugar de
     * detener la cola, salvo que los fallos se acumulen.
     */
    private fun onItemUnplayable(error: PlaybackException) {
        if (currentIndex !in queue.indices) {
            showError(error)
            return
        }

        // Una URL caducada (403/410) no se reintenta tal cual: se descarta de
        // la caché y la ventana se reconstruye con una URL nueva.
        val expiredUrl = isHttpStatusError(error)
        if (expiredUrl) {
            currentVideoId?.let { YouTubeManager.invalidate(it) }
        }

        consecutiveFailures++
        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            showError(error)
            stopAtQueueEnd()
            return
        }

        // Sin destino válido no hay nada que reintentar: se detiene la cola.
        val target = QueueIndex.onTrackEnded(currentIndex, queue.size, queueRepeatMode)
        if (target == null) {
            showError(error)
            stopAtQueueEnd()
            return
        }
        playIndex(target, reResolve = expiredUrl, backwards = false)
    }

    private fun showError(error: PlaybackException) {
        val prefix = Translations.get(getApplication(), "error_prefix")
        _error.publish(prefix + (error.message ?: ""))
    }

    /** Fin de la cola: se detiene la reproducción y se cierra la ventana. */
    private fun stopAtQueueEnd() {
        _exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
            windowState = windowState.reset()
        currentVideoId = null
        // B63: al vaciar la ventana, la UI puede seguir apuntando a la última
        // canción; al pulsar '>' (play) necesitamos que vuelva a arrancar con
        // lógica coherente. Se limpia el título para no dejar una pista "fantasma"
        // en pantalla, aunque la cola siga en memoria (no la borramos para que
        // el usuario pueda reintentar).
        trackAt(currentIndex)?.let { _ ->
            // Mantener la pista en la cola, pero reflejar estado "detenido"
            _currentTrack.publish(null)
            _currentTitle.publish(null)
        }
        // El candado no se toca: o lo lleva esta misma transición (y su
        // `finally` lo retira), o lo lleva otra que sigue trabajando, y
        // soltarlo desde aquí la dejaría sin candado a medio camino (B59).
        updateLoadingState()
    }

    private fun isHttpStatusError(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            if (cause is java.io.IOException &&
                cause.javaClass.simpleName.contains("ResponseCode")
            ) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    // ------------------------------------------------------------------ //
    // Sincronización de estado
    // ------------------------------------------------------------------ //

    /**
     * Traduce la posición de ExoPlayer a un índice de la cola. Como la ventana
     * es contigua, es aritmética: no depende de comparar IDs, que se rompía
     * con pistas repetidas en la cola.
     */
    private fun syncIndexFromWindow() {
        val player = _exoPlayer ?: return
        if (queue.isEmpty()) return

        val absolute = windowStart + player.currentMediaItemIndex
        if (absolute !in queue.indices) return

        currentIndex = absolute
        val track = queue[absolute]
        currentVideoId = resolvedVideoId[track.id] ?: track.youtubeVideoId
    }

    private fun trackAt(index: Int): TrackEntity? = queue.getOrNull(index)

    /** Fija la posición actual y refleja el cambio en la UI. */
    private fun setCurrentIndex(index: Int) {
        currentIndex = index
        val track = trackAt(index)
        // El id que cuenta es el que sirvió la URL, no el de la pista: si la
        // canción se resolvió por búsqueda, el de la pista es null (B4).
        currentVideoId = track?.let { resolvedVideoId[it.id] ?: it.youtubeVideoId }
        consecutiveFailures = 0
        publishCurrentTrack()
    }

    private fun publishCurrentTrack() {
        val track = trackAt(currentIndex) ?: return
        _currentTrack.publish(track)
        _currentTitle.publish("${track.name} - ${track.artists}")
        _currentTrackIndex.publish(currentIndex)
    }

    /**
     * Invalida todo el trabajo en vuelo: sus resultados ya no se van a aplicar,
     * así que se retira también el estado de carga que sostenían.
     *
     * Todo `generation++` tiene que pasar por aquí. Era lo que faltaba en B58:
     * había incrementos que no tocaban el estado de carga, y el que lo había
     * puesto no lo cerraba porque su generación ya no era la vigente.
     */
    private fun invalidateLoads() {
        windowState = windowState.invalidate()
        loading.supersede()
        // La operación que invalida se hace cargo: las transiciones anteriores
        // ya no van a aplicar nada, así que su candado también caduca (B59).
        transitions.supersede()
        // Lo que se había apuntado pedía saltos respecto a la cola anterior:
        // la nueva cola manda y esas peticiones ya no significan nada (B59).
        pendingSkips.clear()
        updateLoadingState()
    }

    /** Empieza una operación de carga y publica el nuevo estado. */
    private fun beginLoading(): Int {
        val token = loading.begin()
        updateLoadingState()
        return token
    }

    /** Termina la operación [token] y publica el nuevo estado. */
    private fun endLoading(token: Int) {
        loading.end(token)
        updateLoadingState()
    }

    /**
     * `isLoading` = alguna operación de carga en vuelo o el reproductor
     * bufferizando. Al derivarlo de la [LoadingState] no se queda bloqueado en
     * `true` aunque la operación que lo abrió sea invalidada (B58).
     */
    private fun updateLoadingState() {
        val buffering = _exoPlayer?.playbackState == Player.STATE_BUFFERING
        _isLoading.publish(loading.isLoading || buffering)
    }

    /**
     * URI reproducible de un vídeo: el fichero descargado si existe (offline,
     * sin caducidad), o si no la URL de streaming de YouTube ([forceRefresh]
     * salta la caché de URLs tras un 403/410).
     */
    private suspend fun resolveSourceUri(videoId: String?, forceRefresh: Boolean = false): Uri? {
        if (videoId == null) return null
        DownloadedAudioStore.localUri(getApplication(), videoId)?.let { return it }
        return YouTubeManager.getAudioUrl(videoId, forceRefresh)?.let(Uri::parse)
    }

    private fun createMediaItem(track: TrackEntity, uri: Uri, queueIndex: Int): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId("${track.id}#$queueIndex")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.name)
                    .setArtist(track.artists)
                    .build()
            )
            .build()

    /**
     * Asigna un valor desde el hilo principal cuando es posible, para que
     * quien lea `.value` a continuación vea el valor ya aplicado.
     */
    private fun <T> MutableLiveData<T>.publish(value: T) {
        if (Looper.myLooper() == Looper.getMainLooper()) setValue(value) else postValue(value)
    }

    /** Item resuelto junto a su posición en la cola, para no perder la alineación. */
    private data class ResolvedItem(val index: Int, val mediaItem: MediaItem)

    /** Tramo de la cola que falta por preparar en la ventana. */
    private data class FillTarget(val from: Int, val lastNew: Int)
}
