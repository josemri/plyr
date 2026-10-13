package com.plyr.desktop.qr

import com.plyr.utils.QrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Transporte de imagen y lectura del QR de escritorio (M11): el PNG generado se
 * vuelve a decodificar y las URLs se traducen al mismo `ScanResult` que Android.
 */
class QrImagesTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun elPngGuardadoSeDecodifica() {
        val content = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val matrix = QrCodec.generate(content)
        assertNotNull(matrix)

        val file = temp.newFile("qr.png")
        QrImages.writePng(matrix!!, file)

        assertEquals(content, QrImages.decodeImage(file))
    }

    @Test
    fun scanFromImage_leeElQrGuardado() {
        val matrix = QrCodec.generate("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(matrix)

        val file = temp.newFile("track.png")
        QrImages.writePng(matrix!!, file)

        val result = QrScan.fromImage(file)
        assertEquals("youtube", result?.source)
        assertEquals("track", result?.type)
        assertEquals("dQw4w9WgXcQ", result?.id)
    }

    @Test
    fun scanFromText_reconoceUnaUrlDeYoutube() {
        val result = QrScan.fromText("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals("youtube", result?.source)
        assertEquals("dQw4w9WgXcQ", result?.id)
    }

    @Test
    fun scanFromText_reconoceUnaListaDeSpotify() {
        val result = QrScan.fromText("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        assertEquals("spotify", result?.source)
        assertEquals("playlist", result?.type)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", result?.id)
    }

    @Test
    fun scanFromText_vacio_devuelveNull() {
        assertEquals(null, QrScan.fromText("   "))
        assertEquals(null, QrScan.fromText("no es una url"))
    }

    @Test
    fun laMatrizSePintaComoImageBitmap() {
        val matrix = QrCodec.generate("hola")
        assertNotNull(matrix)

        val bitmap = QrImages.toImageBitmap(matrix!!)

        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }
}
