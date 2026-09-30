package com.plyr.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Utils - Funciones utilitarias para la aplicación
 * 
 * Contiene funciones auxiliares para:
 * - Validación de URLs de audio
 * - Formateo de tiempo
 * - Otras utilidades comunes
 */

/**
 * Obtiene el PackageInfo evitando el overload deprecado en API 33+.
 * En API 33+ usa [PackageManager.PackageInfoFlags].
 */
@SuppressLint("NewApi")
fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, flags)
    }
}

/**
 * Formatea tiempo en milisegundos a formato MM:SS.
 * 
 * @param ms Tiempo en milisegundos
 * @return Tiempo formateado como "MM:SS" (ej: "03:45")
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
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

