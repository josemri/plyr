package com.plyr.desktop.qr

import com.plyr.model.ScanResult
import com.plyr.utils.UrlParser
import java.io.File

/**
 * Lectura de un QR sin cámara (M11): el usuario pega una URL o abre una imagen
 * con el QR, y se decodifica a un [ScanResult] con el mismo parser que usa
 * Android (`[UrlParser.parseScanText]`).
 *
 * Es deliberadamente pequeño: el parseo es de `:core`, así que se testea tanto
 * el texto como una imagen PNG generada al vuelo.
 */
object QrScan {

    /** Decodifica una URL o id pegado a mano. `null` si no es reconocible. */
    fun fromText(text: String): ScanResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return UrlParser.parseScanText(trimmed)
    }

    /** Decodifica el QR de una imagen del disco. `null` si no hay o no se lee. */
    fun fromImage(file: File): ScanResult? {
        val text = QrImages.decodeImage(file) ?: return null
        return fromText(text)
    }
}
