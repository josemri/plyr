package com.plyr.utils

import android.annotation.SuppressLint

/**
 * Utils - Funciones utilitarias para la aplicación
 * 
 * Contiene funciones auxiliares para:
 * - Validación de URLs de audio
 * - Formateo de tiempo
 * - Otras utilidades comunes
 */

/**
 * Valida si una URL es apropiada para reproducción de audio.
 * 
 * Verifica que la URL:
 * - Sea una URL válida (http/https)
 * - Contenga patrones relacionados con audio
 * - Sea compatible con ExoPlayer y formatos de audio comunes
 * 
 * @param url URL a validar
 * @return true si la URL es válida para audio, false en caso contrario
 */
fun isValidAudioUrl(url: String): Boolean {
    if (!isValidUrlFormat(url)) {
        return false
    }
    val hasAudioPattern = containsAudioPattern(url)
    return hasAudioPattern || isValidUrlFormat(url)
}

/**
 * Verifica si la URL tiene un formato válido (http/https).
 * @param url URL a verificar
 * @return true si el formato es válido
 */
private fun isValidUrlFormat(url: String): Boolean {
    return url.startsWith("http://") || url.startsWith("https://")
}

/**
 * Verifica si la URL contiene patrones relacionados con audio.
 * @param url URL a verificar
 * @return true si contiene patrones de audio
 */
private fun containsAudioPattern(url: String): Boolean {
    val audioPatterns = listOf(
        // Patrones de YouTube
        "videoplayback",        // URLs directas de YouTube
        "mime=audio",          // MIME type de audio de YouTube
        "googlevideo.com",     // Dominio de videos de Google/YouTube
        "ytimg.com",           // Dominio de recursos de YouTube
        
        // Extensiones de archivo de audio
        ".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac", ".opus",
        
        // Patrones genéricos de audio
        "/audio/",             // Directorio de audio
        "audio=",              // Parámetro de audio
        "audio/",              // MIME type path
        "sound/"               // Directorio de sonido
    )
    
    return audioPatterns.any { pattern -> 
        url.lowercase().contains(pattern.lowercase())
    }
}

/**
 * Formatea tiempo en milisegundos a formato MM:SS.
 * 
 * @param ms Tiempo en milisegundos
 * @return Tiempo formateado como "MM:SS" (ej: "03:45")
 */
@SuppressLint("DefaultLocale")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Formatea una duración en milisegundos a "M:SS" (minutos sin cero a la izquierda).
 * Sustituye a los formateadores inline de SongListItem y SongMenuDialog.
 *
 * @param ms Duración en milisegundos
 * @return Duración formateada como "M:SS" (ej: "3:45")
 */
fun formatDurationMs(ms: Number): String {
    val totalSeconds = ms.toLong() / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * Formatea una duración en segundos a "MM:SS" o "HH:MM:SS". Sustituye a
 * getFormattedDuration de YouTubeSearchManager.
 *
 * @param totalSeconds Duración en segundos
 * @return Duración formateada, o "En vivo" si es <= 0
 */
fun formatDurationSeconds(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "En vivo"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Formatea un timestamp a tiempo relativo ("now", "5m", "3h", "2d").
 *
 * @param timestamp Timestamp en milisegundos
 * @return Tiempo relativo en formato corto
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

