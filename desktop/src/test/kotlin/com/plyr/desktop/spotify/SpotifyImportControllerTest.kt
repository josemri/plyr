package com.plyr.desktop.spotify

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.i18n.Translations
import com.plyr.utils.SpotifyImport
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Estado de la sección de importación de Spotify de los ajustes (M11): validación
 * de la URL, progreso y mensaje final. La descarga y la búsqueda se inyectan, así
 * que no hay red.
 */
class SpotifyImportControllerTest {

    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database, InMemoryTombstoneStore())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private val playlist = SpotifyImport.SpotifyPlaylist(
        id = "37i9dQZF1DXcBWIGoYBM5M",
        name = "Mi Lista",
        imageUrl = null,
        tracks = listOf(SpotifyImport.SpotifyTrack("Uno", listOf("A"), 1000)),
    )

    private fun controller(): SpotifyImportController {
        val service = SpotifyImportService(
            repository = repository,
            fetchPlaylist = { Result.success(playlist) },
            searchTrack = { "vid" },
        )
        return SpotifyImportController(scope, service)
    }

    @Test
    fun urlInvalidaMuestraErrorSinImportar() {
        val controller = controller()
        controller.onUrlChange("no es una url")

        controller.import()

        val state = controller.state.value
        assertEquals(Translations.get("spotify_import_invalid"), state.statusMessage)
        assertTrue(state.statusIsError)
        assertFalse(state.isImporting)
        assertTrue(repository.allPlaylists().isEmpty())
    }

    @Test
    fun importacionCorrectaActualizaElEstado() = runBlocking {
        val controller = controller()
        controller.onUrlChange("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        controller.import()

        val state = withTimeout(5_000) {
            controller.state.first {
                !it.isImporting && it.statusMessage != null &&
                    it.statusMessage != Translations.get("spotify_importing")
            }
        }
        assertFalse(state.statusIsError)
        assertTrue(state.statusMessage.orEmpty().contains("Mi Lista"))
        assertFalse(repository.allPlaylists().isEmpty())
    }

    @Test
    fun cambiarLaUrlLimpiaElEstado() {
        val controller = controller()
        controller.onUrlChange("malo")
        controller.import()

        controller.onUrlChange("otra url")

        assertEquals(null, controller.state.value.statusMessage)
        assertFalse(controller.state.value.statusIsError)
    }
}
