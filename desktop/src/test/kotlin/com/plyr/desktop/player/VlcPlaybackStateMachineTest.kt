package com.plyr.desktop.player

import com.plyr.player.AudioPlayerListener
import com.plyr.player.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la máquina de estados de [VlcAudioPlayer].
 *
 * Verifican, sin `libvlc` instalado, que los eventos de VLCJ se traducen al
 * [PlaybackState] neutral y que solo se notifica cuando algo cambia de verdad.
 */
class VlcPlaybackStateMachineTest {

    private class RecordingListener : AudioPlayerListener {
        val states = mutableListOf<PlaybackState>()
        val playing = mutableListOf<Boolean>()
        var errors = 0

        override fun onPlaybackStateChanged(state: PlaybackState) {
            states.add(state)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing.add(isPlaying)
        }

        override fun onMediaItemTransition(automatic: Boolean) = Unit

        override fun onPlayerError(error: Throwable) {
            errors++
        }
    }

    private fun machine(listener: AudioPlayerListener? = null) = VlcPlaybackStateMachine(listener)

    @Test
    fun estadoInicial_esIdleYNoSuena() {
        val m = machine()

        assertEquals(PlaybackState.IDLE, m.state)
        assertFalse(m.isPlaying)
    }

    @Test
    fun openingYBuffering_pasanAEspera() {
        val m = machine()

        m.onOpening()
        assertEquals(PlaybackState.BUFFERING, m.state)

        m.onBuffering()
        assertEquals(PlaybackState.BUFFERING, m.state)
    }

    @Test
    fun playing_marcaReadYSonando() {
        val m = machine()

        m.onPlaying()

        assertEquals(PlaybackState.READY, m.state)
        assertTrue(m.isPlaying)
    }

    @Test
    fun bufferingDuranteLaReproduccion_noVuelveACargando() {
        val m = machine()
        m.onPlaying()

        // VLCJ manda `buffering` con la caché llena justo después de `playing`;
        // no debe devolver el estado a BUFFERING (bug detectado con VLC real).
        m.onBuffering()

        assertEquals(PlaybackState.READY, m.state)
        assertTrue(m.isPlaying)
    }

    @Test
    fun paused_dejaDeSonarPeroSigueListo() {
        val m = machine()
        m.onPlaying()

        m.onPaused()

        assertFalse(m.isPlaying)
        assertEquals(PlaybackState.READY, m.state)
    }

    @Test
    fun stopped_vuelveAIdleYDejaDeSonar() {
        val m = machine()
        m.onPlaying()

        m.onStopped()

        assertFalse(m.isPlaying)
        assertEquals(PlaybackState.IDLE, m.state)
    }

    @Test
    fun finished_marcaEndedYDejaDeSonar() {
        val m = machine()
        m.onPlaying()

        m.onFinished()

        assertFalse(m.isPlaying)
        assertEquals(PlaybackState.ENDED, m.state)
    }

    @Test
    fun error_dejaDeSonarYVuelveAIdle() {
        val m = machine()
        m.onPlaying()

        m.onError()

        assertFalse(m.isPlaying)
        assertEquals(PlaybackState.IDLE, m.state)
    }

    @Test
    fun soloNotificaCuandoElValorCambia() {
        val listener = RecordingListener()
        val m = machine(listener)

        m.onOpening()   // IDLE -> BUFFERING (notifica)
        m.onBuffering() // sigue BUFFERING (no notifica)
        m.onPlaying()   // -> READY + isPlaying true
        m.onPlaying()   // sin cambios (no notifica)
        m.onPaused()    // isPlaying false (notifica)
        m.onPaused()    // sin cambios (no notifica)

        assertEquals(listOf(PlaybackState.BUFFERING, PlaybackState.READY), listener.states)
        assertEquals(listOf(true, false), listener.playing)
    }

    @Test
    fun elListenerSePuedeSustituir() {
        val first = RecordingListener()
        val second = RecordingListener()
        val m = machine(first)

        m.listener = second
        m.onPlaying()

        assertEquals(0, first.states.size)
        assertEquals(listOf(PlaybackState.READY), second.states)
    }
}
