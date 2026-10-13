package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip del código QR compartido (M11): se genera la rejilla, se convierte
 * a píxeles ARGB y se vuelve a decodificar, sin cámara ni ficheros.
 */
class QrCodecTest {

    private val content = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test
    fun generarYDecodificar_devuelveElMismoContenido() {
        val matrix = QrCodec.generate(content)

        assertNotNull(matrix)
        val pixels = QrCodec.toArgb(matrix!!)
        val decoded = QrCodec.decode(pixels, matrix.width, matrix.height)

        assertEquals(content, decoded)
    }

    @Test
    fun generaElTamanoPedido() {
        val matrix = QrCodec.generate(content, size = 200)

        assertNotNull(matrix)
        assertEquals(200, matrix!!.width)
        assertEquals(200, matrix.height)
    }

    @Test
    fun contenidoVacio_devuelveNull() {
        assertNull(QrCodec.generate(""))
        assertNull(QrCodec.generate("   "))
    }

    @Test
    fun tamanoInvalido_devuelveNull() {
        assertNull(QrCodec.generate(content, size = 0))
        assertNull(QrCodec.generate(content, size = -1))
    }

    @Test
    fun pixelesDeSoloBlanco_noDecodifican() {
        val pixels = IntArray(64 * 64) { QrCodec.WHITE }

        assertNull(QrCodec.decode(pixels, 64, 64))
    }

    @Test
    fun dimensionesInvalidas_devuelvenNull() {
        assertNull(QrCodec.decode(IntArray(4), 0, 0))
        assertNull(QrCodec.decode(IntArray(4), 8, 8))
    }

    @Test
    fun laRejillaMarcaModulosOscuros() {
        val matrix = QrCodec.generate(content, size = 100)
        assertNotNull(matrix)
        val dark = (0 until matrix!!.height).sumOf { y ->
            (0 until matrix.width).count { x -> matrix.isDark(x, y) }
        }

        // Un QR tiene una mezcla de módulos negros y blancos, nunca un lienzo
        // entero de un solo color ni un cuadrado vacío.
        assertTrue("La rejilla debe tener módulos oscuros", dark > 0)
        assertTrue("La rejilla no puede ser toda negra", dark < matrix.width * matrix.height)
    }
}
