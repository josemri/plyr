package com.plyr.desktop.sync

import com.plyr.core.util.Logger
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

private const val TAG = "CoverImages"

/**
 * Recorte y reescalado de portadas para el ZIP de exportación.
 *
 * Es el equivalente de escritorio del tramo de `DataExporter` de Android que usa
 * `BitmapFactory`/`Bitmap`: `java.nio` no trae decodificador de imagen, así que
 * aquí se usa `javax.imageio`, que forma parte de la JVM. La política es la
 * misma: recortar por el centro a un cuadrado de [MAX_SIDE] px en JPEG.
 */
object CoverImages {

    /** Lado del cuadrado al que se normalizan las portadas exportadas. */
    const val MAX_SIDE = 512

    /** Calidad JPEG; igual que en Android (`EXPORT_COVER_QUALITY`). */
    private const val QUALITY = 0.85f

    /**
     * Recorta [bytes] por el centro a un cuadrado de [MAX_SIDE] px y lo recomprime
     * como JPEG. Devuelve null si la imagen no se pudo decodificar o ya es lo
     * bastante pequeña (`DataExporter` escribe entonces los bytes originales).
     */
    fun shrinkToSquare(bytes: ByteArray, maxSide: Int = MAX_SIDE): ByteArray? {
        val source = decode(bytes) ?: return null
        val longestSide = maxOf(source.width, source.height)
        if (longestSide <= maxSide) return null

        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val square = source.getSubimage(left, top, side, side)

        val scaled = BufferedImage(maxSide, maxSide, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, maxSide, maxSide)
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.drawImage(square, 0, 0, maxSide, maxSide, null)
        } finally {
            graphics.dispose()
        }

        return encodeJpeg(scaled)
    }

    private fun decode(bytes: ByteArray): BufferedImage? = try {
        ByteArrayInputStream(bytes).use { ImageIO.read(it) }
    } catch (e: Exception) {
        Logger.w(TAG, "No se pudo decodificar la portada: ${e.message}")
        null
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray? = try {
        val output = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        if (!writers.hasNext()) {
            // Sin escritor JPEG explícito se recurre al de ImageIO, que siempre
            // está: la calidad por defecto es algo menor, pero no invalida nada.
            ImageIO.write(image, "jpg", output)
        } else {
            val writer = writers.next()
            MemoryCacheImageOutputStream(output).use { stream ->
                writer.output = stream
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = QUALITY
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
            writer.dispose()
        }
        output.toByteArray()
    } catch (e: Exception) {
        Logger.w(TAG, "No se pudo recomprimir la portada: ${e.message}")
        null
    }
}
