package com.plyr.desktop.sync

import java.nio.file.Path

/**
 * Nombres y rutas de los archivos que la copia automática mantiene en la carpeta
 * elegida por el usuario.
 *
 * El archivo se llama siempre [BACKUP_FILE_NAME]: no se generan copias con fecha
 * porque el sentido de la copia automática es tener *una* copia siempre al día,
 * no un histórico. El temporal [STAGING_FILE_NAME] es donde se escribe antes de
 * pasar a su sitio, para que un ZIP a medias nunca destruya la copia buena.
 */
object BackupFolder {

    /** Nombre del archivo de copia dentro de la carpeta elegida. */
    const val BACKUP_FILE_NAME = "plyr-sync.zip"

    /** Temporal donde se escribe antes de renombrar. */
    const val STAGING_FILE_NAME = "plyr-sync.zip.part"

    /** El archivo anterior se aparta aquí mientras dura el intercambio. */
    const val PREVIOUS_FILE_NAME = "plyr-sync.zip.old"

    /** Archivo de copia dentro de [folder]. */
    fun backupFile(folder: Path): Path = folder.resolve(BACKUP_FILE_NAME)

    /** Temporal dentro de [folder]. */
    fun stagingFile(folder: Path): Path = folder.resolve(STAGING_FILE_NAME)

    /** Archivo apartado dentro de [folder]. */
    fun previousFile(folder: Path): Path = folder.resolve(PREVIOUS_FILE_NAME)

    /** ¿[name] es el nombre del archivo de copia (con o sin `.part`/`.old`)? */
    fun isManaged(name: String?): Boolean =
        name == BACKUP_FILE_NAME ||
            name == STAGING_FILE_NAME ||
            name == PREVIOUS_FILE_NAME
}
