package com.plyr.receivers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests del mapeo de botones de media ([MediaButtonCommand]).
 *
 * Es la lógica pura del [MediaButtonReceiver]: si un keyCode no se traducía a
 * ninguna acción, el receiver enviaba un intent al servicio que
 * `MusicService.onStartCommand` nunca leía, así que el botón no hacía nada.
 */
class MediaButtonCommandTest {

    @Test
    fun play() {
        assertEquals(
            MediaCommand.PLAY,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_PLAY)
        )
    }

    @Test
    fun pause() {
        assertEquals(
            MediaCommand.PAUSE,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_PAUSE)
        )
    }

    @Test
    fun playPause() {
        assertEquals(
            MediaCommand.PLAY_PAUSE,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_PLAY_PAUSE)
        )
    }

    @Test
    fun next() {
        assertEquals(
            MediaCommand.NEXT,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_NEXT)
        )
    }

    @Test
    fun previous() {
        assertEquals(
            MediaCommand.PREVIOUS,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_PREVIOUS)
        )
    }

    @Test
    fun fastForwardYRewind() {
        assertEquals(
            MediaCommand.FAST_FORWARD,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_FAST_FORWARD)
        )
        assertEquals(
            MediaCommand.REWIND,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_REWIND)
        )
    }

    @Test
    fun stopSeTrataComoPausa() {
        assertEquals(
            MediaCommand.PAUSE,
            MediaButtonCommand.fromKeyCode(MediaButtonCommand.KEYCODE_MEDIA_STOP)
        )
    }

    @Test
    fun teclasQueNoSonDeMedia_noProducenAccion() {
        assertEquals(MediaCommand.NONE, MediaButtonCommand.fromKeyCode(0))
        assertEquals(MediaCommand.NONE, MediaButtonCommand.fromKeyCode(4))
        assertEquals(MediaCommand.NONE, MediaButtonCommand.fromKeyCode(66))
        assertEquals(MediaCommand.NONE, MediaButtonCommand.fromKeyCode(82))
    }

    @Test
    fun ningunKeycodeSeRepiteEnDosAcciones() {
        val keyCodes = listOf(
            MediaButtonCommand.KEYCODE_MEDIA_PLAY,
            MediaButtonCommand.KEYCODE_MEDIA_PAUSE,
            MediaButtonCommand.KEYCODE_MEDIA_PLAY_PAUSE,
            MediaButtonCommand.KEYCODE_MEDIA_NEXT,
            MediaButtonCommand.KEYCODE_MEDIA_PREVIOUS,
            MediaButtonCommand.KEYCODE_MEDIA_FAST_FORWARD,
            MediaButtonCommand.KEYCODE_MEDIA_REWIND,
            MediaButtonCommand.KEYCODE_MEDIA_STOP
        )
        assertEquals(keyCodes.size, keyCodes.toSet().size)
    }
}
