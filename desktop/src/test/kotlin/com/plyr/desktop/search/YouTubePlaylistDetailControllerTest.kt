package com.plyr.desktop.search

import com.plyr.database.PlaylistSource
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.model.AudioItem
import com.plyr.network.YouTubePlaylistInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [YouTubePlaylistDetailController] (M19): carga de pistas con la red
 * inyectada (sin tocar YouTube) y guardado/borrado como lista local sobre
 * SQLite en memoria.
 */
class YouTubePlaylistDetailControllerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined)
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun playlist(id: String = "PL1") = YouTubePlaylistInfo(
        playlistId = id,
        title = "Mix",
        uploader = "DJ",
        videoCount = 2,
        thumbnailUrl = null,
    )

    private fun videos() = listOf(
        AudioItem(title = "Uno", url = "", videoId = "vid00000001", channel = "DJ", duration = "3:00"),
        AudioItem(title = "Dos", url = "", videoId = "vid00000002", channel = "DJ", duration = "4:00"),
    )

    private fun controller(load: suspend (String) -> List<AudioItem> = { videos() }) =
        YouTubePlaylistDetailController(scope, repository, load)

    @Test
    fun open_cargaLasPistasConElIdLocal() {
        val controller = controller()
        controller.open(playlist())

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.tracks.size)
        assertEquals("Uno", state.tracks.first().name)
        assertEquals("youtube_PL1", state.tracks.first().playlistId)
        assertFalse(state.isSaved)
    }

    @Test
    fun open_sinVideosQuedaVacio() {
        val controller = controller { emptyList() }
        controller.open(playlist())

        assertTrue(controller.state.value.isEmpty)
    }

    @Test
    fun open_errorSePublicaYNoHayPistas() {
        val controller = controller { error("sin red") }
        controller.open(playlist())

        val state = controller.state.value
        assertNotNull(state.error)
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun toggleSave_guardaLaListaComoLocal() {
        val controller = controller()
        controller.open(playlist())
        controller.toggleSave()

        assertTrue(controller.state.value.isSaved)
        val saved = repository.playlistById("youtube_PL1")
        assertNotNull(saved)
        assertEquals(PlaylistSource.YOUTUBE, saved!!.source)
        assertEquals("PL1", saved.sourceId)
        assertEquals("Mix", saved.name)
        assertEquals(2, repository.tracksByPlaylist("youtube_PL1").size)
    }

    @Test
    fun toggleSave_deNuevoLaBorra() {
        val controller = controller()
        controller.open(playlist())
        controller.toggleSave()
        controller.toggleSave()

        assertFalse(controller.state.value.isSaved)
        assertNull(repository.playlistById("youtube_PL1"))
    }

    @Test
    fun open_reconoceUnaListaYaGuardada() {
        controller().apply {
            open(playlist())
            toggleSave()
        }

        val reopened = controller().apply { open(playlist()) }
        assertTrue(reopened.state.value.isSaved)
    }

    @Test
    fun close_limpiaElEstado() {
        val controller = controller()
        controller.open(playlist())
        controller.close()

        assertEquals(YouTubePlaylistDetailState(), controller.state.value)
    }
}
