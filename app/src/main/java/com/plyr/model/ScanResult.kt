package com.plyr.model

/**
 * Resultado de un escaneo (QR o NFC): fuente, tipo e ID del contenido.
 * Sustituye a los antiguos QrScanResult y NfcScanResult.
 */
data class ScanResult(
    val source: String, // "spotify" or "youtube"
    val type: String,   // "track", "playlist", "album", "artist"
    val id: String
)
