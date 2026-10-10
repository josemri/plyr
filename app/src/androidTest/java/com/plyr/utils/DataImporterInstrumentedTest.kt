package com.plyr.utils

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.plyr.database.PlaylistLocalRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tests instrumentados de la importación end-to-end ([DataImporter.importFrom]):
 * ZIP real abierto vía `Uri` y persistido en Room. La lógica de parsing y de
 * política ya está cubierta en JVM (`ImportManifestTest`), aquí se prueba la
 * fontanería que sí necesita Android.
 */
@RunWith(AndroidJUnit4::class)
class DataImporterInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun writeZip(file: File, manifestJson: String) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry(ExportManifest.FILE_NAME))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun manifestWith(playlistId: String): String = ExportManifest.build(
        appVersion = "instrumented-test",
        exportedAt = 0L,
        playlists = listOf(
            ExportPlaylist(
                id = playlistId,
                name = "Instrumented",
                description = null,
                coverEntry = null,
                tracks = listOf(
                    ExportTrack(
                        position = 0,
                        name = "Song",
                        artists = listOf("Artist"),
                        remoteTrackId = "remote-1",
                        youtubeVideoId = "dQw4w9WgXcQ"
                    )
                )
            )
        )
    )

    @Test
    fun importsPlaylistFromZipUri() {
        val id = "test_import_${System.currentTimeMillis()}"
        val file = File(context.cacheDir, "plyr-import-test.zip")
        writeZip(file, manifestWith(id))

        try {
            val result = runBlocking { DataImporter.importFrom(context, Uri.fromFile(file)) }

            assertTrue(result.isSuccess)
            val summary = result.getOrThrow()
            assertEquals(1, summary.importedPlaylists)
            assertEquals(1, summary.importedTracks)

            val stored = runBlocking { PlaylistLocalRepository(context).getAllPlaylists() }
            assertNotNull(stored.firstOrNull { it.remoteId == id })
        } finally {
            runBlocking { PlaylistLocalRepository(context).deletePlaylist(id) }
            file.delete()
        }
    }

    @Test
    fun invalidFileFailsCleanly() {
        val file = File(context.cacheDir, "plyr-invalid.zip")
        file.writeText("no es un zip")
        try {
            val result = runBlocking { DataImporter.importFrom(context, Uri.fromFile(file)) }
            assertTrue(result.isFailure)
        } finally {
            file.delete()
        }
    }
}
