package com.plyr.utils

import android.annotation.SuppressLint

fun isValidAudioUrl(url: String): Boolean {
    println("Utils: Validando URL: $url")
    
    // Verificar si es una URL válida
    val isValidUrl = url.startsWith("http://") || url.startsWith("https://")
    
    if (!isValidUrl) {
        println("Utils: URL no válida - no empieza con http(s)")
        return false
    }
    
    // Aceptar URLs que contengan formatos de audio comunes o YouTube patterns
    val audioPatterns = listOf(
        "videoplayback",     // YouTube direct URLs
        "mime=audio",        // YouTube audio MIME
        ".mp3", ".m4a", ".aac", ".ogg", ".wav", ".flac",  // Audio file extensions
        "/audio/",           // Audio directory pattern
        "audio=",            // Audio parameter
        "googlevideo.com",   // YouTube video URLs
        "ytimg.com"          // YouTube image/audio URLs
    )
    
    val hasAudioPattern = audioPatterns.any { pattern -> 
        url.lowercase().contains(pattern.lowercase())
    }
    
    println("Utils: URL contiene patrón de audio: $hasAudioPattern")
    return hasAudioPattern || isValidUrl  // Si no encuentra patrón específico, acepta URLs válidas
}

@SuppressLint("DefaultLocale")
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
