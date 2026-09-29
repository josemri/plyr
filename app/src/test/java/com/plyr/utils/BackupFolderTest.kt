package com.plyr.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de los nombres de archivo que la copia de seguridad manages dentro de
 * la carpeta elegida.
 */
class BackupFolderTest {

    @Test
    fun `el archivo bueno se reconoce como nuestro`() {
        assertTrue(BackupFolder.isManagedFile(BackupFolder.BACKUP_FILE_NAME))
    }

    @Test
    fun `el temporal y el anterior tambien`() {
        assertTrue(BackupFolder.isManagedFile(BackupFolder.STAGING_FILE_NAME))
        assertTrue(BackupFolder.isManagedFile(BackupFolder.PREVIOUS_FILE_NAME))
    }

    @Test
    fun `los tres nombres son distintos entre si`() {
        val names = setOf(
            BackupFolder.BACKUP_FILE_NAME,
            BackupFolder.STAGING_FILE_NAME,
            BackupFolder.PREVIOUS_FILE_NAME
        )
        assertTrue("los nombres se solapan: $names", names.size == 3)
    }

    @Test
    fun `un archivo ajeno no se toca`() {
        // El ZIP con fecha del botón de exportar manual vive en la misma
        // carpeta a menudo: no se puede confundir con el de la copia automática.
        assertFalse(BackupFolder.isManagedFile("plyr-export-2026-09-29_14-33.zip"))
        assertFalse(BackupFolder.isManagedFile("canciones.mp3"))
    }

    @Test
    fun `un nombre vacio o nulo no es nuestro`() {
        assertFalse(BackupFolder.isManagedFile(null))
        assertFalse(BackupFolder.isManagedFile(""))
    }
}
