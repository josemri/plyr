package com.plyr.desktop.download

import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.sync.BackupConfig
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Almacén de audio offline sobre `java.nio`. La carpeta se inyecta para no tocar
 * los datos reales del usuario; "descargado" debe ser, literalmente, que exista
 * el fichero.
 */
class DownloadedAudioStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = DownloadedAudioStore(directory = tmp.newFolder().toPath())

    @Test
    fun isDownloaded_soloSiExisteElFichero() {
        val store = store()
        assertFalse(store.isDownloaded("vid"))
        assertNull(store.localUri("vid"))

        Files.createDirectories(store.directory)
        Files.write(store.fileFor("vid"), byteArrayOf(1, 2, 3))

        assertTrue(store.isDownloaded("vid"))
        assertTrue(store.localUri("vid")!!.endsWith("/vid.m4a"))
    }

    @Test
    fun porDefectoUsaLaSubcarpetaAudioDeLaCarpetaDeSync() {
        val backup = tmp.newFolder().toPath()
        val config = BackupConfig(MapSettings()).apply { folder = backup.toString() }

        // Como Android: `<carpeta de sync>/audio`, junto al plyr-sync.zip.
        assertEquals(backup.resolve("audio"), DownloadedAudioStore(config).directory)
    }

    @Test
    fun sinCarpetaDeSyncCaeALaCarpetaDeDatos() {
        val config = BackupConfig(MapSettings())

        assertEquals(
            Path.of(AppPaths.directories.dataDir, "audio"),
            DownloadedAudioStore(config).directory,
        )
    }

    @Test
    fun blankVideoIdNuncaEstaDescargado() {
        val store = store()
        assertFalse(store.isDownloaded(""))
        assertFalse(store.isDownloaded(" "))
    }

    @Test
    fun write_escribeDeFormaAtomicaYNoDejaTemporales() = runBlocking {
        val store = store()

        val ok = store.write("vid") { out ->
            out.write(byteArrayOf(4, 5, 6))
            true
        }

        assertTrue(ok)
        assertTrue(store.isDownloaded("vid"))
        assertArrayEquals(byteArrayOf(4, 5, 6), Files.readAllBytes(store.fileFor("vid")))
        assertNoPartFiles(store)
    }

    @Test
    fun write_noComprometeSiElVolcadoFalla() = runBlocking {
        val store = store()

        val ok = store.write("vid") { out ->
            out.write(byteArrayOf(1))
            false
        }

        assertFalse(ok)
        assertFalse(store.isDownloaded("vid"))
        assertNoPartFiles(store)
    }

    @Test
    fun write_noComprometeConUnFicheroVacio() = runBlocking {
        val store = store()
        val ok = store.write("vid") { true }
        assertFalse(ok)
        assertFalse(store.isDownloaded("vid"))
    }

    @Test
    fun write_propagaExcepcionComoFalloYNoDejaTemporales() = runBlocking {
        val store = store()
        val ok = store.write("vid") { throw IllegalStateException("corte") }
        assertFalse(ok)
        assertFalse(store.isDownloaded("vid"))
        assertNoPartFiles(store)
    }

    @Test
    fun summaryCuentaPistasYBytes() = runBlocking {
        val store = store()
        store.write("a") { it.write(ByteArray(10)); true }
        store.write("b") { it.write(ByteArray(5)); true }
        // Un `.part` suelto no debe contar.
        Files.write(store.directory.resolve("c-123.part"), ByteArray(99))

        val summary = store.summary()
        assertEquals(2, summary.count)
        assertEquals(15L, summary.bytes)
    }

    @Test
    fun deleteBorraSoloElVideoIndicado() = runBlocking {
        val store = store()
        store.write("a") { it.write(byteArrayOf(1)); true }
        store.write("b") { it.write(byteArrayOf(2)); true }

        assertTrue(store.delete("a"))
        assertFalse(store.isDownloaded("a"))
        assertTrue(store.isDownloaded("b"))
        assertFalse(store.delete("a"))
    }

    @Test
    fun deleteAllVaciaElAlmacen() = runBlocking {
        val store = store()
        store.write("a") { it.write(byteArrayOf(1)); true }
        store.write("b") { it.write(byteArrayOf(2)); true }

        assertTrue(store.deleteAll())
        assertEquals(0, store.summary().count)
        assertTrue(store.deleteAll())
    }

    private fun assertNoPartFiles(store: DownloadedAudioStore) {
        val parts = Files.list(store.directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".part") }.count()
        }
        assertEquals(0L, parts)
    }
}
