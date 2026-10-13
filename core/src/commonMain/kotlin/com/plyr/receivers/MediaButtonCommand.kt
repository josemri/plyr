package com.plyr.receivers

/**
 * MediaButtonCommand - Traducción de los botones físicos de Auriculares
 * inalámbricos, del coche y del teclado a acciones del reproductor.
 *
 * Vive aparte de [MediaButtonReceiver] y no depende de Android (los keyCode son
 * `static final int`, así que el compilador los incrusta) para poder testear el
 * mapeo completo en la JVM.
 */

/** Acción de reproducción que corresponde a un botón de media. */
enum class MediaCommand {
    PLAY,
    PAUSE,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    FAST_FORWARD,
    REWIND,

    /** No hay acción de reproducción asociada a este botón. */
    NONE
}

object MediaButtonCommand {

    // Keycodes de media de Android. Se repiten aquí para no arrastrar
    // android.view.KeyEvent a un archivo pensado para testearse en JVM.
    const val KEYCODE_MEDIA_NEXT = 87
    const val KEYCODE_MEDIA_PREVIOUS = 88
    const val KEYCODE_MEDIA_PLAY_PAUSE = 85
    const val KEYCODE_MEDIA_PLAY = 126
    const val KEYCODE_MEDIA_PAUSE = 127
    const val KEYCODE_MEDIA_STOP = 86
    const val KEYCODE_MEDIA_FAST_FORWARD = 90
    const val KEYCODE_MEDIA_REWIND = 89

    /**
     * Acción asociada a un keyCode, o [MediaCommand.NONE] si el botón no
     * controla la reproducción.
     */
    fun fromKeyCode(keyCode: Int): MediaCommand = when (keyCode) {
        KEYCODE_MEDIA_PLAY -> MediaCommand.PLAY
        KEYCODE_MEDIA_PAUSE -> MediaCommand.PAUSE
        KEYCODE_MEDIA_PLAY_PAUSE -> MediaCommand.PLAY_PAUSE
        KEYCODE_MEDIA_NEXT -> MediaCommand.NEXT
        KEYCODE_MEDIA_PREVIOUS -> MediaCommand.PREVIOUS
        KEYCODE_MEDIA_FAST_FORWARD -> MediaCommand.FAST_FORWARD
        KEYCODE_MEDIA_REWIND -> MediaCommand.REWIND
        // "Stop" no es una acción de ExoPlayer: se trata como pausa, que es lo
        // que espera quien pulsa ese botón.
        KEYCODE_MEDIA_STOP -> MediaCommand.PAUSE
        else -> MediaCommand.NONE
    }
}
