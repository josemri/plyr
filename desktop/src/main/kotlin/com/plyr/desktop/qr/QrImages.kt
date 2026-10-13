package com.plyr.desktop.qr

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.plyr.utils.QrCodec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Transporte de imagen para el QR en escritorio (M11).
 *
 * La matriz y el round-trip de decodificación viven en `:core` (`[QrCodec]`); lo
 * que queda aquí es lo que cada plataforma aporta: pintar la matriz como
 * `ImageBitmap` de Compose, exportarla a PNG con `ImageIO` y leer los píxeles de
 * una imagen del disco para volver a decodificarla. Todo funciona en modo
 * *headless*, sin cámara.
 */
object QrImages {

    /** Convierte la rejilla en un `ImageBitmap` pintable con Compose Desktop. */
    fun toImageBitmap(matrix: QrCodec.QrMatrix): ImageBitmap {
        val pixels = QrCodec.toArgb(matrix)
        val bytes = ByteArray(pixels.size * 4)
        for (i in pixels.indices) {
            val value = (pixels[i] and 0xFF).toByte()
            val offset = i * 4
            bytes[offset] = value
            bytes[offset + 1] = value
            bytes[offset + 2] = value
            bytes[offset + 3] = 0xFF.toByte()
        }
        val info = ImageInfo(matrix.width, matrix.height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
        return Image.makeRaster(info, bytes, matrix.width * 4).toComposeImageBitmap()
    }

    /** Escribe la rejilla como PNG en [file]. */
    fun writePng(matrix: QrCodec.QrMatrix, file: File) {
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, matrix.width, matrix.height, QrCodec.toArgb(matrix), 0, matrix.width)
        ImageIO.write(image, "png", file)
    }

    /** Lee [file] y devuelve el primer QR que contenga, o `null`. */
    fun decodeImage(file: File): String? {
        val image = ImageIO.read(file) ?: return null
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        return QrCodec.decode(pixels, image.width, image.height)
    }
}
