package com.plyr.ui.components

/**
 * Dibujo ASCII del pulso de escritura NFC, sin dependencias de Compose ni de
 * Android, para poder testearlo.
 *
 * Antes esta animación se calculaba dentro de la composición con una lista de
 * estado que se mutaba en cada frame; aquí es una función pura del frame (resto
 * de B20).
 */
object NfcPulse {
    /** Ancho en caracteres del texto. */
    const val WIDTH = 11

    /** Cada cuántos frames nace un anillo. */
    private const val SPAWN_EVERY = 3

    private const val CENTER = WIDTH / 2

    /** Número de frames tras los que la animación vuelve a empezar. */
    const val FRAME_CYCLE = 36

    /**
     * Radios de los anillos visibles en [frame]. Como el radio máximo es [CENTER]
     * y nace uno cada [SPAWN_EVERY] frames, nunca hay más de dos a la vez.
     */
    fun ringsAt(frame: Int): List<Int> {
        val radius = frame % FRAME_CYCLE
        return buildList {
            for (birth in SPAWN_EVERY..radius step SPAWN_EVERY) {
                val r = radius - birth
                if (r > CENTER) continue
                add(r)
            }
        }
    }

    /** Texto del frame [frame], del mismo ancho que [WIDTH]. */
    fun textAt(frame: Int): String {
        val chars = CharArray(WIDTH) { ' ' }
        for (r in ringsAt(frame)) {
            val left = CENTER - r
            val right = CENTER + r
            if (left == right && left in 0 until WIDTH) {
                chars[left] = '•'
            } else {
                if (left in 0 until WIDTH) chars[left] = '('
                if (right in 0 until WIDTH) chars[right] = ')'
            }
        }
        return String(chars)
    }
}
