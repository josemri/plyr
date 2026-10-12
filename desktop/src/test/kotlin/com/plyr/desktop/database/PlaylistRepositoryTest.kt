package com.plyr.desktop.database

import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests del repositorio de listas/pistas/favoritos de escritorio.
 *
 * Cubren la paridad con los repositorios de Android (`PlaylistLocalRepository`,
 * `LikedSongsRepository`, `PlaylistTracksRepository`) sobre SQLite en memoria:
 * alta/edición/borrado de listas, pistas, favoritos y tombs.
 */
class PlaylistRepositoryTest {

    private lateinit var database: DesktopDatabase
    private lateinit var tombstones: InMemoryTombstoneStore
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        database = DesktopDatabase.inMemory()
        tombstones = InMemoryTombstoneStore()
        repository = PlaylistRepository(database, tombstones)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun playlist(
        id: String,
        name: String = id,
        trackCount: Int = 0,
    ) = PlaylistRecord(
        remoteId = id,
        name = name,
        description = null,
        trackCount = trackCount,
        imageUrl = null,
        lastSyncTime = BASE_TIME,
        source = PlaylistSource.LOCAL,
        sourceId = null,
    )

    private fun track(
        id: String,
        videoId: String? = "vid_$id",
        position: Int = 0,
        playlistId: String = "p",
    ) = TrackRecord(
        id = id,
        playlistId = playlistId,
        remoteTrackId = "r_$id",
        name = "Song $id",
        artists = "Artist",
        youtubeVideoId = videoId,
        audioUrl = null,
        position = position,
        lastSyncTime = BASE_TIME,
    )

    // === LISTAS ===

    @Test
    fun savePlaylist_guardaListaYPistasYReajustaElConteo() {
        repository.savePlaylist(
            playlist("p1", "Rock"),
            listOf(track("t1", position = 0), track("t2", position = 1)),
        )

        val stored = repository.playlistById("p1")
        assertNotNull(stored)
        assertEquals("Rock", stored!!.name)
        assertEquals(2, stored.trackCount)
        assertEquals(listOf("t1", "t2"), repository.tracksByPlaylist("p1").map { it.id })
    }

    @Test
    fun savePlaylist_reemplazaLasPistasAnteriores() {
        repository.savePlaylist(playlist("p1"), listOf(track("t1"), track("t2")))
        repository.savePlaylist(playlist("p1"), listOf(track("t3")))

        assertEquals(listOf("t3"), repository.tracksByPlaylist("p1").map { it.id })
        assertEquals(1, repository.playlistById("p1")!!.trackCount)
    }

    @Test
    fun savePlaylist_levantaElTombDeListaBorrada() {
        repository.deletePlaylist("p1")
        assertTrue("p1" in tombstones.deletedPlaylistIds())

        repository.savePlaylist(playlist("p1"), emptyList())

        assertFalse("p1" in tombstones.deletedPlaylistIds())
    }

    @Test
    fun deletePlaylist_borraPistasYDejaTomb() {
        repository.savePlaylist(playlist("p1"), listOf(track("t1")))

        repository.deletePlaylist("p1")

        assertNull(repository.playlistById("p1"))
        assertTrue(repository.tracksByPlaylist("p1").isEmpty())
        assertTrue("p1" in tombstones.deletedPlaylistIds())
    }

    @Test
    fun deletePlaylist_deLiked_noDejaTomb() {
        repository.ensureLikedSongsPlaylist()

        repository.deletePlaylist(LikedSongs.ID)

        assertFalse(LikedSongs.ID in tombstones.deletedPlaylistIds())
    }

    @Test
    fun updatePlaylistDetails_cambiaNombreYDescripcion() {
        repository.savePlaylist(playlist("p1", "Old"), emptyList())

        assertTrue(repository.updatePlaylistDetails("p1", "New", "desc"))

        val updated = repository.playlistById("p1")!!
        assertEquals("New", updated.name)
        assertEquals("desc", updated.description)
    }

    @Test
    fun updatePlaylistDetails_deListaAusente_devuelveFalse() {
        assertFalse(repository.updatePlaylistDetails("ghost", "New", null))
    }

    @Test
    fun updatePlaylistImage_guardaLaPortada() {
        repository.savePlaylist(playlist("p1"), emptyList())

        assertTrue(repository.updatePlaylistImage("p1", "/covers/p1.jpg"))

        assertEquals("/covers/p1.jpg", repository.playlistById("p1")!!.imageUrl)
    }

    // === VISIBLES ===

    @Test
    fun visiblePlaylists_ocultaAlbumesYLikedVacia() {
        repository.savePlaylist(playlist("album_1", "Album"), emptyList())
        repository.savePlaylist(playlist("rock", "Rock"), emptyList())
        repository.ensureLikedSongsPlaylist()

        assertEquals(listOf("rock"), repository.visiblePlaylists().map { it.remoteId })
    }

    @Test
    fun visiblePlaylists_muestraLikedPrimeroCuandoTieneFavoritos() {
        repository.savePlaylist(playlist("rock", "Rock"), emptyList())
        repository.savePlaylist(playlist("alfa", "Alfa"), emptyList())
        repository.ensureLikedSongsPlaylist()

        repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA")

        assertEquals(
            listOf(LikedSongs.ID, "alfa", "rock"),
            repository.visiblePlaylists().map { it.remoteId },
        )
    }

    // === PISTAS ===

