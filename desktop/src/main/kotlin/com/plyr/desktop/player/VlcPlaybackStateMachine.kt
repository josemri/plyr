package com.plyr.desktop.player

import com.plyr.player.AudioPlayerListener
import com.plyr.player.PlaybackState

/**
 * Máquina de estados pura del reproductor de escritorio.
 *
 * Traduce los eventos de VLCJ (`opening`, `buffering`, `playing`, `paused`,
 * `stopped`, `finished`, `error`) al [PlaybackState] neutral de `:core` y avisa
 * al [AudioPlayerListener]. No toca VLCJ: se puede probar en un JVM sin `libvlc`
 * instalado, que es justo lo que pide la verificación de M4.
 *
 * Solo notifica cuando el valor cambia de verdad, igual que hacía el listener
 * de `ExoAudioPlayer`, para no espolear recomposiciones ni transiciones de cola
 * con eventos repetidos.
 */
class VlcPlaybackStateMachine(
    /** Listener actual; puede sustituirse con [com.plyr.player.AudioPlayer.setListener]. */
    var listener: AudioPlayerListener? = null,
) {

    @Volatile
    var state: PlaybackState = PlaybackState.IDLE
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    fun onOpening() = setState(PlaybackState.BUFFERING)

    /**
     * VLCJ emite `buffering` también durante la reproducción normal (con la
     * caché ya llena), justo después de `playing`. Si ya está sonando, un
     * `buffering` no debe devolvernos a "cargando": solo cuenta antes de que
     * el motor llegue a `playing`.
     */
    fun onBuffering() {
        if (!isPlaying) setState(PlaybackState.BUFFERING)
    }

    fun onPlaying() {
        setPlaying(true)
        setState(PlaybackState.READY)
    }

    fun onPaused() = setPlaying(false)

    fun onStopped() {
        setPlaying(false)
        setState(PlaybackState.IDLE)
    }

    fun onFinished() {
        setPlaying(false)
        setState(PlaybackState.ENDED)
    }

    fun onError() {
        setPlaying(false)
        setState(PlaybackState.IDLE)
    }

    private fun setState(next: PlaybackState) {
        if (next == state) return
        state = next
        listener?.onPlaybackStateChanged(next)
    }

    private fun setPlaying(next: Boolean) {
        if (next == isPlaying) return
        isPlaying = next
        listener?.onIsPlayingChanged(next)
    }
}
