package com.plyr.utils

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generación y decodificación de códigos QR, compartida entre Android y
 * escritorio (M11).
 *
 * Toda la dependencia de ZXing y la conversión a píxeles viven aquí; cada
 * plataforma solo aporta el transporte de imagen: `android.graphics.Bitmap` en
 * Android y `ImageIO`/Skia en escritorio. Es lógica pura, así que se testea en
 * JVM con un round-trip generar → píxeles → decodificar, sin cámara ni ficheros.
 */
object QrCodec {

    /** Lado del cuadrado del QR generado, en píxeles. */
    const val DEFAULT_SIZE = 512

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * Rejilla de módulos de un QR. `dark` está en orden de filas: el índice de
     * `(x, y)` es `y * width + x`.
     */
    class QrMatrix(val width: Int, val height: Int, private val dark: BooleanArray) {
        fun isDark(x: Int, y: Int): Boolean = dark[y * width + x]
    }

    /**
     * Genera la rejilla del QR de [content] a [size] píxeles de lado. Devuelve
     * `null` si el contenido está vacío o ZXing no puede codificarlo.
     */
    fun generate(content: String, size: Int = DEFAULT_SIZE): QrMatrix? {
        if (content.isBlank() || size <= 0) return null
        return try {
            val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val dark = BooleanArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    if (bits[x, y]) dark[y * size + x] = true
                }
            }
            QrMatrix(size, size, dark)
        } catch (_: Exception) {
            null
        }
    }

    /** Píxeles ARGB de la rejilla, en orden de filas (listos para `setPixels`). */
    fun toArgb(matrix: QrMatrix, dark: Int = BLACK, light: Int = WHITE): IntArray {
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix.isDark(x, y)) dark else light
            }
        }
        return pixels
    }

    /**
     * Decodifica el primer código QR de los píxeles ARGB dados. Devuelve `null`
     * si no hay ninguno legible.
     */
    fun decode(pixels: IntArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return try {
            val source = RGBLuminanceSource(width, height, pixels)
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: Exception) {
            null
        }
    }
}
