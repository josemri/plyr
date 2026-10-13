package com.plyr.utils

/**
 * Formatea un timestamp a tiempo relativo corto ("now", "5m", "3h", "2d").
 *
 * Vive en `:core` porque lo usan las dos plataformas: la app Android ya lo tenía
 * en `Utils.kt` y el feed del escritorio (M12) muestra la antigüedad de cada
 * recomendación. Es puro salvo por "ahora", que se lee del reloj del sistema.
 *
 * Un timestamp `<= 0` es ilegible/desconocido (B25): se devuelve "unknown" en
 * vez de disfrazarlo de recién creado.
 */
fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "unknown"
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