    @Test
    fun addTrack_anadeAlFinalYCuenta() {
        repository.savePlaylist(playlist("p"), emptyList())

        assertTrue(repository.addTrack("p", track("t1", playlistId = "p")))
        assertTrue(repository.addTrack("p", track("t2", playlistId = "p")))

        val tracks = repository.tracksByPlaylist("p")
        assertEquals(listOf("vid_t1", "vid_t2"), tracks.map { it.youtubeVideoId })
        assertEquals(listOf(0, 1), tracks.map { it.position })
        assertEquals(2, repository.playlistById("p")!!.trackCount)
    }

    @Test
    fun addTrack_noDuplicaLaMismaCancion() {
        repository.savePlaylist(playlist("p"), emptyList())

        repository.addTrack("p", track("t1", videoId = "vidA", playlistId = "p"))
        repository.addTrack("p", track("t2", videoId = "vidA", playlistId = "p"))

        assertEquals(1, repository.tracksByPlaylist("p").size)
    }

    @Test
    fun removeTrack_renumeraYCuenta() {
        repository.savePlaylist(
            playlist("p"),
            listOf(track("t1", position = 0), track("t2", position = 1), track("t3", position = 2)),
        )

        assertTrue(repository.removeTrack("p", "r_t2"))

        val tracks = repository.tracksByPlaylist("p")
        assertEquals(listOf("t1", "t3"), tracks.map { it.id })
        assertEquals(listOf(0, 1), tracks.map { it.position })
        assertEquals(2, repository.playlistById("p")!!.trackCount)
    }

    @Test
    fun removeTrack_deLoQueNoEsta_devuelveFalse() {
        repository.savePlaylist(playlist("p"), listOf(track("t1")))

        assertFalse(repository.removeTrack("p", "r_ghost"))
    }

    @Test
    fun reorderTracks_cambiaElOrden() {
        repository.savePlaylist(
            playlist("p"),
            listOf(track("t1", position = 0), track("t2", position = 1), track("t3", position = 2)),
        )

        assertTrue(repository.reorderTracks("p", listOf("r_t3", "r_t1", "r_t2")))

        val tracks = repository.tracksByPlaylist("p")
        assertEquals(listOf("t3", "t1", "t2"), tracks.map { it.id })
        assertEquals(listOf(0, 1, 2), tracks.map { it.position })
    }

    @Test
    fun updateTrackYoutubeId_guardaElIdResuelto() {
        repository.savePlaylist(playlist("p"), listOf(track("t1", videoId = null)))

        repository.updateTrackYoutubeId("t1", "vidNew")

        assertEquals("vidNew", repository.trackById("t1")!!.youtubeVideoId)
    }

    // === FAVORITOS ===

    @Test
    fun toggleLike_anadeYQuitaConTomb() {
        repository.ensureLikedSongsPlaylist()

        assertTrue(repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA"))
        assertTrue(repository.isTrackLiked("vidA"))
        assertEquals(1, repository.playlistById(LikedSongs.ID)!!.trackCount)

        assertFalse(repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA"))
        assertFalse(repository.isTrackLiked("vidA"))
        assertTrue(repository.tracksByPlaylist(LikedSongs.ID).isEmpty())
        assertTrue("vidA" in tombstones.removedLikedTrackKeys())
    }

    @Test
    fun toggleLike_volver_aMarcar_levantaElTomb() {
        repository.ensureLikedSongsPlaylist()

        repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA")
        repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA")
        repository.toggleLikeTrack("vidA", "Song A", "Artist", "rA")

        assertTrue(repository.isTrackLiked("vidA"))
        assertFalse("vidA" in tombstones.removedLikedTrackKeys())
    }

    @Test
    fun isTrackLikedByKey_sinVideoId_caeANombreYArtistas() {
        repository.ensureLikedSongsPlaylist()
        repository.toggleLikeTrack("", "Song A", "Artist", "rA")

        assertTrue(repository.isTrackLikedByKey("Song A", "Artist", "rOther", null))
        assertFalse(repository.isTrackLikedByKey("Song B", "Artist", "rOther", null))
    }

    @Test
    fun mergeLikedSongsTracks_noDuplicaYRespetaTombs() {
        repository.ensureLikedSongsPlaylist()

        val first = listOf(track("t1", videoId = "v1"), track("t2", videoId = "v2"))
        assertEquals(2, repository.mergeLikedSongsTracks(first))
        assertEquals(0, repository.mergeLikedSongsTracks(first))

        val second = listOf(
            track("t2", videoId = "v2"),
            track("t3", videoId = "v3"),
            track("t4", videoId = "v4"),
            track("t5", videoId = "v5"),
        )
        assertEquals(2, repository.mergeLikedSongsTracks(second, removedKeys = setOf("v4")))

        assertEquals(
            listOf("v1", "v2", "v3", "v5"),
            repository.tracksByPlaylist(LikedSongs.ID).map { it.youtubeVideoId },
        )
    }

    @Test
    fun mergeLikedSongsTracks_vacioNoTocaNada() {
        repository.ensureLikedSongsPlaylist()

        assertEquals(0, repository.mergeLikedSongsTracks(emptyList()))
        assertTrue(repository.tracksByPlaylist(LikedSongs.ID).isEmpty())
    }

    @Test
    fun ensureLikedSongsPlaylist_esIdempotente() {
        repository.ensureLikedSongsPlaylist()
        repository.ensureLikedSongsPlaylist()

        assertEquals(1, repository.allPlaylists().count { it.remoteId == LikedSongs.ID })
    }

    private companion object {
        const val BASE_TIME = 1_000L
    }
}
