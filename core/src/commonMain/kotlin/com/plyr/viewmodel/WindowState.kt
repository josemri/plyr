package com.plyr.viewmodel

/**
 * Estado invariante de la ventana deslizante del reproductor.
 *
 * La ventana de ExoPlayer es un tramo contiguo de la cola empezando en
 * [startIndex] (índice de cola). Cada cambio de cola que invalida resultados
 * en vuelo incrementa [generation].
 *
 * Mantener estos valores coherentes a mano ha producido los bugs de ventana
 * (B57, B58, B59, B62, B63). Este objeto los encapsula y mantiene el
 * invariante: ventana siempre contigua y alineada.
 */
data class WindowState(
    val startIndex: Int = 0,
    val generation: Int = 0,
) {
    companion object {
        val EMPTY = WindowState(startIndex = 0, generation = 0)
    }

    /** Invalida cargas en vuelo: nueva generación, ventana vuelve al origen. */
    fun invalidate(): WindowState = copy(generation = generation + 1, startIndex = 0)

    /** Mueve el ancla de la ventana al índice dado. */
    fun anchorAt(index: Int): WindowState = copy(startIndex = index)

    /** Desplaza la ventana hacia delante por [delta] items (tras recorte). */
    fun shiftForward(delta: Int): WindowState =
        if (delta <= 0) this else copy(startIndex = startIndex + delta)

    /** Reinicia la ventana (vacía). */
    fun reset(): WindowState = copy(startIndex = 0)

    /** Nueva generación manteniendo ancla actual (útil cuando la cola cambia pero se quiere conservar posición lógica). */
    fun bumpGeneration(): WindowState = copy(generation = generation + 1)
}
