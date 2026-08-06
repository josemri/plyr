package com.plyr.service

/**
 * CoverCropMath - Matemática pura del recorte cuadrado de portadas.
 *
 * Modelo:
 * - La imagen se dibuja rellenando por completo el viewport (viewportW x viewportH)
 *   con un "center-crop" (escala [coverScale]), y luego se transforma en la capa
 *   gráfica: escalado por [CoverCropState.zoom] alrededor de la esquina superior
 *   izquierda más una traslación [CoverCropState.offsetX]/[CoverCropState.offsetY].
 * - Un punto de la imagen en coordenadas de contenido (cx, cy) aparece en pantalla
 *   en (cx * zoom + offsetX, cy * zoom + offsetY).
 * - El zoom puede bajar de 1 (zoom-out) pero nunca por debajo de [minZoom], el
 *   punto en que la imagen dejaría de cubrir el marco cuadrado de recorte.
 * - El marco cuadrado de recorte ([square]) está centrado en el viewport y
 *   [sourceRect] devuelve, en píxeles de la imagen original, el cuadrado que
 *   corresponde a la zona visible dentro del marco.
 *
 * Todas las funciones son puras (sin Android) para poder testearlas en JVM.
 */
data class CoverCropState(
    /** Zoom (>= [CoverCropMath.minZoom]) sobre la escala base de cobertura */
    val zoom: Float = 1f,
    /** Traslación horizontal de la capa en píxeles de pantalla */
    val offsetX: Float = 0f,
    /** Traslación vertical de la capa en píxeles de pantalla */
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

    /** Zoom máximo permitido (por seguridad). */
    const val MAX_ZOOM = 8f

    /**
     * Escala para que la imagen (imageW x imageH) cubra por completo el
     * viewport (viewportW x viewportH). Es el "center-crop": se usa el mayor
     * de los dos factores para que la imagen siempre llene la pantalla.
     */
    fun coverScale(viewportW: Float, viewportH: Float, imageW: Float, imageH: Float): Float {
        if (viewportW <= 0f || viewportH <= 0f || imageW <= 0f || imageH <= 0f) return 1f
        return maxOf(viewportW / imageW, viewportH / imageH)
    }

    /**
     * Zoom mínimo para que la imagen siga cubriendo por completo el marco
     * cuadrado de recorte (siempre <= 1, ya que a zoom 1 la imagen cubre el
     * viewport, que contiene el marco).
     */
    fun minZoom(viewportW: Float, viewportH: Float, square: Float, imageW: Float, imageH: Float): Float {
        if (square <= 0f) return 1f
        val cover = coverScale(viewportW, viewportH, imageW, imageH)
        return maxOf(square / (imageW * cover), square / (imageH * cover))
    }

    /**
     * Zoom manteniendo fijo el punto de la imagen que está bajo el centroide del
     * gesto (que a su vez sigue el arrastre [panX]/[panY]). El zoom puede bajar
     * de 1 pero nunca por debajo de [minZoom].
     */
    fun focalZoom(
        state: CoverCropState,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        gestureZoom: Float,
        viewportW: Float,
        viewportH: Float,
        square: Float,
        imageW: Float,
        imageH: Float
    ): CoverCropState {
        val newZoom = maxOf(minZoom(viewportW, viewportH, square, imageW, imageH), state.zoom * gestureZoom)
        // Punto de la imagen (coordenadas de contenido) bajo el centroide antes del zoom
        val imgFX = (centroidX - state.offsetX) / state.zoom
        val imgFY = (centroidY - state.offsetY) / state.zoom
        return CoverCropState(
            zoom = newZoom,
            offsetX = (centroidX + panX) - imgFX * newZoom,
            offsetY = (centroidY + panY) - imgFY * newZoom
        )
    }

    /**
     * Normaliza un estado de recorte: zoom entre [minZoom] y [MAX_ZOOM], y
     * traslación limitada para que la imagen cubra **siempre el marco cuadrado
     * de recorte** (no el viewport completo). Mientras el marco quede cubierto,
     * la imagen se puede mover libremente por cualquier lado (nunca se queda
     * "pegada" ni bloqueada a un borde, sea cual sea su tamaño). Como el marco
     * es más pequeño que el viewport, la imagen dispone de margen de sobra.
     */
    fun clampState(
        state: CoverCropState,
        viewportW: Float,
        viewportH: Float,
        square: Float,
        imageW: Float,
        imageH: Float
    ): CoverCropState {
        val cover = coverScale(viewportW, viewportH, imageW, imageH)
        val zoom = state.zoom.coerceIn(minZoom(viewportW, viewportH, square, imageW, imageH), MAX_ZOOM)
        val w = imageW * cover * zoom
        val h = imageH * cover * zoom
        // Marco de recorte: cuadrado centrado en el viewport
        val fx0 = (viewportW - square) / 2f
        val fy0 = (viewportH - square) / 2f
        // La imagen debe cubrir el marco por los cuatro lados (zoom >= minZoom
        // garantiza w,h >= square). Si no cupiera en un eje, se centra en el marco.
        val minX = if (w >= square) fx0 + square - w else fx0 + (square - w) / 2f
        val maxX = if (w >= square) fx0 else minX
        val minY = if (h >= square) fy0 + square - h else fy0 + (square - h) / 2f
        val maxY = if (h >= square) fy0 else minY
        return state.copy(
            zoom = zoom,
            offsetX = state.offsetX.coerceIn(minX, maxX),
            offsetY = state.offsetY.coerceIn(minY, maxY)
        )
    }

    /**
     * Calcula el cuadrado de la imagen original visible dentro del marco de
     * recorte (cuadrado [square] centrado en el viewport).
     * El resultado está recortado a los límites de la imagen por seguridad.
     */
    fun sourceRect(
        viewportW: Float,
        viewportH: Float,
        square: Float,
        imageW: Float,
        imageH: Float,
        state: CoverCropState
    ): CoverCropRect {
        val cover = coverScale(viewportW, viewportH, imageW, imageH)
        val zoom = state.zoom
        // Centro de la imagen original dentro del viewport (crop centrado)
        val bx0 = (viewportW - imageW * cover) / 2f
        val by0 = (viewportH - imageH * cover) / 2f
        val left = (((viewportW - square) / 2f - state.offsetX) / zoom - bx0) / cover
        val top = (((viewportH - square) / 2f - state.offsetY) / zoom - by0) / cover
        val side = square / (zoom * cover)
        return CoverCropRect(
            left = left.coerceIn(0f, imageW),
            top = top.coerceIn(0f, imageH),
            right = (left + side).coerceIn(0f, imageW),
            bottom = (top + side).coerceIn(0f, imageH)
        )
    }
}
