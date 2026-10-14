package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.download.AudioDownloader
import com.plyr.desktop.download.AudioFetcher
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadedAudioStore
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val BASE_TIME = 1_700_000_000_000L

/**
 * Detalle de lista con la UI de descarga offline (M17): el botón descarga las
 * pistas que falten, marca las pistas offline con `↓` y permite borrar la
 * descarga de una pista. La descarga va con un fetcher inyectado (sin red).
 */
@OptIn(ExperimentalTestApi::class)
class PlaylistDetailViewTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository
    private lateinit var scope: CoroutineScope
    private lateinit var controller: PlaylistsController
    private lateinit var downloadStore: DownloadedAudioStore
    private lateinit var downloadScope: CoroutineScope

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        DesktopConfig.language = Language.ENGLISH
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database, InMemoryTombstoneStore())
        scope = CoroutineScope(Dispatchers.Unconfined)
        controller = PlaylistsController(scope, repository)
        downloadStore = DownloadedAudioStore(directory = tmp.newFolder().toPath())
        downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        downloadScope.cancel()
        scope.cancel()
        database.close()
    }

    private fun downloadController() = DownloadController(
        scope = downloadScope,
        store = downloadStore,
        downloader = AudioDownloader(
            store = downloadStore,
            resolveAudioUrl = { "https://audio/$it" },
            fetcher = AudioFetcher { _, sink, _ ->
                sink.write(byteArrayOf(1))
                true
            },
            retryDelayMs = 0,
        ),
    )

    private fun track(id: String, position: Int, videoId: String) = TrackRecord(
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

    private fun seedPlaylist() {
        repository.savePlaylist(
            PlaylistRecord(
                remoteId = "p1",
                name = "Lista",
                description = null,
                trackCount = 2,
                imageUrl = null,
                lastSyncTime = BASE_TIME,
                source = PlaylistSource.LOCAL,
                sourceId = null,
            ),
            listOf(track("t1", 0, "vidA"), track("t2", 1, "vidB")),
        )
        controller.selectPlaylist("p1")
    }

    @Test
    fun descargaLaListaYMuestraElIndicadorPorPista() = runComposeUiTest {
        seedPlaylist()
        val download = downloadController()

        // `PlaylistDetailView` emite varios hijos de nivel raíz, así que se
        // envuelve en una `Column` igual que hace `PlaylistsScreen`; si no, los
        // hijos se solaparían en (0,0) y los clics acertarían al nodo de encima.
        setContent {
            PlyrTheme {
                Column {
                    PlaylistDetailView(
                        controller = controller,
                        downloadController = download,
                        state = controller.state.value,
                        onPlayTracks = { _, _ -> },
                        onBack = {},
                    )
                }
            }
        }

        onNodeWithTag("playlist_download").assertIsDisplayed()
        onNodeWithTag("playlist_download").performClick()

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("track_downloaded_r_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                onAllNodesWithTag("track_downloaded_r_t2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(downloadStore.isDownloaded("vidA"))
        assertTrue(downloadStore.isDownloaded("vidB"))

        // El botón de la lista pasa a ofrecer "limpiar" (ya está todo offline).
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("playlist_download").fetchSemanticsNodes().isNotEmpty()
        }

        // Borrar la descarga de una sola pista.
        onNodeWithTag("track_delete_download_r_t1").performClick()
        waitUntil(timeoutMillis = 5_000) { !downloadStore.isDownloaded("vidA") }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("track_downloaded_r_t1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertFalse(downloadStore.isDownloaded("vidA"))
        assertTrue(downloadStore.isDownloaded("vidB"))
    }

    @Test
    fun elBotonAleatorioBarajaLaLista() = runComposeUiTest {
        seedPlaylist()
        var randomTracks: List<TrackRecord>? = null

        setContent {
            PlyrTheme {
                Column {
                    PlaylistDetailView(
                        controller = controller,
                        downloadController = downloadController(),
                        state = controller.state.value,
                        onPlayTracks = { _, _ -> },
                        onBack = {},
                        isRandomizing = false,
                        onPlayRandom = { randomTracks = it },
                    )
                }
            }
        }

        onNodeWithTag("playlist_random").assertIsDisplayed()
        onNodeWithTag("playlist_random").performClick()

        assertEquals(2, randomTracks?.size)
    }

    @Test
    fun enModoAleatorioElBotonPara() = runComposeUiTest {
        seedPlaylist()
        var stopped = false

        setContent {
            PlyrTheme {
                Column {
                    PlaylistDetailView(
                        controller = controller,
                        downloadController = downloadController(),
                        state = controller.state.value,
                        onPlayTracks = { _, _ -> },
                        onBack = {},
                        isRandomizing = true,
                        onStopRandomize = { stopped = true },
                    )
                }
            }
        }

        onNodeWithTag("playlist_random").performClick()

        assertTrue(stopped)
    }
}
