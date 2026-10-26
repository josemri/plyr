package com.plyr.viewmodel

import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.plyr.database.TrackEntity
import com.plyr.network.YouTubeManager
import com.plyr.player.AudioPlayer
import com.plyr.player.AudioPlayerListener
import com.plyr.player.AudioTrack
import com.plyr.player.ExoAudioPlayer
import com.plyr.player.PlaybackState
import com.plyr.utils.Config
import com.plyr.utils.Translations
import com.plyr.viewmodel.TrackResolver.ResolvedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reproductor de audio.
 *
 * La cola (`queue` + `currentIndex`) es la única fuente de verdad. El
 * reproductor (`AudioPlayer`) mantiene una **ventana deslizante** de la cola ya
 * resuelta (la canción actual y las siguientes), de forma que el salto a la
 * siguiente canción no depende de una carga en segundo plano indeterminada: el
 * item destino ya está preparado cuando el actual termina.
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
        /** Fallos consecutivos tolerados antes de detener la cola. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /** Canciones que se pueden saltar por no resolverse antes de parar. */
        const val MAX_RESOLUTION_SKIPS = 5

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

    private var _player: AudioPlayer? = null

    /** Reproductor en curso, o `null` si aún no se ha inicializado. */
    val audioPlayer: AudioPlayer? get() = _player

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

    var onMediaSessionUpdate: ((AudioPlayer) -> Unit)? = null

    // --- Estado autoritativo (hilo principal) ---

    /**
     * Lógica pura de la cola y su ventana. Ver [QueueEngine].
     *
     * Aquí viven la cola, el índice actual, el modo de repetición y el
     * [WindowState]; el ViewModel solo aporta lo que depende del entorno
     * (red, coroutines, reproductor) y aplica sus decisiones.
     */
    private val engine = QueueEngine<TrackEntity>()

    private val queue: List<TrackEntity> get() = engine.queue

    private var currentIndex: Int
        get() = engine.currentIndex
        set(value) = engine.moveTo(value)

    /** Índice de cola del primer item de la ventana que tiene el reproductor. */
    private val windowStart: Int get() = engine.windowStart

    /** Se incrementa en cada cambio de cola; invalida resoluciones en vuelo. */
    private val generation: Int get() = engine.generation

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

    private var queueRepeatMode: String
        get() = engine.repeatMode
        set(value) = engine.setRepeatMode(value)

    /**
     * Resolución de URLs de audio y construcción de `AudioTrack`. Mantiene la
     * caché de vídeos ya resueltos por pista (ver [TrackResolver]).
     */
    private val resolver = TrackResolver(getApplication())

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
     * Resoluciones de reproducción pedidas por la UI ([PlaybackRequester]).
     * Viven en el scope del ViewModel, no en el de la pantalla que las pidió:
     * navegar fuera no debe cancelarlas (B60).
     */
    val playback = PlaybackRequester(viewModelScope) { loadAudioFromTrack(it) }

    // ------------------------------------------------------------------ //
    // Ciclo de vida del reproductor
    // ------------------------------------------------------------------ //

    fun initializePlayer() {
        if (_player == null) {
            queueRepeatMode = Config.getRepeatMode(getApplication())
            _player = buildPlayer()
        }
    }

    /**
     * Reproductor de la plataforma envuelto tras [AudioPlayer].
     *
     * Es el único punto donde se instancia `ExoAudioPlayer`; a partir de aquí
     * todo pasa por la interfaz neutral.
     */
    private fun buildPlayer(): AudioPlayer =
        ExoAudioPlayer(
            context = getApplication(),
            repeatOne = queueRepeatMode == Config.REPEAT_MODE_ONE,
        ).apply {
            setListener(playerListener)
        }

    /**
     * Traduce los eventos del reproductor a las transiciones de la cola.
     *
     * Estaba dentro de `buildPlayer()` como listener anónimo del reproductor; al
     * depender solo de [AudioPlayerListener] puede vivir aparte.
     */
    private val playerListener = object : AudioPlayerListener {
        override fun onPlaybackStateChanged(state: PlaybackState) {
            updateLoadingState()
            if (state == PlaybackState.ENDED) {
                onTrackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateLoadingState()
        }

        override fun onMediaItemTransition(automatic: Boolean) {
            if (_player?.mediaItemCount == 0) return
            syncIndexFromWindow()
            publishCurrentTrack()
            // Un item arranca a reproducirse: el error anterior era de la pista
            // que ya no suena, así que se retira. Sin esto el mensaje se quedaba
            // pegado y los controles quedaban deshabilitados para siempre (B6).
            _error.publish(null)
            _player?.let { onMediaSessionUpdate?.invoke(it) }

            // Transición automática: es el momento de reponer la ventana para
            // que la siguiente canción esté lista.
            if (automatic) {
                consecutiveFailures = 0
                growWindow()
            }
        }

        override fun onPlayerError(error: Throwable) {
            updateLoadingState()
            onItemUnplayable(error)
        }
    }

    override fun onCleared() {
        prefetchJob?.cancel()
        prefetchJob = null
        _player?.release()
        _player = null
        // No se llama a super.onCleared(): la implementación base está vacía
        // (lint EmptySuperCall) y aquí no queda nada que liberar en el padre.
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

        engine.setQueue(playlist, startIndex)
        _currentPlaylist.publish(playlist)

        if (playlist.isEmpty()) {
            _currentTrackIndex.publish(0)
            _currentTrack.publish(null)
            _currentTitle.publish(null)
            return
        }

        publishCurrentTrack()
    }

    fun pausePlayer() = _player?.pause()

    fun playPlayer() {
        val player = _player ?: return
        val action = IdlePlayback.decide(
            isIdle = player.playbackState == PlaybackState.IDLE,
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
                playIndex(action.index)
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
        val target = engine.nextTarget() ?: return
        playIndex(target, backwards = false)
    }

    /**
     * Si la cola tiene una canción después de la actual (con "repetir todo"
     * también la tiene estando en la última, porque da la vuelta).
     *
     * Es la respuesta que decide si existe el `>>` de la notificación (B57). La
     * ventana del reproductor no puede darla: es una porción de la cola que se
     * recorta por delante y que a veces ni llega a rellenarse, y el `>>` se
     * escondía cada vez que la canción actual quedaba al final de la ventana,
     * aunque quedaran canciones por delante en la cola.
     */
    val hasNextInQueue: Boolean
        get() = engine.hasNext

    fun navigateToPrevious() {
        // Igual que en `navigateToNext`: mientras hay una transición en vuelo
        // se apunta en vez de soltarla (B59), y al aplicarla se vuelve a mirar
        // la posición real, que entretanto ha cambiado.
        if (transitionInFlight) {
            pendingSkips.request(PendingSkips.Direction.BACKWARDS)
            return
        }
        val position = _player?.currentPosition ?: 0L
        val target = engine.previousTarget(position)
        if (target == null) {
            // No hay canción anterior: en la primera de la cola, y antes de los
            // 3 s, el botón se quedaba sin hacer nada. Reiniciar la canción
            // actual es lo que hacen el resto de reproductores, y deja el botón
            // con una respuesta en vez de aparentar que está roto (B49).
            _player?.seekTo(0L)
            return
        }
        if (target == currentIndex) {
            _player?.seekTo(0L)
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
        _player?.setRepeatOne(mode == Config.REPEAT_MODE_ONE)
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

        engine.replaceQueue(updated)
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
        playback.cancel()
        // `invalidateLoads()` también retira el candado de cualquier
        // transición en vuelo: esta operación se hace cargo de todo (B59).
        invalidateLoads()

        consecutiveFailures = 0
        currentVideoId = null
        resolver.clear()
        engine.resetWindow()

        _player?.let { player ->
            player.stop()
            player.clearMediaItems()
        }

        engine.setQueue(emptyList())

        _isLoading.publish(false)
        _error.publish(null)
    }

    // ------------------------------------------------------------------ //
    // Reproducción
    // ------------------------------------------------------------------ //

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
            engine.appendTrack(track)
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
        val player = _player ?: return false

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
            val knownId = resolver.knownVideoId(track)
            val videoId = withContext(Dispatchers.IO) {
                YouTubeManager.resolveVideoId(track.name, track.artists, knownId)
            }
            val sourceUri = withContext(Dispatchers.IO) { resolver.resolveSourceUri(videoId) }

            // La cola cambió mientras se resolvía: el resultado ya no vale.
            if (gen != generation) return false

            if (videoId == null || sourceUri == null) {
                _error.publish(Translations.get(getApplication(), "error_obtaining_audio"))
                return false
            }

            engine.anchorWindowAt(index)
            resolver.remember(track.id, videoId)
            applyCurrentIndex(index)
            currentVideoId = videoId

            player.setMediaItems(listOf(resolver.createAudioTrack(track, sourceUri, index)), 0)
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
        val player = _player ?: return
        if (target !in queue.indices) {
            stopAtQueueEnd()
            return
        }

        val windowPosition = target - windowStart
        if (!reResolve && windowPosition in 0 until player.mediaItemCount) {
            applyCurrentIndex(target)
            player.seekTo(windowPosition, AudioPlayer.TIME_UNSET)
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

                val outcome = resolveWithSkips(target, reResolve, backwards, gen)

                // El candado se retira en el `finally`, no aquí: mientras se
                // aplica el resultado la cola sigue moviéndose.
                if (gen != generation || _player !== player) return@launch

                if (outcome.gaveUp) {
                    _error.publish(Translations.get(getApplication(), "error_obtaining_audio"))
                    stopAtQueueEnd()
                    return@launch
                }

                applyResolvedWindow(player, outcome.resolved)
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

    /** Resultado de intentar resolver una pista, con saltos acotados si no se puede. */
    private data class ResolutionOutcome(val resolved: List<ResolvedItem>, val gaveUp: Boolean)

    /**
     * Resuelve a partir de [target] y, si no se puede, prueba con las siguientes
     * canciones (o anteriores si [backwards]) hasta un número acotado de saltos
     * ([MAX_RESOLUTION_SKIPS]). Hacia atrás se busca hacia atrás: si no, el `<<`
     * acababa sonando una canción distinta hacia delante (B49).
     */
    private suspend fun resolveWithSkips(
        target: Int,
        reResolve: Boolean,
        backwards: Boolean,
        gen: Int
    ): ResolutionOutcome {
        var candidate = target
        var skipped = 0
        var resolved: List<ResolvedItem> = emptyList()

        while (resolved.isEmpty()) {
            val endExclusive = minOf(candidate + engine.windowAhead, queue.size - 1) + 1
            resolved = resolveItems(candidate, endExclusive, gen, forceRefresh = reResolve)
            if (resolved.isNotEmpty()) break

            // Ninguna se pudo resolver: se prueban las siguientes antes de
            // rendirse, y solo un número acotado de saltos.
            val following = if (skipped < MAX_RESOLUTION_SKIPS) {
                engine.retryTarget(candidate, backwards)
            } else {
                null
            }
            if (following == null) return ResolutionOutcome(emptyList(), gaveUp = true)
            candidate = following
            skipped++
        }
        return ResolutionOutcome(resolved, gaveUp = false)
    }

    /**
     * Vuelca en el reproductor las pistas ya resueltas. La ventana arranca en la
     * primera canción que sí se resolvió, de modo que posición del reproductor e
     * índice de la cola coinciden. `resolved` puede tener huecos (los que no se
     * resolvieron se descartan conservando el índice original): cargarlos tal
     * cual rompería la contigüedad de la ventana y a partir de ahí
     * `growWindow`/`currentIndex` operarían sobre índices equivocados (B62). Se
     * recorta en el primer hueco; lo recortado se vuelve a pedir en el
     * `growWindow` de justo debajo.
     */
    private fun applyResolvedWindow(player: AudioPlayer, resolved: List<ResolvedItem>) {
        val prefix = QueueIndex.contiguousPrefixLength(resolved.map { it.index })
        val start = resolved.first().index
        engine.anchorWindowAt(start)
        applyCurrentIndex(start)
        player.setMediaItems(resolved.take(prefix).map { it.track }, 0)
        player.prepare()
        player.play()
        onMediaSessionUpdate?.invoke(player)
        growWindow()
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
        if (_player == null) return
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
     * que se pide (la cola entera, o la actual más `QueueEngine.windowAhead`).
     *
     * El cálculo vive en [QueueEngine]; aquí solo se resuelve el reproductor.
     */
    private fun fillTarget(): QueueEngine.FillTarget? {
        val player = _player ?: return null
        return engine.fillTarget(player.mediaItemCount)
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
            // ventana debe dejar de crecer ahí o la posición del reproductor
            // dejaría de corresponder con el índice de la cola.
            var expected = target.from
            val contiguous = ArrayList<AudioTrack>(resolved.size)
            for (item in resolved) {
                if (item.index != expected) break
                contiguous.add(item.track)
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
            // Añadir en ese caso dejaría la posición del reproductor desfasada
            // respecto a la cola; se reevalúa en la siguiente vuelta.
            val player = _player ?: return
            if (windowStart + player.mediaItemCount != target.from) return

            attempt = 1
            player.addMediaItems(contiguous)
            trimWindow()
        }
    }

    /**
     * Recorta por delante lo que ya no puede volver a usarse, dejando
     * `QueueEngine.keepBehind` items detrás del actual.
     *
     * Antes no dejaba ninguno, y eso rompía el `<<`: con la ventana empezando
     * justo en la canción actual, el anterior nunca estaba preparado, así que
     * cada `<<` caía en el camino asíncrono (resolver por red). Durante la
     * resolución la app se marca como cargando, los tres botones se desactivan y
     * un segundo toque se pierde en silencio. Con un item detrás, el salto
     * habitual es un `seekTo` inmediato y sin cortes (B49).
     */
    private fun trimWindow() {
        val player = _player ?: return
        val removable = engine.trimCount(player.currentMediaItemIndex)
        if (removable <= 0 || removable >= player.mediaItemCount) return

        player.removeMediaItems(0, removable)
        engine.shiftWindow(removable)
    }

    /**
     * Resuelve las URLs de `queue[from until toExclusive]` delegando en
     * [TrackResolver]. El recorte a los límites de la cola y la comprobación de
     * vigencia (la `generation` no cambió) se quedan aquí, que son estado del
     * ViewModel.
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

        return resolver.resolveItems(
            tracks = queue.subList(start, end).toList(),
            startIndex = start,
            forceRefresh = forceRefresh,
        ) { gen == generation }
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

        val target = engine.onTrackEndedTarget()
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
    private fun onItemUnplayable(error: Throwable) {
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
        val target = engine.onTrackEndedTarget()
        if (target == null) {
            showError(error)
            stopAtQueueEnd()
            return
        }
        playIndex(target, reResolve = expiredUrl, backwards = false)
    }

    private fun showError(error: Throwable) {
        val prefix = Translations.get(getApplication(), "error_prefix")
        _error.publish(prefix + (error.message ?: ""))
    }

    /** Fin de la cola: se detiene la reproducción y se cierra la ventana. */
    private fun stopAtQueueEnd() {
        _player?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        engine.resetWindow()
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

    private fun isHttpStatusError(error: Throwable): Boolean {
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
     * Traduce la posición del reproductor a un índice de la cola. Como la ventana
     * es contigua, es aritmética: no depende de comparar IDs, que se rompía
     * con pistas repetidas en la cola.
     */
    private fun syncIndexFromWindow() {
        val player = _player ?: return
        if (queue.isEmpty()) return

        val absolute = windowStart + player.currentMediaItemIndex
        if (absolute !in queue.indices) return

        currentIndex = absolute
        val track = queue[absolute]
        currentVideoId = resolver.cachedVideoId(track.id) ?: track.youtubeVideoId
    }

    private fun trackAt(index: Int): TrackEntity? = queue.getOrNull(index)

    /** Fija la posición actual y refleja el cambio en la UI. */
    private fun applyCurrentIndex(index: Int) {
        currentIndex = index
        val track = trackAt(index)
        // El id que cuenta es el que sirvió la URL, no el de la pista: si la
        // canción se resolvió por búsqueda, el de la pista es null (B4).
        currentVideoId = track?.let { resolver.cachedVideoId(it.id) ?: it.youtubeVideoId }
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
        engine.invalidateWindow()
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
        val buffering = _player?.playbackState == PlaybackState.BUFFERING
        _isLoading.publish(loading.isLoading || buffering)
    }

    /**
     * Asigna un valor desde el hilo principal cuando es posible, para que
     * quien lea `.value` a continuación vea el valor ya aplicado.
     */
    private fun <T> MutableLiveData<T>.publish(value: T) {
        if (Looper.myLooper() == Looper.getMainLooper()) setValue(value) else postValue(value)
    }
}
