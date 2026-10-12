package com.plyr.desktop.database

import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests de [DesktopDatabase]: creación del esquema y, sobre todo, que lo guardado
 * **sobrevive a reinicios** (cerrar y reabrir el fichero SQLite), que es el DoD
 * de M6.
 */
class DesktopDatabaseTest {

    private fun newDatabasePath(): Path =
        Files.createTempDirectory("plyr-m6").resolve("plyr.db")

    private fun playlist(id: String, name: String = id) = PlaylistRecord(
        remoteId = id,
        name = name,
        description = null,
        trackCount = 0,
        imageUrl = null,
        lastSyncTime = 1_000L,
        source = PlaylistSource.LOCAL,
        sourceId = null,
    )

    private fun track(id: String, playlistId: String, position: Int = 0) = TrackRecord(
        id = id,
        playlistId = playlistId,
        remoteTrackId = "r_$id",
        name = "Song $id",
        artists = "Artist",
        youtubeVideoId = "vid_$id",
        audioUrl = null,
        position = position,
        lastSyncTime = 1_000L,
    )

    @Test
    fun listasPistasYFavoritos_sobrevivenAReabrirLaBaseDeDatos() {
        val path = newDatabasePath()

        DesktopDatabase.open(path).use { database ->
            val repository = PlaylistRepository(database)
            repository.savePlaylist(playlist("p1", "Rock"), listOf(track("t1", "p1", 0)))
            repository.ensureLikedSongsPlaylist()
            repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA")
        }

        DesktopDatabase.open(path).use { database ->
            val repository = PlaylistRepository(database)
            assertEquals("Rock", repository.playlistById("p1")!!.name)
            assertEquals(listOf("t1"), repository.tracksByPlaylist("p1").map { it.id })
            assertTrue(repository.isTrackLiked("vidA"))
        }
    }

    @Test
    fun cambios_sobrevivenAlSegundoReinicio() {
        val path = newDatabasePath()

        DesktopDatabase.open(path).use { database ->
            PlaylistRepository(database).savePlaylist(playlist("p1", "Old"), emptyList())
        }
        DesktopDatabase.open(path).use { database ->
            val repository = PlaylistRepository(database)
            repository.updatePlaylistDetails("p1", "New", null)
            repository.deletePlaylist("p1")
            repository.savePlaylist(playlist("p2", "Kept"), emptyList())
        }

        DesktopDatabase.open(path).use { database ->
            val repository = PlaylistRepository(database)
            assertNull(repository.playlistById("p1"))
            assertEquals("Kept", repository.playlistById("p2")!!.name)
        }
    }

    @Test
    fun abrirUnaBaseNueva_creaElEsquemaVacio() {
        DesktopDatabase.open(newDatabasePath()).use { database ->
            val repository = PlaylistRepository(database)

            assertTrue(repository.allPlaylists().isEmpty())
            assertNull(repository.playlistById("nada"))
        }
    }

    @Test
    fun open_creaLosDirectoriosIntermedios() {
        val path = Files.createTempDirectory("plyr-m6").resolve("sub").resolve("dir").resolve("plyr.db")

        DesktopDatabase.open(path).use { database ->
            PlaylistRepository(database).savePlaylist(playlist("p1"), emptyList())
        }

        assertTrue(Files.exists(path))
    }

    @Test
    fun elHistorialDeBusquedas_tambienSobrevive() {
        val path = newDatabasePath()

        DesktopDatabase.open(path).use { database ->
            SearchHistoryRepository(database).addSearch("rock", "youtube", timestamp = 10)
        }

        DesktopDatabase.open(path).use { database ->
            assertEquals(listOf("rock"), SearchHistoryRepository(database).recentSearches().map { it.query })
        }
    }
}
