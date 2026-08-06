package com.plyr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la matemática de recorte cuadrado de portadas (CoverCropMath).
 * Comprueba que el zoom focal mantiene el punto bajo los dedos, que el zoom-out
 * está limitado para no dejar huecos en el marco de recorte y que el recorte
 * siempre es un cuadrado dentro de la imagen.
 */
class CoverCropMathTest {

    private val viewportW = 800f
    private val viewportH = 450f
    private val square = 400f

    @Test
    fun coverScale_picksTheBiggestRatio() {
        // Imagen vertical en viewport horizontal: debe cubrir por ancho
        assertEquals(4f, CoverCropMath.coverScale(viewportW, viewportH, 200f, 500f), 0.0001f)
        // Imagen horizontal en viewport vertical: debe cubrir por altura
        assertEquals(4.5f, CoverCropMath.coverScale(viewportW, viewportH, 200f, 100f), 0.0001f)
        // Imagen 16:9 en viewport 16:9: mismo ratio
        assertEquals(0.5f, CoverCropMath.coverScale(viewportW, viewportH, 1600f, 900f), 0.0001f)
    }

    @Test
    fun coverScale_squareImageUsesViewportRatio() {
        // Imagen cuadrada en viewport horizontal: la limita el ancho
        assertEquals(1f, CoverCropMath.coverScale(viewportW, viewportH, 800f, 800f), 0.0001f)
    }

    @Test
    fun coverScale_handlesInvalidSizes() {
        assertEquals(1f, CoverCropMath.coverScale(0f, 450f, 400f, 400f), 0.0001f)
        assertEquals(1f, CoverCropMath.coverScale(800f, 0f, 400f, 400f), 0.0001f)
        assertEquals(1f, CoverCropMath.coverScale(800f, 450f, 0f, 400f), 0.0001f)
    }

    @Test
    fun minZoom_coversTheFrame() {
        // 16:9: la altura (450) limita; la imagen cubre el marco hasta 400/450
        assertEquals(400f / 450f, CoverCropMath.minZoom(viewportW, viewportH, square, 1600f, 900f), 0.0001f)
        // Cuadrada: zoom-out hasta que el lado llega al marco (400/800)
        assertEquals(0.5f, CoverCropMath.minZoom(viewportW, viewportH, square, 800f, 800f), 0.0001f)
        // Vertical: la limita el ancho (400/800)
        assertEquals(0.5f, CoverCropMath.minZoom(viewportW, viewportH, square, 200f, 500f), 0.0001f)
    }

