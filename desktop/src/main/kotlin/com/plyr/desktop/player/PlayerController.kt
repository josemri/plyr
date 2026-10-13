package com.plyr.desktop.player

import com.plyr.desktop.i18n.Translations
import com.plyr.domain.RepeatMode
import com.plyr.player.AudioPlayer
import com.plyr.player.AudioPlayerListener
import com.plyr.player.AudioTrack
import com.plyr.player.PlaybackState
import com.plyr.viewmodel.IdlePlayback
import com.plyr.viewmodel.LoadingState
import com.plyr.viewmodel.PendingSkips
import com.plyr.viewmodel.QueueEngine
import com.plyr.viewmodel.QueueIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Estado de la reproducción de escritorio, ya resuelto para pintarse.
 *
 * Es el análogo neutral de las `LiveData` de `PlayerViewModel`: M7 pidió
 * `StateFlow` en lugar de `LiveData`, y aquí está el único punto de verdad que
 * la UI observa.
 */
data class PlayerState(
    val queue: List<PlayerTrack> = emptyList(),
    val currentIndex: Int = -1,
    val title: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: String = RepeatMode.OFF,
) {
    val hasQueue: Boolean get() = queue.isNotEmpty()
}

/** Fuente de audio resuelta para una pista: id de vídeo real + URI reproducible. */
data class ResolvedSource(val videoId: String, val uri: String)

/**
 * Resuelve la URI reproducible de una pista.
 *
 * Abstrae red (YouTube), ficheros descargados, etc. Es una función para poder
 * inyectar una fuente falsa en los tests, sin tocar la red ni `libvlc`.
 *
 * @param knownVideoId id ya conocido de la pista (de la propia pista o de una
 *   resolución previa), o `null` para que se busque.
 * @param forceRefresh fuerza a ignorar la caché (reintento tras un fallo).
 */
fun interface TrackSourceResolver {
    suspend fun resolve(track: PlayerTrack, knownVideoId: String?, forceRefresh: Boolean): ResolvedSource?
}

/**
 * Reproductor de escritorio: cola + ventana deslizante + motor de audio.
 *
 * Es el port a escritorio del `PlayerViewModel` de Android, pero sobre
 * `StateFlow` y con el motor inyectado ([AudioPlayer]): en producción un
 * [VlcAudioPlayer], en tests un doble. Reutiliza tal cual la lógica pura de
 * `:core` ([QueueEngine], [QueueIndex], [LoadingState], [PendingSkips],
 * [IdlePlayback]), de modo que las decisiones de cola y ventana —y sus
 * correcciones B49/B57/B58/B59/B62/B63— son literalmente las mismas.
 *
 * La cola sigue siendo la única fuente de verdad y el reproductor mantiene una
 * ventana contigua de la cola ya resuelta, para que el salto no dependa de la
 * red. Todas las operaciones se serializan en el [scope] inyectado (en la UI, el
 * hilo principal de Compose), igual que hacía `PlayerViewModel` sobre
 * `Dispatchers.Main`.
 */
