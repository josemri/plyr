package com.plyr.viewmodel

import android.app.Application
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
import com.plyr.utils.Translations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Índice de cola del primer item de la ventana que tiene ExoPlayer. */
    private var windowStart: Int = 0

    /** Se incrementa en cada cambio de cola; invalida resoluciones en vuelo. */
    private var generation: Int = 0

    private var consecutiveFailures: Int = 0
    private var transitionInFlight: Boolean = false
    private var resolving: Boolean = false
    private var currentVideoId: String? = null
    private var queueRepeatMode: String = Config.REPEAT_MODE_OFF

    private var prefetchJob: Job? = null

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
        generation++
        prefetchJob?.cancel()
        prefetchJob = null

        queue = playlist
        _currentPlaylist.publish(playlist)

        if (playlist.isEmpty()) {
            currentIndex = -1
            windowStart = 0
            _currentTrackIndex.publish(0)
            _currentTrack.publish(null)
            _currentTitle.publish(null)
            return
        }

        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        windowStart = currentIndex
        publishCurrentTrack()
    }

    fun pausePlayer() = _exoPlayer?.pause()

    fun playPlayer() = _exoPlayer?.play()

    fun navigateToNext() {
        val target = QueueIndex.nextIndex(currentIndex, queue.size, queueRepeatMode) ?: return
        playIndex(target)
    }

    fun navigateToPrevious() {
        val position = _exoPlayer?.currentPosition ?: 0L
        val target = QueueIndex.previousIndex(currentIndex, queue.size, position, queueRepeatMode) ?: return
        if (target == currentIndex) {
            _exoPlayer?.seekTo(0L)
            return
        }
        playIndex(target)
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
            generation++
            prefetchJob?.cancel()
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
        generation++

        resolving = false
        transitionInFlight = false
        consecutiveFailures = 0
        currentVideoId = null
        windowStart = 0

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
     * Resuelve la URL de audio de [track] y empieza a reproducirla.
     *
     * La posición en la cola se deduce de la que ya fijó [setCurrentPlaylist];
     * solo si la pista no pertenece a la cola actual, se añade al final para no
     * perder la lista en curso.
     */
    suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
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

        generation++
        val gen = generation
        prefetchJob?.cancel()
        prefetchJob = null

        resolving = true
        updateLoadingState()
        _error.publish(null)

        val track = queue[index]
        try {
            val videoId = withContext(Dispatchers.IO) {
                YouTubeManager.resolveVideoId(track.name, track.artists, track.youtubeVideoId)
            }
            val audioUrl = videoId?.let {
                withContext(Dispatchers.IO) { YouTubeManager.getAudioUrl(it) }
            }

            // La cola cambió mientras se resolvía: el resultado ya no vale.
            if (gen != generation) return false

            if (videoId == null || audioUrl == null) {
                _error.publish(Translations.get(getApplication(), "error_obtaining_audio"))
                return false
            }

            windowStart = index
            setCurrentIndex(index)
            currentVideoId = videoId
            transitionInFlight = false

            player.setMediaItem(createMediaItem(track, audioUrl, index))
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
            // El estado de carga se cierra siempre aquí: no puede quedar
            // bloqueado en true, que era lo que deshabilitaba los controles.
            resolving = false
            updateLoadingState()
        }
    }

    /**
     * Salta a [target]. Si ya está en la ventana, el salto es inmediato y sin
     * cortes; si no, se resuelve y se reconstruye la ventana alrededor de él.
     *
     * @param reResolve fuerza a resolver de nuevo en vez de reutilizar el item
     *   ya preparado. Se usa cuando la URL caducó: un `seekTo` reintentaría con
     *   la misma URL y volvería a fallar.
     */
    private fun playIndex(target: Int, reResolve: Boolean = false) {
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

        transitionInFlight = true
        val gen = generation

        viewModelScope.launch {
            // Como en startAt: la re-resolución también es carga. Sin esto la UI
            // dejaba de mostrar el spinner y los controles se reactivaban a
            // mitad de la resolución (B36).
            resolving = true
            updateLoadingState()
            _error.publish(null)

            try {
                var candidate = target
                var skipped = 0
                var resolved: List<ResolvedItem> = emptyList()
                var giveUp = false

                while (resolved.isEmpty()) {
                    val endExclusive = minOf(candidate + WINDOW_AHEAD, queue.size - 1) + 1
                    resolved = resolveItems(candidate, endExclusive, gen)
                    if (resolved.isNotEmpty()) break

                    // Ninguna se pudo resolver: se prueban las siguientes antes de
                    // rendirse, y solo un número acotado de saltos.
                    val following = if (skipped < MAX_RESOLUTION_SKIPS) {
                        QueueIndex.nextIndex(candidate, queue.size, queueRepeatMode)
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

                transitionInFlight = false
                if (gen != generation || _exoPlayer !== player) return@launch

                if (giveUp) {
                    _error.publish(Translations.get(getApplication(), "error_obtaining_audio"))
                    stopAtQueueEnd()
                    return@launch
                }

                // La ventana arranca en la primera canción que sí se resolvió, de
                // modo que posición del reproductor e índice de la cola coinciden.
                val start = resolved.first().index
                windowStart = start
                setCurrentIndex(start)
                player.setMediaItems(resolved.map { it.mediaItem }, 0, C.TIME_UNSET)
                player.prepare()
                player.play()
                onMediaSessionUpdate?.invoke(player)
                growWindow()
            } finally {
                // Solo cierra el estado si esta resolución sigue siendo la
                // vigente; si otra transición la superó, esa la gestiona.
                if (gen == generation) {
                    resolving = false
                    updateLoadingState()
                }
            }
        }
    }

    /**
     * Extiende la ventana con las siguientes canciones aún no preparadas, para
     * que la transición sea inmediata. Se llama tras cada transición.
     */
    private fun growWindow() {
        val player = _exoPlayer ?: return
        if (queue.isEmpty() || currentIndex !in queue.indices) return

        trimWindow()

        val lastCovered = windowStart + player.mediaItemCount - 1
        val wanted = minOf(currentIndex + WINDOW_AHEAD, queue.size - 1)
        val missing = wanted - lastCovered
        if (missing <= 0) return

        // Se acota el relleno por llamada: cambiar a "repetir todo" estando en la
        // última canción pediría resolver la cola entera de golpe.
        val from = lastCovered + 1
        val lastNew = minOf(minOf(lastCovered + missing, queue.size - 1), from + WINDOW_AHEAD - 1)
        if (from > lastNew) return

        prefetchJob?.cancel()
        val gen = generation
        prefetchJob = viewModelScope.launch {
            val resolved = resolveItems(from, lastNew + 1, gen)
            if (gen != generation) return@launch

            // Solo se añaden los items desde `from` sin huecos: si uno falla, la
            // ventana debe dejar de crecer ahí o la posición de ExoPlayer
            // dejaría de corresponder con el índice de la cola.
            var expected = from
            val contiguous = ArrayList<MediaItem>(resolved.size)
            for (item in resolved) {
                if (item.index != expected) break
                contiguous.add(item.mediaItem)
                expected++
            }
            if (contiguous.isEmpty()) return@launch

            _exoPlayer?.addMediaItems(contiguous)
        }
    }

    /**
     * Descarta de la ventana los items ya superados para que no crezca sin
     * límite. Nunca se elimina el item en reproducción.
     */
    private fun trimWindow() {
        val player = _exoPlayer ?: return
        val current = player.currentMediaItemIndex
        if (current <= 0 || current >= player.mediaItemCount) return

        player.removeMediaItems(0, current)
        windowStart += current
    }

    /**
     * Resuelve las URLs de `queue[from until toExclusive]`, en paralelo y con
     * poca concurrencia. Solo devuelve items cuyo índice se conoce, y nunca si
     * la cola cambió durante la resolución: un resultado obsoleto no debe
     * aplicarse. Los índices ausentes se ignoran (huecos), no se comprimen.
     */
    private suspend fun resolveItems(
        from: Int,
        toExclusive: Int,
        gen: Int
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
                            val videoId = YouTubeManager.resolveVideoId(
                                track.name,
                                track.artists,
                                track.youtubeVideoId
                            )
                            videoId to videoId?.let { YouTubeManager.getAudioUrl(it) }
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

        if (gen != generation) return emptyList()

        return resolved.mapIndexedNotNull { i, (videoId, url) ->
            if (url == null) null else ResolvedItem(start + i, createMediaItem(tracks[i], url, start + i))
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
        playIndex(target)
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
        playIndex(target, reResolve = expiredUrl)
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
        windowStart = 0
        currentVideoId = null
        transitionInFlight = false
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
        currentVideoId = queue[absolute].youtubeVideoId
    }

    private fun trackAt(index: Int): TrackEntity? = queue.getOrNull(index)

    /** Fija la posición actual y refleja el cambio en la UI. */
    private fun setCurrentIndex(index: Int) {
        currentIndex = index
        currentVideoId = trackAt(index)?.youtubeVideoId
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
     * `isLoading` = resolviendo URLs o el reproductor está bufferizando.
     * Al derivarlo de ambos estados no puede quedarse bloqueado en `true`.
     */
    private fun updateLoadingState() {
        val buffering = _exoPlayer?.playbackState == Player.STATE_BUFFERING
        _isLoading.publish(resolving || buffering)
    }

    private fun createMediaItem(track: TrackEntity, audioUrl: String, queueIndex: Int): MediaItem =
        MediaItem.Builder()
            .setUri(audioUrl)
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
}
