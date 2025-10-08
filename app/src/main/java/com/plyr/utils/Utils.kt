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
    println("Utils: Validando URL: $url")
    
    // Verificar formato básico de URL
    if (!isValidUrlFormat(url)) {
        println("Utils: URL no válida - formato incorrecto")
        return false
    }
    
    // Verificar patrones de audio conocidos
    val hasAudioPattern = containsAudioPattern(url)
    println("Utils: URL contiene patrón de audio: $hasAudioPattern")
    
    // Aceptar URLs válidas con patrones de audio o URLs generales válidas
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

