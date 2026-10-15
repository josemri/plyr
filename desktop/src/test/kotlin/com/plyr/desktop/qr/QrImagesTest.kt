package com.plyr.desktop.qr

import com.plyr.utils.QrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.imageio.ImageIO

/**
 * Transporte de imagen del QR de escritorio (M11): el PNG que guarda el diálogo de
 * compartir se escribe con el tamaño de la matriz y la matriz se pinta como
 * `ImageBitmap` de Compose. El lector de QR sin cámara se retiró en M28.
 */
class QrImagesTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun elPngGuardadoSeEscribeConElTamanoDeLaMatriz() {
        val matrix = QrCodec.generate("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")
        assertNotNull(matrix)

        val file = temp.newFile("qr.png")
        QrImages.writePng(matrix!!, file)

        val image = ImageIO.read(file)
        assertEquals(matrix.width, image.width)
        assertEquals(matrix.height, image.height)
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
