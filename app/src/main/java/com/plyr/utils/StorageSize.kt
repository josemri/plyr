package com.plyr.utils

import java.util.Locale

/**
 * Formatea un tamaño en bytes a una unidad legible (`512 B`, `1.5 MB`...).
 *
 * Puro y sin Android, para poder testearlo. Usa [Locale.ROOT] porque el
 * resultado es técnico (no texto de usuario) y no debe depender del idioma.
 */
object StorageSize {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun format(bytes: Long): String = when {
        bytes < KB -> "$bytes B"
        bytes < MB -> String.format(Locale.ROOT, "%.1f KB", bytes / KB)
        bytes < GB -> String.format(Locale.ROOT, "%.1f MB", bytes / MB)
        else -> String.format(Locale.ROOT, "%.2f GB", bytes / GB)
    }
}