class PlayerController(
    private val scope: CoroutineScope,
    /** Construye el motor de audio; puede lanzar si falta `libvlc`. */
    private val openPlayer: () -> AudioPlayer,
    private val sourceResolver: TrackSourceResolver,
    initialRepeatMode: String = RepeatMode.OFF,
    private val onRepeatModeChanged: (String) -> Unit = {},
) {

    private companion object {
        /** Fallos consecutivos tolerados antes de detener la cola. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /** Canciones que se pueden saltar por no resolverse antes de parar. */
        const val MAX_RESOLUTION_SKIPS = 5

        /** Intentos seguidos para rellenar un tramo de la ventana. */
        const val MAX_FILL_ATTEMPTS = 2

        /** Espera entre intentos de relleno fallidos. */
        const val FILL_RETRY_DELAY_MS = 400L

        /** Extracciones de URL simultáneas al rellenar la ventana. */
        const val RESOLVE_CONCURRENCY = 2
    }

    // --- Estado autoritativo ---

    private val engine = QueueEngine<PlayerTrack>()

    private val _state = MutableStateFlow(PlayerState(repeatMode = initialRepeatMode))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var player: AudioPlayer? = null

    /**
     * Video de YouTube realmente usado por cada pista (B4): para las pistas
     * resueltas por búsqueda el `videoId` de la pista es `null`, así que el
     * reintento necesita recordar cuál sirvió la URL.
     */
    private val resolvedVideoId = ConcurrentHashMap<String, String>()

    private val loading = LoadingState()
    private val transitions = LoadingState()
    private val pendingSkips = PendingSkips()

    private val transitionInFlight: Boolean get() = transitions.isLoading

    private var consecutiveFailures = 0
    private var currentVideoId: String? = null

    private var prefetchJob: Job? = null
    private var playJob: Job? = null

    private val queue: List<PlayerTrack> get() = engine.queue
    private val currentIndex: Int get() = engine.currentIndex
    private val generation: Int get() = engine.generation
    private val windowStart: Int get() = engine.windowStart

    // --- Listeners del motor ---

    /**
     * VLCJ avisa desde sus propios hilos, así que cada reacción se despacha al
     * [scope] para que todas las mutaciones del estado ocurran en el mismo hilo
     * (el mismo contrato que `Dispatchers.Main` en Android).
     */
    private val playerListener = object : AudioPlayerListener {
        override fun onPlaybackStateChanged(state: PlaybackState) {
            scope.launch {
                updateLoadingState()
                if (state == PlaybackState.ENDED) onTrackEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            scope.launch {
                _state.update { it.copy(isPlaying = isPlaying) }
                updateLoadingState()
            }
        }

        override fun onMediaItemTransition(automatic: Boolean) {
            scope.launch {
                if (player?.mediaItemCount == 0) return@launch
                syncIndexFromWindow()
                // Un item nuevo arranca: el error anterior era de la pista que ya
                // no suena (B6).
                _state.update { it.copy(error = null) }
                if (automatic) {
                    consecutiveFailures = 0
                    growWindow()
                }
            }
        }

        override fun onPlayerError(error: Throwable) {
            scope.launch {
                updateLoadingState()
                onItemUnplayable(error)
            }
        }
    }

    // ------------------------------------------------------------------ //
    // API pública
    // ------------------------------------------------------------------ //

    /**
     * Define la cola a reproducir y arranca en [startIndex].
     *
     * Es el gesto de "reproducir este resultado": la lista de resultados pasa a
     * ser la cola y la pista pulsada, la actual.
     */
    fun play(tracks: List<PlayerTrack>, startIndex: Int = 0) {
        invalidateLoads()
        prefetchJob?.cancel()
        prefetchJob = null

        engine.setQueue(tracks, startIndex)
        publishQueue()

        if (tracks.isEmpty()) {
            publishCurrentTrack(null)
            return
        }

        publishCurrentTrack()
        launchStartAt(engine.currentIndex)
    }

    /** Salta a la pista [index] de la cola y la reproduce. */
    fun jumpTo(index: Int) {
        if (index !in queue.indices) return
        playIndex(index)
    }

    /** Alterna entre reproducir y pausar la pista actual. */
    fun togglePlayPause() {
        val player = player
        if (player == null) {
            if (queue.isNotEmpty()) playIndex(currentIndex.coerceAtLeast(0))
            return
        }
        if (player.isPlaying) {
            player.pause()
        } else {
            when (val action = IdlePlayback.decide(
                isIdle = player.playbackState == PlaybackState.IDLE,
                mediaItemCount = player.mediaItemCount,
                queueSize = queue.size,
                currentIndex = currentIndex,
            )) {
                IdlePlayback.Action.Resume -> player.play()
                IdlePlayback.Action.PrepareAndPlay -> {
                    player.prepare()
                    player.play()
                }
                is IdlePlayback.Action.RestartAt -> playIndex(action.index)
                IdlePlayback.Action.Nothing -> Unit
            }
        }
        refreshProgress()
    }

    fun next() {
        // Mientras dura una transición la cola todavía no se ha movido: se apunta
        // y se aplica al terminar (B59).
        if (transitionInFlight) {
            pendingSkips.request(PendingSkips.Direction.FORWARDS)
            return
        }
        val target = engine.nextTarget() ?: return
        playIndex(target, backwards = false)
    }

    fun previous() {
        if (transitionInFlight) {
            pendingSkips.request(PendingSkips.Direction.BACKWARDS)
            return
        }
        val position = player?.currentPosition ?: 0L
        val target = engine.previousTarget(position)
        if (target == null) {
            // En la primera canción (o antes del umbral) se reinicia la actual.
            player?.seekTo(0L)
            return
        }
        if (target == currentIndex) {
            player?.seekTo(0L)
            return
        }
        playIndex(target, backwards = target < currentIndex)
    }

    /** Salta a [positionMs] dentro de la pista actual. */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        refreshProgress()
    }

    /** Cicla el modo de repetición: off -> one -> all -> off. */
    fun cycleRepeat() {
        val next = when (engine.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    fun setRepeatMode(mode: String) {
        engine.setRepeatMode(mode)
        player?.setRepeatOne(mode == RepeatMode.ONE)
        onRepeatModeChanged(mode)
        publishQueue()
        growWindow()
    }

    /**
     * Mueve la pista de la posición [from] a la [to] de la cola.
     *
     * Si hay una ventana cargada se reconstruye en la pista en curso para que el
     * índice de cola y el del reproductor sigan alineados; si no, solo se
     * reordena la lista (MVP de M7; la reordenación "sin cortes" es de M8/M15).
     */
    fun moveQueueItem(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return

        val currentTrackId = trackAt(currentIndex)?.id
        val reordered = queue.toMutableList().apply { add(to, removeAt(from)) }
        val newIndex = currentTrackId?.let { id -> reordered.indexOfFirst { it.id == id } } ?: -1

        val loaded = (player?.mediaItemCount ?: 0) > 0
        if (!loaded) {
            engine.setQueue(reordered, newIndex.coerceAtLeast(0))
            publishQueue()
            publishCurrentTrack()
            return
        }

        val position = player?.currentPosition ?: 0L
        player?.stop()
        player?.clearMediaItems()
        invalidateLoads()
        prefetchJob?.cancel()
        prefetchJob = null

        engine.setQueue(reordered, newIndex.coerceAtLeast(0))
        publishQueue()
        publishCurrentTrack()
        launchStartAt(newIndex, position)
    }

    /** Para la reproducción y vacía la cola. */
    fun clear() {
        playJob?.cancel()
        playJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        invalidateLoads()

        consecutiveFailures = 0
        currentVideoId = null
        resolvedVideoId.clear()
        engine.resetWindow()

        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        engine.setQueue(emptyList())

        _state.update {
            it.copy(
                queue = emptyList(),
                currentIndex = -1,
                title = null,
                isLoading = false,
                error = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
    }

    /**
     * Refresca posición/duración/estado desde el motor. La UI lo llama por
     * temporizador mientras suena algo (no hay un bucle interno: así la
     * composición queda ociosa y los tests no se cuelgan).
     */
    fun refreshProgress() {
        val player = player ?: return
        val position = player.currentPosition
        val duration = player.duration
        val playing = player.isPlaying
        _state.update { current ->
            if (current.positionMs != position || current.durationMs != duration || current.isPlaying != playing) {
                current.copy(positionMs = position, durationMs = duration, isPlaying = playing)
            } else {
                current
            }
        }
    }

    /** Libera el motor y cancela el trabajo en vuelo. */
    fun release() {
        playJob?.cancel()
        playJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        player?.release()
        player = null
    }

    // ------------------------------------------------------------------ //
    // Ciclo de vida del reproductor
    // ------------------------------------------------------------------ //

    private fun launchStartAt(index: Int, startPositionMs: Long = 0L) {
        playJob?.cancel()
        playJob = scope.launch { startAt(index, startPositionMs) }
    }

    /** Construye el motor la primera vez; si falla (p. ej. falta `libvlc`) avisa. */
    private fun ensurePlayer(): AudioPlayer? {
        player?.let { return it }
        return try {
            openPlayer().also {
                it.setListener(playerListener)
                player = it
            }
        } catch (t: Throwable) {
            setError(t.message?.takeIf { it.isNotBlank() } ?: Translations.get("player_not_available"))
            null
        }
    }

    // ------------------------------------------------------------------ //
    // Reproducción
    // ------------------------------------------------------------------ //

    /**
     * Resuelve [index] y la reproduce. A partir de aquí, `growWindow` deja
     * preparadas las siguientes.
     */
    private suspend fun startAt(index: Int, startPositionMs: Long = 0L): Boolean {
        if (index !in queue.indices) return false
        val player = ensurePlayer() ?: return false

        invalidateLoads()
        val gen = generation
        prefetchJob?.cancel()
        prefetchJob = null

        val transitionToken = transitions.begin()
        val token = beginLoading()
        try {
            setError(null)

            val track = queue[index]
            val knownId = track.videoId ?: resolvedVideoId[track.id]
            val source = sourceResolver.resolve(track, knownId, false)

            // La cola cambió mientras se resolvía: el resultado ya no vale.
            if (gen != generation) return false
            if (source == null) {
                setError(Translations.get("error_obtaining_audio"))
                return false
            }

            engine.anchorWindowAt(index)
            resolvedVideoId[track.id] = source.videoId
            applyCurrentIndex(index)
            currentVideoId = source.videoId

            player.setMediaItems(listOf(audioTrack(track, source.uri, index)), 0)
            if (startPositionMs > 0L) player.seekTo(0, startPositionMs)
            player.prepare()
            player.play()
            growWindow()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setError(e.message)
            return false
        } finally {
            transitions.end(transitionToken)
            drainPendingSkips()
            endLoading(token)
        }
    }

    /** Resultado de resolver una pista, con saltos acotados si no se puede. */
    private data class ResolutionOutcome(val resolved: List<ResolvedItem>, val gaveUp: Boolean)

    /** Item resuelto junto a su posición en la cola, para no perder la alineación. */
    private data class ResolvedItem(val index: Int, val track: AudioTrack)

    /**
     * Salta a [target]. Si ya está en la ventana es inmediato; si no, se resuelve
     * y se reconstruye la ventana alrededor de él.
     */
    private fun playIndex(
        target: Int,
        reResolve: Boolean = false,
        backwards: Boolean = target < currentIndex,
    ) {
        if (transitionInFlight) return
        val player = player ?: return
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

        val transitionToken = transitions.begin()
        val gen = generation
        scope.launch {
            val token = beginLoading()
            try {
                setError(null)
                val outcome = resolveWithSkips(target, reResolve, backwards, gen)
                if (gen != generation || player !== this@PlayerController.player) return@launch
                if (outcome.gaveUp) {
                    setError(Translations.get("error_obtaining_audio"))
                    stopAtQueueEnd()
                    return@launch
                }
                applyResolvedWindow(player, outcome.resolved)
            } finally {
                transitions.end(transitionToken)
                drainPendingSkips()
                endLoading(token)
            }
        }
    }

    /**
     * Resuelve a partir de [target] y, si no se puede, prueba con las siguientes
     * (o anteriores si [backwards]) hasta un número acotado de saltos.
     */
    private suspend fun resolveWithSkips(
        target: Int,
        reResolve: Boolean,
        backwards: Boolean,
        gen: Int,
    ): ResolutionOutcome {
        var candidate = target
        var skipped = 0
        var resolved: List<ResolvedItem> = emptyList()

        while (resolved.isEmpty()) {
            val endExclusive = minOf(candidate + engine.windowAhead, queue.size - 1) + 1
            resolved = resolveItems(candidate, endExclusive, gen, forceRefresh = reResolve)
            if (resolved.isNotEmpty()) break

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
     * Vuelca la ventana resuelta en el reproductor. Se recorta en el primer hueco
     * para que la ventana siga siendo contigua (B62).
     */
    private fun applyResolvedWindow(player: AudioPlayer, resolved: List<ResolvedItem>) {
        val prefix = QueueIndex.contiguousPrefixLength(resolved.map { it.index })
        val start = resolved.first().index
        engine.anchorWindowAt(start)
        applyCurrentIndex(start)
        player.setMediaItems(resolved.take(prefix).map { it.track }, 0)
        player.prepare()
        player.play()
        growWindow()
    }

    /**
     * Extiende la ventana con las siguientes canciones aún no preparadas. Solo
     * corre un relleno a la vez y nunca se cancela para lanzar otro (B57).
     */
    private fun growWindow() {
        if (player == null) return
        if (queue.isEmpty() || currentIndex !in queue.indices) return

        trimWindow()
        if (prefetchJob?.isActive == true) return
        if (fillTarget() == null) return

        val gen = generation
        prefetchJob = scope.launch { fillWindow(gen) }
    }

    private fun fillTarget(): QueueEngine.FillTarget? {
        val player = player ?: return null
        return engine.fillTarget(player.mediaItemCount)
    }

    private suspend fun fillWindow(gen: Int) {
        var attempt = 1
        while (gen == generation) {
            val target = fillTarget() ?: return
            val resolved = resolveItems(target.from, target.lastNew + 1, gen)
            if (gen != generation) return

            // Solo se añaden desde `from` sin huecos: un hueco rompería la
            // alineación entre posición del reproductor e índice de la cola.
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

            val player = player ?: return
            if (windowStart + player.mediaItemCount != target.from) return

            attempt = 1
            player.addMediaItems(contiguous)
            trimWindow()
        }
    }

    /** Recorta por delante dejando `QueueEngine.keepBehind` items detrás (B49). */
    private fun trimWindow() {
        val player = player ?: return
        val removable = engine.trimCount(player.currentMediaItemIndex)
        if (removable <= 0 || removable >= player.mediaItemCount) return

        player.removeMediaItems(0, removable)
        engine.shiftWindow(removable)
    }

    /** Resuelve las URLs de `queue[from until toExclusive]` con la concurrencia acotada. */
    private suspend fun resolveItems(
        from: Int,
        toExclusive: Int,
        gen: Int,
        forceRefresh: Boolean = false,
    ): List<ResolvedItem> {
        val start = from.coerceAtLeast(0)
        val end = toExclusive.coerceAtMost(queue.size)
        if (start >= end) return emptyList()

        val slice = queue.subList(start, end).toList()
        val resolved: List<Pair<PlayerTrack, ResolvedSource?>> = coroutineScope {
            slice.chunked(RESOLVE_CONCURRENCY)
                .map { chunk ->
                    async {
                        chunk.map { track ->
                            val knownId = track.videoId ?: resolvedVideoId[track.id]
                            val source = sourceResolver.resolve(track, knownId, forceRefresh)
                            if (source != null) resolvedVideoId[track.id] = source.videoId
                            track to source
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

        if (gen != generation) return emptyList()

        return resolved.mapIndexedNotNull { i, (track, source) ->
            if (source == null) null else ResolvedItem(start + i, audioTrack(track, source.uri, start + i))
        }
    }

    private fun audioTrack(track: PlayerTrack, uri: String, queueIndex: Int): AudioTrack =
        AudioTrack(
            mediaId = "${track.id}#$queueIndex",
            title = track.title,
            artist = track.artist,
            uri = uri,
        )

    // ------------------------------------------------------------------ //
    // Transiciones y errores
    // ------------------------------------------------------------------ //

    private fun onTrackEnded() {
        if (transitionInFlight) return
        val target = engine.onTrackEndedTarget()
        if (target == null) {
            stopAtQueueEnd()
            return
        }
        playIndex(target, backwards = false)
    }

    private fun onItemUnplayable(error: Throwable) {
        if (currentIndex !in queue.indices) {
            setError(error.message)
            return
        }

        consecutiveFailures++
        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            setError(error.message)
            stopAtQueueEnd()
            return
        }

        val target = engine.onTrackEndedTarget()
        if (target == null) {
            setError(error.message)
            stopAtQueueEnd()
            return
        }
        // Un fallo de VLC suele ser URL caducada o formato no soportado: se
        // reintenta re-resolviendo en vez de con la misma URL (B4).
        playIndex(target, reResolve = true, backwards = false)
    }

    private fun stopAtQueueEnd() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        engine.resetWindow()
        currentVideoId = null
        _state.update {
            it.copy(
                title = null,
                isPlaying = false,
                positionMs = 0L,
                durationMs = 0L,
            )
        }
        updateLoadingState()
    }

    private fun drainPendingSkips() {
        while (!transitionInFlight) {
            when (pendingSkips.poll()) {
                null -> return
                PendingSkips.Direction.FORWARDS -> next()
                PendingSkips.Direction.BACKWARDS -> previous()
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Sincronización de estado
    // ------------------------------------------------------------------ //

    /** Traduce la posición del reproductor a un índice de la cola (ventana contigua). */
    private fun syncIndexFromWindow() {
        val player = player ?: return
        if (queue.isEmpty()) return

        val absolute = windowStart + player.currentMediaItemIndex
        if (absolute !in queue.indices) return

        engine.moveTo(absolute)
        val track = queue[absolute]
        currentVideoId = resolvedVideoId[track.id] ?: track.videoId
        publishCurrentTrack()
    }

    private fun trackAt(index: Int): PlayerTrack? = engine.trackAt(index)

    private fun applyCurrentIndex(index: Int) {
        engine.moveTo(index)
        val track = trackAt(index)
        currentVideoId = track?.let { resolvedVideoId[it.id] ?: it.videoId }
        consecutiveFailures = 0
        publishCurrentTrack()
    }

    private fun invalidateLoads() {
        engine.invalidateWindow()
        loading.supersede()
        transitions.supersede()
        pendingSkips.clear()
        updateLoadingState()
    }

    private fun beginLoading(): Int {
        val token = loading.begin()
        updateLoadingState()
        return token
    }

    private fun endLoading(token: Int) {
        loading.end(token)
        updateLoadingState()
    }

    private fun updateLoadingState() {
        val buffering = player?.playbackState == PlaybackState.BUFFERING
        _state.update { it.copy(isLoading = loading.isLoading || buffering) }
    }

    private fun publishQueue() {
        _state.update {
            it.copy(
                queue = queue,
                currentIndex = currentIndex,
                repeatMode = engine.repeatMode,
            )
        }
    }

    private fun publishCurrentTrack(track: PlayerTrack? = trackAt(currentIndex)) {
        _state.update {
            it.copy(
                title = track?.let { t -> displayTitle(t) },
                currentIndex = currentIndex,
            )
        }
    }

    private fun setError(message: String?) {
        _state.update { it.copy(error = message) }
    }

    private fun displayTitle(track: PlayerTrack): String =
        if (track.artist.isBlank()) track.title else "${track.title} - ${track.artist}"
}