    @Test
    fun focalZoom_keepsFocalPointFixed() {
        val result = CoverCropMath.focalZoom(
            CoverCropState(), 200f, 350f, 0f, 0f, 2f,
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(2f, result.zoom, 0.0001f)
        assertEquals(-200f, result.offsetX, 0.0001f)
        assertEquals(-350f, result.offsetY, 0.0001f)
        // El punto de la imagen bajo el centroide sigue en el mismo sitio:
        // contenido x=200 -> pantalla 200*2 + (-200) = 200
        assertEquals(200f, 200f * result.zoom + result.offsetX, 0.0001f)
        assertEquals(350f, 350f * result.zoom + result.offsetY, 0.0001f)
    }

    @Test
    fun focalZoom_followsPan() {
        val result = CoverCropMath.focalZoom(
            CoverCropState(), 200f, 350f, 10f, 5f, 2f,
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(2f, result.zoom, 0.0001f)
        assertEquals(-190f, result.offsetX, 0.0001f)
        assertEquals(-345f, result.offsetY, 0.0001f)
        // El punto sigue al centroide + pan
        assertEquals(210f, 200f * result.zoom + result.offsetX, 0.0001f)
        assertEquals(355f, 350f * result.zoom + result.offsetY, 0.0001f)
    }

    @Test
    fun focalZoom_onZoomedStateKeepsCenterFixed() {
        val state = CoverCropState(zoom = 2f, offsetX = -400f, offsetY = -225f)
        val result = CoverCropMath.focalZoom(
            state, 400f, 225f, 0f, 0f, 1.5f,
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(3f, result.zoom, 0.0001f)
        assertEquals(-800f, result.offsetX, 0.0001f)
        assertEquals(-450f, result.offsetY, 0.0001f)
        // El centro del viewport (contenido x=400) sigue fijo
        assertEquals(400f, 400f * result.zoom + result.offsetX, 0.0001f)
    }

    @Test
    fun focalZoom_zoomOutAllowedAndKeepsFocalPoint() {
        // Zoom-out hasta el mínimo permitido (0.8889 para 16:9)
        val result = CoverCropMath.focalZoom(
            CoverCropState(), 400f, 225f, 0f, 0f, 0.5f,
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(400f / 450f, result.zoom, 0.0001f)
        assertEquals(400f - 400f * (400f / 450f), result.offsetX, 0.001f)
        assertEquals(225f - 225f * (400f / 450f), result.offsetY, 0.001f)
        // El punto bajo el centroide sigue fijo
        assertEquals(400f, 400f * result.zoom + result.offsetX, 0.0001f)
        assertEquals(225f, 225f * result.zoom + result.offsetY, 0.0001f)
    }

    @Test
    fun focalZoom_zoomOutClampedAtMinZoom() {
        val result = CoverCropMath.focalZoom(
            CoverCropState(zoom = 2f, offsetX = -100f, offsetY = -50f),
            400f, 225f, 0f, 0f, 0.2f,
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(400f / 450f, result.zoom, 0.0001f)
        assertEquals(400f - 250f * (400f / 450f), result.offsetX, 0.001f)
        assertEquals(225f - 137.5f * (400f / 450f), result.offsetY, 0.001f)
    }

    @Test
    fun clampState_forcesZoomToAtLeastMinZoomAndCoversFrame() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 0.2f, offsetX = 999f, offsetY = 999f),
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(400f / 450f, clamped.zoom, 0.0001f)
        // La imagen (711x400) solo debe cubrir el marco, que va de x=200 a 600:
        // el offset queda limitado al margen que cubre el marco, no al viewport.
        assertEquals(200f, clamped.offsetX, 0.0001f)
        assertEquals(25f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun clampState_centersImageWhenSmallerThanViewport() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 0.3f, offsetX = 500f, offsetY = 500f),
            viewportW, viewportH, square, 800f, 800f
        )
        // minZoom 0.5 -> imagen 400x400 == marco: queda encajada en el marco
        assertEquals(0.5f, clamped.zoom, 0.0001f)
        assertEquals(200f, clamped.offsetX, 0.0001f)
        assertEquals(25f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun clampState_16by9ImageCanMoveWhileFrameCovered() {
        // Antes, una imagen 16:9 a zoom 1 quedaba congelada ([0,0]); ahora puede
        // deslizarse mientras el marco de recorte quede cubierto.
        val inRange = CoverCropMath.clampState(
            CoverCropState(zoom = 1f, offsetX = 150f, offsetY = -10f),
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(1f, inRange.zoom, 0.0001f)
        assertEquals(150f, inRange.offsetX, 0.0001f)
        assertEquals(-10f, inRange.offsetY, 0.0001f)
        // Rango horizontal [-200, 200] y vertical [-25, 25]
        val extremes = CoverCropMath.clampState(
            CoverCropState(zoom = 1f, offsetX = 999f, offsetY = -999f),
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(200f, extremes.offsetX, 0.0001f)
        assertEquals(-25f, extremes.offsetY, 0.0001f)
    }

    @Test
    fun clampState_wideImageCanMoveVertically() {
        // Imagen muy ancha (3000x1000): antes quedaba clavada verticalmente ([0,0]).
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 1f, offsetX = 0f, offsetY = 10f),
            viewportW, viewportH, square, 3000f, 1000f
        )
        assertEquals(10f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun clampState_tallImageCanMoveHorizontally() {
        // Imagen muy alta (200x500): antes quedaba clavada horizontalmente ([0,0]).
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 1f, offsetX = 100f, offsetY = 0f),
            viewportW, viewportH, square, 200f, 500f
        )
        assertEquals(100f, clamped.offsetX, 0.0001f)
    }

    @Test
    fun clampState_clampsOffsetToBounds() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 2f, offsetX = 500f, offsetY = -900f),
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(2f, clamped.zoom, 0.0001f)
        // A zoom 2: imagen 1600x900; el marco (x 200..600, y 25..425) exige
        // offsetX en [-1000, 200] y offsetY en [-475, 25].
        assertEquals(200f, clamped.offsetX, 0.0001f)
        assertEquals(-475f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun clampState_inRangeOffsetIsUnchanged() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 2f, offsetX = -400f, offsetY = -100f),
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(2f, clamped.zoom, 0.0001f)
        assertEquals(-400f, clamped.offsetX, 0.0001f)
        assertEquals(-100f, clamped.offsetY, 0.0001f)
    }

    @Test
    fun clampState_capsZoomAtMax() {
        val clamped = CoverCropMath.clampState(
            CoverCropState(zoom = 99f, offsetX = 0f, offsetY = 0f),
            viewportW, viewportH, square, 1600f, 900f
        )
        assertEquals(CoverCropMath.MAX_ZOOM, clamped.zoom, 0.0001f)
    }

    @Test
    fun sourceRect_noOffset_centeredCrop() {
        val rect = CoverCropMath.sourceRect(viewportW, viewportH, square, 1600f, 900f, CoverCropState())
        assertEquals(800f, rect.width(), 0.0001f)
        assertEquals(800f, rect.height(), 0.0001f)
        // Centrado en la imagen
        assertEquals(1600f / 2f, rect.left + rect.width() / 2f, 0.0001f)
        assertEquals(900f / 2f, rect.top + rect.height() / 2f, 0.0001f)
        assertTrue(rect.left >= 0f && rect.right <= 1600f)
        assertTrue(rect.top >= 0f && rect.bottom <= 900f)
    }

    @Test
    fun sourceRect_zoomShrinksSide() {
        val rect = CoverCropMath.sourceRect(
            viewportW, viewportH, square, 1600f, 900f,
            CoverCropState(zoom = 2f)
        )
        // El recorte es un cuadrado (square/(zoom*cover)): la mitad que sin zoom
        assertEquals(400f, rect.width(), 0.0001f)
        assertEquals(400f, rect.height(), 0.0001f)
        assertEquals(400f, rect.width(), 0.0001f)
    }

    @Test
    fun sourceRect_zoomOutGrowsSide() {
        // Al mínimo de zoom la imagen queda centrada: se captura la imagen completa
        val rect = CoverCropMath.sourceRect(
            viewportW, viewportH, square, 1600f, 900f,
            CoverCropState(zoom = 400f / 450f, offsetX = 44.444f, offsetY = 25f)
        )
        assertEquals(900f, rect.width(), 0.001f)
        assertEquals(900f, rect.height(), 0.001f)
        assertEquals(0f, rect.top, 0.0001f)
        assertEquals(900f, rect.bottom, 0.001f)
        assertTrue(rect.left >= 0f && rect.right <= 1600f)
    }

    @Test
    fun sourceRect_offsetMovesWindow() {
        val centered = CoverCropMath.sourceRect(viewportW, viewportH, square, 1600f, 900f, CoverCropState(zoom = 2f))
        val moved = CoverCropMath.sourceRect(
            viewportW, viewportH, square, 1600f, 900f,
            CoverCropState(zoom = 2f, offsetX = -200f)
        )
        // Trasladar la capa a la izquierda muestra más zona derecha de la imagen
        assertTrue(moved.left > centered.left)
        assertEquals(400f, moved.left, 0.0001f)
        assertTrue(moved.left >= 0f && moved.right <= 1600f)
    }

    @Test
    fun sourceRect_staysInsideImage() {
        val rect = CoverCropMath.sourceRect(
            viewportW, viewportH, square, 1600f, 900f,
            CoverCropState(zoom = 3f, offsetX = 9999f, offsetY = -9999f)
        )
        assertTrue(rect.left >= 0f && rect.right <= 1600f)
        assertTrue(rect.top >= 0f && rect.bottom <= 900f)
    }

    @Test
    fun sourceRect_verticalImageCenteredCrop() {
        val rect = CoverCropMath.sourceRect(viewportW, viewportH, square, 200f, 500f, CoverCropState())
        assertEquals(100f, rect.width(), 0.0001f)
        assertEquals(100f, rect.height(), 0.0001f)
        assertEquals(200f / 2f, rect.left + rect.width() / 2f, 0.0001f)
        assertEquals(500f / 2f, rect.top + rect.height() / 2f, 0.0001f)
    }

    @Test
    fun sourceRect_squareImageWithZoomIsCenteredSquare() {
        val rect = CoverCropMath.sourceRect(
            viewportW, viewportH, square, 800f, 800f,
            CoverCropState(zoom = 2f)
        )
        // Zoom sobre la esquina superior izquierda sin arrastre: se ve la zona
        // superior-izquierda de la imagen, y el recorte sigue siendo cuadrado.
        assertEquals(200f, rect.width(), 0.0001f)
        assertEquals(200f, rect.height(), 0.0001f)
        assertEquals(100f, rect.left, 0.0001f)
        assertEquals(187.5f, rect.top, 0.0001f)
    }
}
