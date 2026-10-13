package com.plyr.desktop.player

import com.plyr.player.AudioPlayer
import com.plyr.player.AudioPlayerListener
import com.plyr.player.AudioTrack
import com.plyr.player.PlaybackState

/**
 * Doble de [AudioPlayer] para testear [PlayerController] sin VLC ni audio real.
 *
 * Reproduce el comportamiento relevante de `VlcAudioPlayer`/ExoPlayer: ventana de
 * items, índice actual, auto-avance dentro de la ventana y emisión de eventos.
 */
class FakeAudioPlayer : AudioPlayer {

    private var listener: AudioPlayerListener? = null

    private val items = mutableListOf<AudioTrack>()
    private var index = 0
    private var repeatOne = false

    /** Llamadas a [setMediaItems] (items, startIndex), en orden. */
    val setMediaCalls = mutableListOf<Pair<List<AudioTrack>, Int>>()

    /** Llamadas a [addMediaItems] (solo los items añadidos), en orden. */
    val addMediaCalls = mutableListOf<List<AudioTrack>>()

    var playing = false
        private set
    private var currentState = PlaybackState.IDLE
    var position = 0L
    var durationMs = 60_000L

    /** Items cargados ahora mismo. */
    val loadedItems: List<AudioTrack> get() = items.toList()

    override val mediaItemCount: Int get() = items.size
    override val currentMediaItemIndex: Int get() = index
    override val currentPosition: Long get() = position
    override val duration: Long get() = durationMs
    override val isPlaying: Boolean get() = playing
    override val playbackState: PlaybackState get() = currentState

    override fun setListener(listener: AudioPlayerListener) {
        this.listener = listener
    }

    override fun setMediaItems(items: List<AudioTrack>, startIndex: Int) {
        this.items.clear()
        this.items.addAll(items)
        this.index = if (this.items.isEmpty()) 0 else startIndex.coerceIn(0, this.items.size - 1)
        setMediaCalls += items to startIndex
    }

    override fun addMediaItems(items: List<AudioTrack>) {
        this.items.addAll(items)
        addMediaCalls += items
    }

    override fun removeMediaItems(fromIndex: Int, toExclusive: Int) {
        if (fromIndex < 0 || toExclusive > items.size || fromIndex >= toExclusive) return
        val removedBeforeCurrent = toExclusive.coerceAtMost(index) - fromIndex
        items.subList(fromIndex, toExclusive).clear()
        if (removedBeforeCurrent > 0) index -= removedBeforeCurrent
        index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    override fun prepare() {
        currentState = PlaybackState.READY
    }

    override fun play() {
        if (items.isEmpty()) return
        playing = true
        currentState = PlaybackState.READY
        listener?.onIsPlayingChanged(true)
    }

    override fun pause() {
        playing = false
        currentState = PlaybackState.READY
        listener?.onIsPlayingChanged(false)
    }

    override fun stop() {
        playing = false
        currentState = PlaybackState.IDLE
        listener?.onPlaybackStateChanged(PlaybackState.IDLE)
    }

    override fun clearMediaItems() {
        items.clear()
        index = 0
    }

    override fun seekTo(index: Int, positionMs: Long) {
        if (index in items.indices) this.index = index
        position = if (positionMs == AudioPlayer.TIME_UNSET) 0L else positionMs
        if (index in items.indices) listener?.onMediaItemTransition(false)
    }

    override fun seekTo(positionMs: Long) {
        position = positionMs.coerceAtLeast(0L)
    }

    override fun seekForward() {
        position += 10_000L
    }

    override fun seekBack() {
        position -= 10_000L
    }

    override fun setRepeatOne(enabled: Boolean) {
        repeatOne = enabled
    }

    val isRepeatOne: Boolean get() = repeatOne

    override fun release() {
        playing = false
    }

    // --- Simulación de eventos del motor ---

    /** Simula que VLCJ/ExoPlayer avanza solo al siguiente item de la ventana. */
    fun advanceWithinWindowAutomatically() {
        if (index + 1 < items.size) index++
        playing = true
        listener?.onMediaItemTransition(true)
    }

    /** Simula el final de la pista actual (la ventana se agota). */
    fun emitEnded() {
        playing = false
        currentState = PlaybackState.ENDED
        listener?.onPlaybackStateChanged(PlaybackState.ENDED)
    }

    /** Simula un error de reproducción. */
    fun emitError(error: Throwable = IllegalStateException("boom")) {
        playing = false
        listener?.onPlayerError(error)
    }
}
