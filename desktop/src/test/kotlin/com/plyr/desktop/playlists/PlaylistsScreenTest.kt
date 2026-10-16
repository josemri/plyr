package com.plyr.desktop.playlists

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.download.AudioDownloader
import com.plyr.desktop.download.AudioFetcher
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadedAudioStore
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Rejilla de listas (M30): la pantalla ya no es un listado sino una rejilla
 * adaptativa como la de Android, con el botón `< back` siempre visible para
 * volver a la Home (en escritorio no hay "back" del sistema que lo haga).
 */
@OptIn(ExperimentalTestApi::class)
class PlaylistsScreenTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository
    private lateinit var scope: CoroutineScope
    private lateinit var controller: PlaylistsController
    private lateinit var downloadScope: CoroutineScope

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        DesktopConfig.language = Language.ENGLISH
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database, InMemoryTombstoneStore())
        scope = CoroutineScope(Dispatchers.Unconfined)
        controller = PlaylistsController(scope, repository)
        downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        downloadScope.cancel()
        scope.cancel()
        database.close()
    }

    private fun downloadController(): DownloadController {
        val store = DownloadedAudioStore(tmp.newFolder().toPath())
        return DownloadController(
            scope = downloadScope,
            store = store,
            downloader = AudioDownloader(
                store = store,
                resolveAudioUrl = { "https://audio/$it" },
                fetcher = AudioFetcher { _, sink, _ ->
                    sink.write(byteArrayOf(1))
                    true
                },
                retryDelayMs = 0,
            ),
        )
    }

    private fun seedPlaylist() {
        repository.savePlaylist(
            PlaylistRecord(
                remoteId = "p1",
                name = "Lista",
                description = null,
                trackCount = 2,
                imageUrl = null,
                lastSyncTime = 1_700_000_000_000L,
                source = PlaylistSource.LOCAL,
                sourceId = null,
            ),
            emptyList(),
        )
    }

    @Test
    fun laRejillaMuestraLasListasYElBotonDeVolver() = runComposeUiTest {
        seedPlaylist()

        setContent {
            PlyrTheme {
                PlaylistsScreen(
                    controller = controller,
                    downloadController = downloadController(),
                    onBack = {},
                )
            }
        }

        onNodeWithTag("screen_PLAYLISTS").assertIsDisplayed()
        onNodeWithTag("playlist_item_p1").assertIsDisplayed()
        onNodeWithTag("playlists_create").assertIsDisplayed()
        // El botón de volver queda visible (antes lo empujaba fuera el listado).
        onNodeWithTag("back_from_PLAYLISTS").assertIsDisplayed()
    }

    @Test
    fun elBotonDeVolverAvisaParaSalir() = runComposeUiTest {
        seedPlaylist()
        var wentBack = false

        setContent {
            PlyrTheme {
                PlaylistsScreen(
                    controller = controller,
                    downloadController = downloadController(),
                    onBack = { wentBack = true },
                )
            }
        }

        onNodeWithTag("back_from_PLAYLISTS").performClick()
        assertTrue(wentBack)
    }

    @Test
    fun elMasAbreElAltaDeLista() = runComposeUiTest {
        setContent {
            PlyrTheme {
                PlaylistsScreen(
                    controller = controller,
                    downloadController = downloadController(),
                    onBack = {},
                )
            }
        }

        onNodeWithTag("playlists_create").performClick()
        onNodeWithTag("create_playlist_confirm").assertIsDisplayed()
    }

    @Test
    fun sinListasMuestraElEstadoVacio() = runComposeUiTest {
        setContent {
            PlyrTheme {
                PlaylistsScreen(
                    controller = controller,
                    downloadController = downloadController(),
                    onBack = {},
                )
            }
        }

        onNodeWithText(Translations.get("no_playlists")).assertIsDisplayed()
    }
}
