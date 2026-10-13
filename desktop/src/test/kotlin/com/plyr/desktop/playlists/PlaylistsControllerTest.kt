package com.plyr.desktop.playlists

import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val BASE_TIME = 1_700_000_000_000L

/**
 * Tests de [PlaylistsController] sobre SQLite en memoria.
 *
 * Cubren lo que faltaba en M8: el alta de listas (que era un no-op), la
 * persistencia del reordenado, la portada personalizada y el "añadir a lista".
 * El repositorio es sincrónico y el scope usa [Dispatchers.Unconfined], así que
 * las operaciones se aplican al terminar la llamada.
 */
class PlaylistsControllerTest {

    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository
    private lateinit var scope: CoroutineScope
    private lateinit var controller: PlaylistsController

    @Before
    fun setUp() {
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database, InMemoryTombstoneStore())
        scope = CoroutineScope(Dispatchers.Unconfined)
        controller = PlaylistsController(scope, repository)
    }

    @After
    fun tearDown() {
        // Cancela el observer del controlador antes de cerrar la base: si no,
        // su consulta en `Dispatchers.IO` revienta contra un driver cerrado y la
        // excepción se filtra como "uncaught" a un test posterior.
        scope.cancel()
        database.close()
    }

    private fun playlist(
        id: String,
        name: String = id,
        description: String? = null,
        trackCount: Int = 0,
    ) = PlaylistRecord(
        remoteId = id,
        name = name,
        description = description,
        trackCount = trackCount,
        imageUrl = null,
        lastSyncTime = BASE_TIME,
        source = PlaylistSource.LOCAL,
        sourceId = null,
    )

    private fun track(id: String, position: Int = 0, videoId: String? = "vid_$id") = TrackRecord(
        id = id,
        playlistId = "p1",
        remoteTrackId = "r_$id",
        name = "Song $id",
        artists = "Artist",
        youtubeVideoId = videoId,
        audioUrl = null,
        position = position,
        lastSyncTime = BASE_TIME,
    )

    /** Espera a que la lista [id] aparezca en el `StateFlow` (lo llena el observer). */
    private fun awaitPlaylistVisible(id: String) = runBlocking {
        withTimeout(5_000) { controller.state.first { state -> state.playlists.any { it.remoteId == id } } }
    }

    @Test
    fun createPlaylist_guardaLaListaYLaSelecciona() {
        val id = controller.createPlaylist("Rock", "clásicos")

        assertNotNull(id)
        val stored = repository.playlistById(id!!)
        assertNotNull(stored)
        assertEquals("Rock", stored!!.name)
        assertEquals("clásicos", stored.description)
        assertEquals(PlaylistSource.LOCAL, stored.source)
        assertEquals(id, controller.state.value.selectedPlaylistId)
        assertFalse(controller.state.value.showCreate)
        assertEquals("Rock", controller.state.value.newTitle)
    }

    @Test
    fun createPlaylist_recortaLosEspaciosYOmiteLaDescripcionVacia() {
        val id = controller.createPlaylist("  Verano  ", "   ")

        val stored = repository.playlistById(id!!)!!
        assertEquals("Verano", stored.name)
        assertNull(stored.description)
    }

    @Test
    fun createPlaylist_conNombreVacioNoCreaNada() {
        val before = repository.allPlaylists().size

        val id = controller.createPlaylist("   ", null)

        assertNull(id)
        assertEquals(before, repository.allPlaylists().size)
    }

    @Test
    fun reorderTracks_persisteElNuevoOrden() {
        repository.savePlaylist(
            playlist("p1", trackCount = 3),
            listOf(track("t1", 0), track("t2", 1), track("t3", 2)),
        )
        controller.selectPlaylist("p1")
        assertEquals(listOf("t1", "t2", "t3"), controller.state.value.tracks.map { it.id })

        controller.reorderTracks(0, 2)

        assertEquals(listOf("t2", "t3", "t1"), controller.state.value.tracks.map { it.id })
        assertEquals(listOf("r_t2", "r_t3", "r_t1"), repository.tracksByPlaylist("p1").map { it.remoteTrackId })
    }

    @Test
    fun removeTrack_laQuitaYRefrescaElEstado() {
        repository.savePlaylist(playlist("p1", trackCount = 2), listOf(track("t1", 0), track("t2", 1)))
        controller.selectPlaylist("p1")

        controller.removeTrack("r_t1")

        assertEquals(listOf("t2"), controller.state.value.tracks.map { it.id })
        assertEquals(listOf("t2"), repository.tracksByPlaylist("p1").map { it.id })
    }

    @Test
    fun addTrackToPlaylist_anadeAlFinalYAceptaRepetidasSinDuplicar() {
        repository.savePlaylist(playlist("p1"), emptyList())
        controller.selectPlaylist("p1")

        controller.addTrackToPlaylist("p1", track("t9"))
        controller.addTrackToPlaylist("p1", track("t9"))

        assertEquals(listOf("r_t9"), repository.tracksByPlaylist("p1").map { it.remoteTrackId })
        assertEquals(listOf("r_t9"), controller.state.value.tracks.map { it.remoteTrackId })
    }

    @Test
    fun updateCover_guardaLaUrlEnLaListaSeleccionada() {
        repository.savePlaylist(playlist("p1"), emptyList())
        controller.selectPlaylist("p1")

        assertTrue(controller.updateCover("file:///tmp/cover.jpg"))

        assertEquals("file:///tmp/cover.jpg", repository.playlistById("p1")!!.imageUrl)
    }

    @Test
    fun saveEdits_actualizaTituloYDescripcion() {
        repository.savePlaylist(playlist("p1", "Vieja"), listOf(track("t1")))
        controller.selectPlaylist("p1")
        awaitPlaylistVisible("p1")
        controller.enterEditMode()
        assertTrue(controller.state.value.isEditing)

        controller.updateTitle("Nueva")
        controller.updateDescription("desc")
        assertTrue(controller.state.value.hasUnsavedChanges)
        controller.saveEdits()

        val stored = repository.playlistById("p1")!!
        assertEquals("Nueva", stored.name)
        assertEquals("desc", stored.description)
        assertFalse(controller.state.value.isEditing)
        assertFalse(controller.state.value.hasUnsavedChanges)
    }

    @Test
    fun cancelEdit_conCambiosPideConfirmacion() {
        repository.savePlaylist(playlist("p1", "Vieja"), emptyList())
        controller.selectPlaylist("p1")
        awaitPlaylistVisible("p1")
        controller.enterEditMode()
        controller.updateTitle("Nueva")

        controller.cancelEdit()

        assertTrue(controller.state.value.showExitEditDialog)
        controller.confirmExitEdit()
        assertFalse(controller.state.value.isEditing)
        assertEquals("Vieja", repository.playlistById("p1")!!.name)
    }

    @Test
    fun deletePlaylist_borraYLlamaAlCallback() {
        repository.savePlaylist(playlist("p1"), listOf(track("t1")))
        controller.selectPlaylist("p1")
        var deleted = false

        controller.deletePlaylist { deleted = true }

        assertTrue(deleted)
        assertNull(repository.playlistById("p1"))
        assertNull(controller.state.value.selectedPlaylistId)
    }
}
