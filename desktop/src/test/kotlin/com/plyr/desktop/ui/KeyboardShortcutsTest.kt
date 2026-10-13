package com.plyr.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.plyr.receivers.MediaCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests del mapa de atajos (M15). Se prueba el núcleo puro, así que no hace
 * falta construir un `KeyEvent` con una ventana real.
 */
class KeyboardShortcutsTest {

    private fun command(key: Key, type: KeyEventType = KeyEventType.KeyDown, jumpTrack: Boolean = false) =
        KeyboardShortcuts.mapKey(key, type, jumpTrack)

    @Test
    fun espacioAlternaLaReproduccion() {
        assertEquals(MediaCommand.PLAY_PAUSE, command(Key.Spacebar))
    }

    @Test
    fun flechasMuevenLaPosicionYConModificadorCambianDePista() {
        assertEquals(MediaCommand.FAST_FORWARD, command(Key.DirectionRight))
        assertEquals(MediaCommand.REWIND, command(Key.DirectionLeft))
        assertEquals(MediaCommand.NEXT, command(Key.DirectionRight, jumpTrack = true))
        assertEquals(MediaCommand.PREVIOUS, command(Key.DirectionLeft, jumpTrack = true))
    }

    @Test
    fun lasTeclasMultimediaSeTraducenIgual() {
        assertEquals(MediaCommand.PLAY_PAUSE, command(Key.MediaPlayPause))
        assertEquals(MediaCommand.NEXT, command(Key.MediaNext))
        assertEquals(MediaCommand.PREVIOUS, command(Key.MediaPrevious))
        assertEquals(MediaCommand.PLAY, command(Key.MediaPlay))
        assertEquals(MediaCommand.PAUSE, command(Key.MediaPause))
        assertEquals(MediaCommand.FAST_FORWARD, command(Key.MediaFastForward))
        assertEquals(MediaCommand.REWIND, command(Key.MediaRewind))
    }

    @Test
    fun lasTeclasSinAtajoYElSoltadoNoHacenNada() {
        assertEquals(MediaCommand.NONE, command(Key.A))
        assertEquals(MediaCommand.NONE, command(Key.Enter))
        assertEquals(MediaCommand.NONE, command(Key.Spacebar, type = KeyEventType.KeyUp))
    }
}
