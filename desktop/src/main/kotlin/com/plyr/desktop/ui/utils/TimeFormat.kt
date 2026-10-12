package com.plyr.desktop.ui.utils

import java.util.Locale

/**
 * Formatea milisegundos a "MM:SS". Portado de `com.plyr.utils.formatTime` de
 * Android para que los controles muestren el tiempo igual en ambas plataformas.
 */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
