package com.plyr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la matemática de recorte cuadrado de portadas (CoverCropMath).
 * Comprueba que el recorte siempre es un cuadrado dentro de la imagen y que
 * los desplazamientos se limitan para no mostrar zonas fuera de la imagen.
 */
class CoverCropMathTest {

    private val square = 400f

    @Test
    fun baseScale_picksTheBiggestRatio() {
        // Imagen horizontal (16:9): debe cubrir por altura (altura menor que ancho)
        assertEquals(400f / 225f, CoverCropMath.baseScale(square, 640f, 225f), 0.0001f)
        // Imagen vertical: debe cubrir por ancho
        assertEquals(400f / 200f, CoverCropMath.baseScale(square, 200f, 500f), 0.0001f)
    }

    @Test
    fun baseScale_squareImageUsesSameRatio() {
        assertEquals(1f, CoverCropMath.baseScale(square, 400f, 400f), 0.0001f)
    }

    @Test
    fun baseScale_handlesInvalidSizes() {
        assertEquals(1f, CoverCropMath.baseScale(0f, 400f, 400f), 0.0001f)
        assertEquals(1f, CoverCropMath.baseScale(square, 0f, 400f), 0.0001f)
    }

    @Test
    fun maxOffset_isZeroWhenImageFitsExactly() {
        // Cuadrado exacto: no se puede desplazar sin mostrar fuera
        val (maxX, maxY) = CoverCropMath.maxOffset(square, 400f, 400f, 1f)
        assertEquals(0f, maxX, 0.0001f)
        assertEquals(0f, maxY, 0.0001f)
    }

    @Test
    fun maxOffset_allowsPanWhenImageHasSurplus() {
        // 16:9 horizontal, escala base 400/225: sobra a los lados
        val scale = CoverCropMath.baseScale(square, 640f, 225f)
        val (maxX, maxY) = CoverCropMath.maxOffset(square, 640f, 225f, scale)
        val expectedX = (640f * scale - square) / 2f
        assertTrue(maxX > 0f)
        assertEquals(expectedX, maxX, 0.0001f)
        assertEquals(0f, maxY, 0.0001f)
    }

    @Test
    fun sourceRect_noOffset_centersTheImage() {
        val rect = CoverCropMath.sourceRect(square, 640f, 225f, CoverCropState())
        val scale = CoverCropMath.baseScale(square, 640f, 225f)
        // El recorte es un cuadrado de square/scale píxeles de la imagen original
        val side = square / scale
        assertEquals(side, rect.width(), 0.0001f)
        assertEquals(side, rect.height(), 0.0001f)
        // Está centrado en la imagen
        assertEquals(640f / 2f, rect.left + rect.width() / 2f, 0.0001f)
        assertEquals(225f / 2f, rect.top + rect.height() / 2f, 0.0001f)
        // Nunca se sale de la imagen
        assertTrue(rect.left >= 0f && rect.right <= 640f)
        assertTrue(rect.top >= 0f && rect.bottom <= 225f)
    }

    @Test
    fun sourceRect_panRightMovesTheVisibleWindowLeft() {
        val scale = CoverCropMath.baseScale(square, 640f, 225f)
        val (maxX, _) = CoverCropMath.maxOffset(square, 640f, 225f, scale)
        val state = CoverCropState(zoom = 1f, offsetX = maxX, offsetY = 0f)
        val rect = CoverCropMath.sourceRect(square, 640f, 225f, state)
        // Al desplazar a la derecha del todo, la ventana visible queda pegada al borde izquierdo
        assertEquals(0f, rect.left, 0.0001f)
    }

    @Test
    fun sourceRect_panLeftMovesTheVisibleWindowRight() {
        val scale = CoverCropMath.baseScale(square, 640f, 225f)
        val (maxX, _) = CoverCropMath.maxOffset(square, 640f, 225f, scale)
        val state = CoverCropState(zoom = 1f, offsetX = -maxX, offsetY = 0f)
        val rect = CoverCropMath.sourceRect(square, 640f, 225f, state)
        assertEquals(640f, rect.right, 0.0001f)
    }

    @Test
    fun sourceRect_zoomKeepsSquareAndShrinksVisibleArea() {
        val state = CoverCropState(zoom = 2f, offsetX = 0f, offsetY = 0f)
        val rect = CoverCropMath.sourceRect(square, 640f, 225f, state)
        val scale = CoverCropMath.baseScale(square, 640f, 225f) * 2f
        // El recorte sigue siendo un cuadrado (square/scale), la mitad que sin zoom
        val side = square / scale
        assertEquals(side, rect.width(), 0.0001f)
        assertEquals(side, rect.height(), 0.0001f)
        assertEquals(112.5f, rect.width(), 0.0001f)
    }

    @Test
    fun sourceRect_zoomedRectStaysInsideImage() {
        val state = CoverCropState(zoom = 3f, offsetX = 500f, offsetY = -500f)
        val rect = CoverCropMath.sourceRect(square, 640f, 225f, state)
        assertTrue(rect.left >= 0f && rect.right <= 640f)
        assertTrue(rect.top >= 0f && rect.bottom <= 225f)
    }

    @Test
    fun clampState_forcesZoomToAtLeastOne() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 0.2f, offsetX = 999f, offsetY = 999f),
            square, 640f, 225f
        )
        assertEquals(1f, clamped.zoom, 0.0001f)
    }

    @Test
    fun clampState_clampsOffsetToMaxOffset() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 1f, offsetX = 9999f, offsetY = 9999f),
            square, 640f, 225f
        )
        val scale = CoverCropMath.baseScale(square, 640f, 225f)
        val (maxX, maxY) = CoverCropMath.maxOffset(square, 640f, 225f, scale)
        assertEquals(maxX, clamped.offsetX, 0.0001f)
        assertEquals(maxY, clamped.offsetY, 0.0001f)
    }

    @Test
    fun clampState_inRangeOffsetIsUnchanged() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 1.5f, offsetX = 10f, offsetY = -8f),
            square, 640f, 225f
        )
        assertEquals(1.5f, clamped.zoom, 0.0001f)
        assertEquals(10f, clamped.offsetX, 0.0001f)
        assertEquals(-8f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun sourceRect_verticalImageCenteredCrop() {
        val rect = CoverCropMath.sourceRect(square, 200f, 500f, CoverCropState())
        val scale = CoverCropMath.baseScale(square, 200f, 500f)
        val side = square / scale
        assertEquals(side, rect.width(), 0.0001f)
        assertEquals(side, rect.height(), 0.0001f)
        assertEquals(200f / 2f, rect.left + rect.width() / 2f, 0.0001f)
        assertEquals(500f / 2f, rect.top + rect.height() / 2f, 0.0001f)
    }

    @Test
    fun sourceRect_squareImageWithZoomIsCenteredSquare() {
        val rect = CoverCropMath.sourceRect(square, 400f, 400f, CoverCropState(zoom = 2f))
        assertEquals(200f, rect.width(), 0.0001f)
        assertEquals(400f / 2f, rect.left + rect.width() / 2f, 0.0001f)
    }
}
