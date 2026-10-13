package com.plyr.desktop.sync

import com.plyr.desktop.config.DesktopSettings
import com.russhwolf.settings.Settings
import java.nio.file.Path

/**
 * Preferencias de la copia de seguridad automática del escritorio: la carpeta
 * elegida y la huella del último contenido escrito.
 *
 * Es el equivalente de `BackupConfig` de Android, pero sin SAF: la carpeta es
 * una ruta del sistema de archivos, así que no hace falta recordar `treeUri` ni
 * un `documentId`. Persiste en el mismo nodo de preferencias que [DesktopConfig].
 */
class BackupConfig(
    private val settings: Settings = DesktopSettings.create(),
) {

    /**
     * Carpeta donde se mantiene `plyr-sync.zip` actualizado. Null si el usuario
     * aún no ha elegido ninguna.
     */
    var folder: String?
        get() = settings.getStringOrNull(KEY_FOLDER)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) settings.remove(KEY_FOLDER)
            else settings.putString(KEY_FOLDER, value)
        }

    /** Huella [ExportBundle.contentHash] de la última copia escrita. */
    var hash: String?
        get() = settings.getStringOrNull(KEY_HASH)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) settings.remove(KEY_HASH)
            else settings.putString(KEY_HASH, value)
        }

    /** La copia automática está activa: hay carpeta elegida. */
    val isConfigured: Boolean get() = folder != null

    /** [folder] como [Path], o null si no hay carpeta configurada. */
    fun folderPath(): Path? = folder?.let { Path.of(it) }

    private companion object {
        const val KEY_FOLDER = "backup_folder"
        const val KEY_HASH = "backup_hash"
    }
}
