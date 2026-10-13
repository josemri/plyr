package com.plyr.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.plyr.receivers.MediaCommand

/**
 * Atajos de teclado del escritorio (M15).
 *
 * Reutiliza [MediaCommand] —el mismo vocabulario con el que Android traduce los
 * botones de los auriculares— para que teclado, MPRIS y los propios botones del
 * reproductor manden sobre las mismas acciones.
 *
 * `Ctrl`/`Cmd` + flecha salta de pista; la flecha sola mueve la posición. Las
 * teclas multimedia dedicadas (play/pausa, siguiente...) se traducen igual, así
 * que los teclados con esas teclas funcionan sin configuración.
 */
object KeyboardShortcuts {

    /** Cuánto avanzan/retroceden las flechas sin modificador, en milisegundos. */
    const val SEEK_STEP_MS = 10_000L

    /** Traduce una pulsación a una orden de reproducción, o [MediaCommand.NONE]. */
    fun commandFor(event: KeyEvent): MediaCommand =
        mapKey(event.key, event.type, event.isCtrlPressed || event.isMetaPressed)

    /**
     * Núcleo puro del mapa, sin depender de `KeyEvent` para poder testearlo.
     * Solo cuentan las pulsaciones ([KeyEventType.KeyDown]), no el soltado.
     */
    internal fun mapKey(key: Key, type: KeyEventType, jumpTrack: Boolean): MediaCommand {
        if (type != KeyEventType.KeyDown) return MediaCommand.NONE
        return when (key) {
            Key.Spacebar -> MediaCommand.PLAY_PAUSE
            Key.DirectionRight -> if (jumpTrack) MediaCommand.NEXT else MediaCommand.FAST_FORWARD
            Key.DirectionLeft -> if (jumpTrack) MediaCommand.PREVIOUS else MediaCommand.REWIND
            else -> mediaKeyCommand(key) ?: MediaCommand.NONE
        }
    }

    /** Teclas multimedia dedicadas, sin modificadores; `null` si [key] no lo es. */
    private fun mediaKeyCommand(key: Key): MediaCommand? = when (key) {
        Key.MediaPlayPause -> MediaCommand.PLAY_PAUSE
        Key.MediaPlay -> MediaCommand.PLAY
        Key.MediaPause -> MediaCommand.PAUSE
        Key.MediaNext -> MediaCommand.NEXT
        Key.MediaPrevious -> MediaCommand.PREVIOUS
        Key.MediaFastForward -> MediaCommand.FAST_FORWARD
        Key.MediaRewind -> MediaCommand.REWIND
        else -> null
    }
}
