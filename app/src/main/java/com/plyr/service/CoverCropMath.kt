package com.plyr.service

/**
 * CoverCropMath - Matemática pura del recorte cuadrado de portadas.
 *
 * Modelo:
 * - La imagen original (imageW x imageH) se escala con [baseScale] * zoom para que
 *   cubra por completo un marco cuadrado de tamaño [square] (centrado en la vista).
 * - El centro de la imagen queda en el centro del marco más el desplazamiento
 *   (offsetX, offsetY) en píxeles escalados.
 * - [sourceRect] devuelve, en píxeles de la imagen original, el cuadrado que
 *   corresponde a la zona visible dentro del marco.
 *
 * Todas las funciones son puras (sin Android) para poder testearlas en JVM.
 */
data class CoverCropState(
    /** Zoom adicional sobre la escala base (>= 1) */
    val zoom: Float = 1f,
    /** Desplazamiento horizontal en píxeles escalados */
    val offsetX: Float = 0f,
    /** Desplazamiento vertical en píxeles escalados */
    val offsetY: Float = 0f
)

/**
 * Rectángulo (en píxeles de la imagen original) que ocupa el recorte.
 * Debido al modelo siempre es un cuadrado perfecto (width == height).
 */
data class CoverCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

object CoverCropMath {

    /**
     * Escala base para que la imagen (imageW x imageH) cubra por completo el
     * marco cuadrado de tamaño [square]. Es el "center-crop": se usa el mayor
     * de los dos factores.
     */
    fun baseScale(square: Float, imageW: Float, imageH: Float): Float {
        if (square <= 0f || imageW <= 0f || imageH <= 0f) return 1f
        return maxOf(square / imageW, square / imageH)
    }

    /**
     * Desplazamiento máximo permitido (en cada eje) para que el marco cuadrado
     * nunca muestre una zona fuera de la imagen. Si la imagen cubre de sobra en
     * un eje, se puede desplazar más; si lo cubre justo, el desplazamiento es 0.
     */
    fun maxOffset(square: Float, imageW: Float, imageH: Float, scale: Float): Pair<Float, Float> {
        val overX = maxOf(0f, (imageW * scale - square) / 2f)
        val overY = maxOf(0f, (imageH * scale - square) / 2f)
        return overX to overY
    }

    /**
     * Normaliza un estado de recorte: zoom >= 1 y desplazamiento dentro de los
     * límites correspondientes a la escala resultante.
     */
    fun clampState(state: CoverCropState, square: Float, imageW: Float, imageH: Float): CoverCropState {
        val zoom = maxOf(1f, state.zoom)
        val scale = baseScale(square, imageW, imageH) * zoom
        val (maxX, maxY) = maxOffset(square, imageW, imageH, scale)
        return state.copy(
            zoom = zoom,
            offsetX = state.offsetX.coerceIn(-maxX, maxX),
            offsetY = state.offsetY.coerceIn(-maxY, maxY)
        )
    }

    /**
     * Calcula el cuadrado de la imagen original visible dentro del marco.
     * El resultado está recortado a los límites de la imagen por seguridad.
     */
    fun sourceRect(square: Float, imageW: Float, imageH: Float, state: CoverCropState): CoverCropRect {
        val scale = baseScale(square, imageW, imageH) * maxOf(1f, state.zoom)
        // Centro de la imagen escalada que coincide con el centro del marco:
        val scaledCenterX = (imageW * scale) / 2f - state.offsetX
        val scaledCenterY = (imageH * scale) / 2f - state.offsetY
        val cx = scaledCenterX / scale
        val cy = scaledCenterY / scale
        val half = square / (2f * scale)
        return CoverCropRect(
            left = (cx - half).coerceIn(0f, imageW),
            top = (cy - half).coerceIn(0f, imageH),
            right = (cx + half).coerceIn(0f, imageW),
            bottom = (cy + half).coerceIn(0f, imageH)
        )
    }
}
