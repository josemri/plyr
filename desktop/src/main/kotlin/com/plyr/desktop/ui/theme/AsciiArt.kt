package com.plyr.desktop.ui.theme

/**
 * Arte ASCII del escritorio.
 *
 * En Android el logo de inicio son drawables (`ascii_1..50`) que se eligen al
 * azar; en escritorio no hay recursos de imagen, así que se usa un banner de
 * texto fijo, en la misma línea estética de consola.
 */
object AsciiArt {

    /** Banner "PLYR" en tipografía de bloque. */
    val LOGO = """
         ____  _     _   _ ____
        |  _ \| |   | | | |  _ \
        | |_) | |   | |_| | |_) |
        |  __/| |___|  _  |  _ <
        |_|   |_____|_| |_|_| \_\
    """.trimIndent()

    /** Separador horizontal de consola. */
    const val DIVIDER = "----------------------------------------"
}
