package com.plyr.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests de la lectura del ZIP de exportación.
 *
 * Lo que se comprueba aquí es lo que protege de un archivo manipulado: que solo
 * se lee lo que el formato define, y que un ZIP con entradas enormes no se traga
 * la memoria de la app.
 */
class ImportArchiveTest {

    @Test
    fun read_returnsManifestAndCovers() {
        val manifest = ExportManifest.FILE_NAME.toByteArray()
        val cover = byteArrayOf(1, 2, 3, 4)

        val entries = read(
            ExportManifest.FILE_NAME to manifest,
            "covers/youtube_PL1.jpg" to cover
        )

        assertEquals(2, entries.size)
        assertArrayEquals(manifest, entries[ExportManifest.FILE_NAME])
        assertArrayEquals(cover, entries["covers/youtube_PL1.jpg"])
    }

    @Test
    fun read_ignoresEntriesOutsideTheFormat() {
        val manifest = ExportManifest.FILE_NAME.toByteArray()
        val entries = read(
            ExportManifest.FILE_NAME to manifest,
            "readme.txt" to "hola".toByteArray(),
            "payload.sh" to "#!/bin/sh".toByteArray(),
            "other/file.json" to "{}".toByteArray(),
            "covers/sub/deep.jpg" to byteArrayOf(9),
            "playlist.json" to "{}".toByteArray(),
            "covers" to byteArrayOf(9)
        )

        assertEquals(setOf(ExportManifest.FILE_NAME), entries.keys)
    }

    /** Defensa extra: el nombre nunca se usa como ruta, pero no hay que fiarse. */
    @Test
    fun read_ignoresCoverNamesWithPathTraversal() {
        val entries = read(
            ExportManifest.FILE_NAME to "[]".toByteArray(),
            "covers/../escape.jpg" to byteArrayOf(9),
            "covers/../../etc/passwd.jpg" to byteArrayOf(9)
        )

        assertEquals(setOf(ExportManifest.FILE_NAME), entries.keys)
    }

    @Test
    fun read_ignoresDirectoryEntries() {
        val entries = read(
            ExportManifest.FILE_NAME to "[]".toByteArray(),
            "covers/" to byteArrayOf(0)
        )

        assertEquals(setOf(ExportManifest.FILE_NAME), entries.keys)
    }

    @Test
    fun read_ignoresOverlongEntryNames() {
        val entries = read(
            ExportManifest.FILE_NAME to "[]".toByteArray(),
            "covers/${"a".repeat(300)}.jpg" to byteArrayOf(1)
        )

        assertEquals(setOf(ExportManifest.FILE_NAME), entries.keys)
    }

    @Test
    fun read_preservesBinaryContent() {
        val binary = ByteArray(256) { it.toByte() }
        val entries = read(
            ExportManifest.FILE_NAME to "[]".toByteArray(),
            "covers/bin.jpg" to binary
        )

        assertArrayEquals(binary, entries["covers/bin.jpg"])
    }

    @Test
    fun read_withEmptyZip_throws() {
        try {
            read()
            fail("Se esperaba IOException con un ZIP vacío")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains(ExportManifest.FILE_NAME))
        }
    }

    @Test
    fun read_withZipWithoutManifest_throws() {
        try {
            read("covers/youtube_PL1.jpg" to byteArrayOf(1))
            fail("Se esperaba IOException si falta el manifiesto")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains(ExportManifest.FILE_NAME))
        }
    }

    @Test
    fun read_withDataThatIsNotAZip_throws() {
        try {
            ImportArchive.read(ByteArrayInputStream("esto no es un zip".toByteArray()))
            fail("Se esperaba IOException con datos que no son un ZIP")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun read_withEntryOverTheLimit_throws() {
        val tooBig = ByteArray((ImportArchive.MAX_ENTRY_BYTES + 1024).toInt())
        try {
            read(ExportManifest.FILE_NAME to "[]".toByteArray(), "covers/big.jpg" to tooBig)
            fail("Se esperaba IOException al superar el límite por entrada")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("límite"))
        }
    }

    /** El ZIP bomba repartido: entradas válidas una a una que suman demasiado. */
    @Test
    fun read_withEntriesOverTheTotalLimit_throws() {
        val chunk = ByteArray(ImportArchive.MAX_ENTRY_BYTES.toInt())
        val chunks = (0..8).map { "covers/c$it.jpg" to chunk }
        try {
            read(ExportManifest.FILE_NAME to "[]".toByteArray(), *chunks.toTypedArray())
            fail("Se esperaba IOException al superar el límite total")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("límite"))
        }
    }

    @Test
    fun read_withEmptyManifestEntry_stillReadsIt() {
        // La validación del contenido es de ImportManifest, no de aquí
        val entries = read(ExportManifest.FILE_NAME to ByteArray(0))
        assertEquals(0, entries[ExportManifest.FILE_NAME]!!.size)
    }

    @Test
    fun read_closesTheInputStream() {
        var closed = false
        val stream = object : ByteArrayInputStream(
            zipOf(ExportManifest.FILE_NAME to "[]".toByteArray())
        ) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        ImportArchive.read(stream)

        assertTrue(closed)
    }

    @Test
    fun archiveLimits_areSane() {
        assertTrue(ImportArchive.MAX_ENTRY_BYTES <= ImportArchive.MAX_TOTAL_BYTES)
        assertFalse(ImportArchive.MAX_TOTAL_BYTES > 512L * 1024 * 1024)
    }

    // === HELPERS ===

    private fun read(vararg entries: Pair<String, ByteArray>): Map<String, ByteArray> =
        ImportArchive.read(ByteArrayInputStream(zipOf(*entries)))

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
