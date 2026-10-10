package com.plyr.ui.components

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentados de la generación de QR ([generateQrBitmap]): necesitan
 * `android.graphics.Bitmap`, por eso no pueden ser JVM. Se comprueba el
 * round-trip real: el QR generado se vuelve a decodificar con zxing.
 */
@RunWith(AndroidJUnit4::class)
class QrCodeInstrumentedTest {

    private fun decode(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun generatedQrDecodesBackToContent() {
        val content = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        val bitmap = requireNotNull(generateQrBitmap(content))

        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertEquals(content, decode(bitmap))
    }

    @Test
    fun emptyContentReturnsNull() {
        assertNull(generateQrBitmap(""))
    }
}
