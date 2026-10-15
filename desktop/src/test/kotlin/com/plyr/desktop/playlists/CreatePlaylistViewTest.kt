package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests de la UI de alta de lista (M22): el buscador de YouTube selecciona pistas
 * y al guardar nacen ya dentro de la lista (F21). La búsqueda se inyecta, así que
 * no toca la red.
 */
@OptIn(ExperimentalTestApi::class)
class CreatePlaylistViewTest {

    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository
    private lateinit var scope: CoroutineScope
    private lateinit var controller: PlaylistsController

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        DesktopConfig.language = Language.ENGLISH
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database, InMemoryTombstoneStore())
        scope = CoroutineScope(Dispatchers.Unconfined)
        controller = PlaylistsController(scope, repository)
        controller.showCreate()
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun track(id: String) = PlayerTrack(id = id, title = "Cancion $id", artist = "Artista", videoId = id)

    @Test
    fun guardaLaListaConLasPistasElegidas() = runComposeUiTest {
        setContent {
            PlyrTheme {
                Column {
                    CreatePlaylistView(controller = controller, searchTracks = { listOf(track("v1"), track("v2")) })
                }
            }
        }

        onNodeWithTag("create_playlist_name").performTextInput("Verano")
        onNodeWithTag("create_playlist_search").performTextInput("cancion")
        onNodeWithTag("create_playlist_search_button").performClick()

        onNodeWithTag("create_result_0").performClick()
        onNodeWithTag("create_result_1").performClick()

        onNodeWithTag("create_playlist_confirm").performClick()

        val created = repository.allPlaylists().single { it.name == "Verano" }
        assertEquals(listOf("v1", "v2"), repository.tracksByPlaylist(created.remoteId).map { it.youtubeVideoId })
    }

    @Test
    fun laSeleccionSePuedeQuitarAntesDeGuardar() = runComposeUiTest {
        setContent {
            PlyrTheme {
                Column {
                    CreatePlaylistView(controller = controller, searchTracks = { listOf(track("v1")) })
                }
            }
        }

        onNodeWithTag("create_playlist_search").performTextInput("cancion")
        onNodeWithTag("create_playlist_search_button").performClick()
        onNodeWithTag("create_result_0").performClick()
        onNodeWithTag("create_selected_0").performClick()

        onNodeWithTag("create_selected_0").assertDoesNotExist()
    }
}
