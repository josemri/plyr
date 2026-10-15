package com.plyr.desktop.sync

import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.database.TombstoneStore
import com.plyr.desktop.utils.CoverCache
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import org.junit.Rule
import org.junit.Test

/**
 * Tests de exportación, importación y copia automática de escritorio (M10).
 *
 * Montan repositorios SQLite en memoria y carpetas temporales, así que no tocan
 * ni la base de datos ni las preferencias del usuario. Cubren el ciclo completo
 * (exportar → importar), la fusión desde la carpeta y la política de tombstones.
 */
class DataSyncTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val databases = mutableListOf<DesktopDatabase>()

    private fun repo(tombstones: TombstoneStore = InMemoryTombstoneStore()): PlaylistRepository {
        val database = DesktopDatabase.inMemory()
        databases += database
        return PlaylistRepository(database, tombstones)
    }

    private fun exporter(repository: PlaylistRepository, tombstones: TombstoneStore) =
        DataExporter(repository, tombstones, coverCache(directory()))

    private fun importer(repository: PlaylistRepository, tombstones: TombstoneStore) =
        DataImporter(repository, tombstones, coversDir())

    private fun directory(): Path = temp.newFolder().toPath()

    private fun coversDir(): Path = temp.newFolder().toPath()

    /** Caché de portadas sin red ni disco compartido: nunca descarga. */
    private fun coverCache(dir: Path) = CoverCache(dir, download = { null })

    private fun configFor(folder: Path): BackupConfig =
        BackupConfig(MapSettings()).also { it.folder = folder.toString() }

    private fun playlist(
        id: String,
        name: String = id,
        imageUrl: String? = null,
        source: PlaylistSource = PlaylistSource.LOCAL,
    ) = PlaylistRecord(
        remoteId = id,
        name = name,
        description = null,
        trackCount = 0,
        imageUrl = imageUrl,
        lastSyncTime = TIME,
        source = source,
        sourceId = null,
    )

    private fun track(id: String, playlistId: String, videoId: String? = "vid_$id") = TrackRecord(
        id = "${playlistId}_$id",
        playlistId = playlistId,
        remoteTrackId = id,
        name = "Song $id",
        artists = "A, B",
        youtubeVideoId = videoId,
        audioUrl = null,
        position = 0,
        lastSyncTime = TIME,
    )

    // === EXPORTAR / IMPORTAR ===

    @Test
    fun exportarLuegoImportarRestauraListasYPistas() = runBlocking {
        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply {
            savePlaylist(playlist("youtube_PL1", "Rock"), listOf(track("t1", "youtube_PL1")))
            ensureLikedSongsPlaylist()
        }
        val zip = directory().resolve("export.zip")

        val exported = exporter(repoA, tombstonesA).exportTo(zip).getOrThrow()
        assertEquals(2, exported.playlistCount)
        assertTrue(Files.isRegularFile(zip))

        val tombstonesB = InMemoryTombstoneStore()
        val repoB = repo(tombstonesB)
        val summary = importer(repoB, tombstonesB).importFrom(zip).getOrThrow()

        assertEquals(1, summary.importedPlaylists)
        assertEquals(1, summary.importedTracks)
        val restored = repoB.playlistById("youtube_PL1")
        assertNotNull(restored)
        assertEquals("Rock", restored!!.name)
        assertEquals(listOf("t1"), repoB.tracksByPlaylist("youtube_PL1").map { it.remoteTrackId })
    }

    @Test
    fun importarRestauraLaPortadaLocal() = runBlocking {
        val cover = temp.newFile("cover.png").toPath()
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", cover.toFile())

        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply {
            savePlaylist(playlist("p1", imageUrl = cover.toUri().toString()), emptyList())
        }
        val zip = directory().resolve("export.zip")
        exporter(repoA, tombstonesA).exportTo(zip).getOrThrow()

        val tombstonesB = InMemoryTombstoneStore()
        val repoB = repo(tombstonesB)
        val summary = importer(repoB, tombstonesB).importFrom(zip).getOrThrow()

        assertEquals(1, summary.restoredCovers)
        val imageUrl = repoB.playlistById("p1")!!.imageUrl
        assertNotNull(imageUrl)
        assertTrue(imageUrl!!.startsWith("file://"))
        assertTrue(Files.isRegularFile(Path.of(java.net.URI(imageUrl))))
    }

    @Test
    fun importarFusionaFavoritosSinDuplicar() = runBlocking {
        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply {
            ensureLikedSongsPlaylist()
            mergeLikedSongsTracks(listOf(track("fav1", LikedSongs.ID, videoId = "yt-fav")))
        }
        val zip = directory().resolve("export.zip")
        exporter(repoA, tombstonesA).exportTo(zip).getOrThrow()

        val tombstonesB = InMemoryTombstoneStore()
        val repoB = repo(tombstonesB).apply {
            ensureLikedSongsPlaylist()
            // Ya tenía el mismo favorito: no debe duplicarse.
            mergeLikedSongsTracks(listOf(track("fav1", LikedSongs.ID, videoId = "yt-fav")))
        }
        val summary = importer(repoB, tombstonesB).importFrom(zip).getOrThrow()

        assertEquals(0, summary.mergedLikedTracks)
        assertEquals(1, repoB.tracksByPlaylist(LikedSongs.ID).size)
    }

    // === TOMBSTONES ===

    @Test
    fun unTombEvitaQueSeResuciteUnaListaBorrada() = runBlocking {
        // La copia contiene p1, pero el usuario la borró (tomb local).
        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply { savePlaylist(playlist("p1"), emptyList()) }
        val zip = directory().resolve("export.zip")
        exporter(repoA, tombstonesA).exportTo(zip).getOrThrow()

        val tombstonesB = InMemoryTombstoneStore().apply { addDeletedPlaylistId("p1") }
        val repoB = repo(tombstonesB)
        importer(repoB, tombstonesB).importFrom(zip).getOrThrow()

        assertEquals(null, repoB.playlistById("p1"))
    }

    @Test
    fun unFavoritoQuitadoNoVuelveDeLaCopia() = runBlocking {
        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply {
            ensureLikedSongsPlaylist()
            mergeLikedSongsTracks(listOf(track("fav1", LikedSongs.ID, videoId = "yt-fav")))
        }
        val zip = directory().resolve("export.zip")
        exporter(repoA, tombstonesA).exportTo(zip).getOrThrow()

        val tombstonesB = InMemoryTombstoneStore().apply {
            // La misma clave que usa la fusión (youtubeVideoId).
            addRemovedLikedTrackKey("yt-fav")
        }
        val repoB = repo(tombstonesB)
        val summary = importer(repoB, tombstonesB).importFrom(zip).getOrThrow()

        assertEquals(0, summary.mergedLikedTracks)
        assertTrue(repoB.tracksByPlaylist(LikedSongs.ID).isEmpty())
    }

    // === COPIA AUTOMÁTICA ===

    @Test
    fun elVolcadoEscribeElArchivoYNoReescribeSiNoCambia() = runBlocking {
        val folder = directory()
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply { savePlaylist(playlist("p1"), emptyList()) }
        val config = BackupConfig(MapSettings())
        config.folder = folder.toString()
        val sync = DataSync(exporter(repo, tombstones), importer(repo, tombstones), config)

        val first = sync.flush(force = false)
        assertTrue(first is SyncResult.Written)
        assertTrue(Files.isRegularFile(BackupFolder.backupFile(folder)))

        val second = sync.flush(force = false)
        assertEquals(SyncResult.UpToDate, second)

        sync.shutdown()
    }

    @Test
    fun sinCarpetaNoHaceNada() = runBlocking {
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply { savePlaylist(playlist("p1"), emptyList()) }
        val sync = DataSync(
            exporter(repo, tombstones),
            importer(repo, tombstones),
            BackupConfig(MapSettings()),
        )

        assertEquals(SyncResult.NotConfigured, sync.flush(force = true))
        sync.shutdown()
    }

    @Test
    fun elVolcadoFusionaLaCopiaDeLaCarpetaEnUnaInstalacionNueva() = runBlocking {
        val folder = directory()
        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply {
            savePlaylist(playlist("p1", "Rock"), listOf(track("t1", "p1")))
        }
        val configA = configFor(folder)
        val syncA = DataSync(exporter(repoA, tombstonesA), importer(repoA, tombstonesA), configA)
        syncA.flush(force = true)
        syncA.shutdown()

        // Instalación nueva: solo liked_songs; la carpeta tiene p1.
        val tombstonesB = InMemoryTombstoneStore()
        val repoB = repo(tombstonesB).apply { ensureLikedSongsPlaylist() }
        val configB = configFor(folder)
        val syncB = DataSync(exporter(repoB, tombstonesB), importer(repoB, tombstonesB), configB)

        val result = syncB.flush(force = true)
        assertTrue(result is SyncResult.Written)
        assertTrue((result as SyncResult.Written).merged != null)
        assertEquals("Rock", repoB.playlistById("p1")?.name)
        syncB.shutdown()
    }

    @Test
    fun unArchivoIlegibleNoSePisa() = runBlocking {
        val folder = directory()
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply { savePlaylist(playlist("p1"), emptyList()) }
        val config = configFor(folder)

        // Un archivo con el nombre de la copia pero que no es un ZIP válido.
        val target = BackupFolder.backupFile(folder)
        Files.write(target, "esto no es un zip".toByteArray())

        val sync = DataSync(exporter(repo, tombstones), importer(repo, tombstones), config)
        val result = sync.flush(force = true)

        assertTrue(result is SyncResult.ArchiveUnreadable)
        assertEquals("esto no es un zip", Files.readString(target))
        sync.shutdown()
    }

    @Test
    fun laCopiaAutomaticaSeDisparaTrasMarcarCambios() = runBlocking {
        val folder = directory()
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply { savePlaylist(playlist("p1"), emptyList()) }
        val config = configFor(folder)
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )
        val sync = DataSync(
            exporter(repo, tombstones),
            importer(repo, tombstones),
            config,
            scope = scope,
            debounceMs = 0,
        )

        sync.markDirty()

        val target = BackupFolder.backupFile(folder)
        val deadline = System.currentTimeMillis() + 5_000
        while (!Files.isRegularFile(target) && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue("La copia automática debería haber escrito el ZIP", Files.isRegularFile(target))

        sync.shutdown()
        scope.cancel()
    }

    // === FLUSH AL CERRAR (M26) ===

    @Test
    fun cerrarVuelcaLaCopiaPendiente() {
        val folder = directory()
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply { savePlaylist(playlist("p1"), emptyList()) }
        val config = configFor(folder)
        // Debounce enorme a propósito: sin flushOnStop la copia no se escribiría.
        val sync = DataSync(
            exporter(repo, tombstones),
            importer(repo, tombstones),
            config,
            debounceMs = 60_000,
        )

        sync.markDirty()
        assertTrue("El cierre debería volcar la copia pendiente", sync.flushOnStop())

        assertTrue(Files.isRegularFile(BackupFolder.backupFile(folder)))
    }

    @Test
    fun cerrarSinCambiosPendientesNoEscribe() {
        val folder = directory()
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply { savePlaylist(playlist("p1"), emptyList()) }
        val config = configFor(folder)
        val sync = DataSync(
            exporter(repo, tombstones),
            importer(repo, tombstones),
            config,
            debounceMs = 60_000,
        )

        assertFalse("No hay nada pendiente que volcar", sync.flushOnStop())
        assertFalse(Files.isRegularFile(BackupFolder.backupFile(folder)))
    }

    @Test
    fun importFromBackupFolderLeeLaCopiaDeLaCarpeta() = runBlocking {
        val folder = directory()
        val tombstonesA = InMemoryTombstoneStore()
        val repoA = repo(tombstonesA).apply { savePlaylist(playlist("p1"), emptyList()) }
        val configA = configFor(folder)
        val syncA = DataSync(exporter(repoA, tombstonesA), importer(repoA, tombstonesA), configA)
        syncA.flush(force = true)
        syncA.shutdown()

        val tombstonesB = InMemoryTombstoneStore()
        val repoB = repo(tombstonesB)
        val configB = configFor(folder)
        val syncB = DataSync(exporter(repoB, tombstonesB), importer(repoB, tombstonesB), configB)

        val summary = syncB.importFromBackupFolder().getOrThrow()
        assertEquals(1, summary.importedPlaylists)
        assertNotNull(repoB.playlistById("p1"))
        syncB.shutdown()
    }

    @Test
    fun importFromBackupFolderSinCarpetaFalla() = runBlocking {
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones)
        val sync = DataSync(
            exporter(repo, tombstones),
            importer(repo, tombstones),
            BackupConfig(MapSettings()),
        )

        assertTrue(sync.importFromBackupFolder().isFailure)
        sync.shutdown()
    }

    @Test
    fun editarLaCopiaDeOtroDespuesCambiaLaHuella() = runBlocking {
        // Verifica que borrar una lista (tomb) fuerza reescribir aunque el resto
        // no cambie: si la huella no incluyera los tombs, el borrado no viajaría.
        val folder = directory()
        val tombstones = InMemoryTombstoneStore()
        val repo = repo(tombstones).apply {
            savePlaylist(playlist("p1"), emptyList())
            savePlaylist(playlist("p2"), emptyList())
        }
        val config = configFor(folder)
        val sync = DataSync(exporter(repo, tombstones), importer(repo, tombstones), config)

        sync.flush(force = true)
        repo.deletePlaylist("p2")
        val second = sync.flush(force = false)

        assertTrue(second is SyncResult.Written)
        assertFalse(repo.allPlaylists().any { it.remoteId == "p2" })
        sync.shutdown()
    }

    @org.junit.After
    fun tearDown() {
        databases.forEach { runCatching { it.close() } }
    }

    private companion object {
        const val TIME = 1_700_000_000_000L
    }
}
