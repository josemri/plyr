package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.search.SearchState
import com.plyr.desktop.ui.components.TrackMenuActions
import com.plyr.desktop.ui.theme.PlyrTheme
import com.plyr.network.YouTubePlaylistInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de UI de la pantalla de búsqueda (M7): resultados, estados vacío/carga/
 * error y el callback de reproducción. Se renderiza con estado falso, así que no
 * toca ni red ni motor de audio.
 */
@OptIn(ExperimentalTestApi::class)
class SearchScreenTest {

    private fun track(id: String) = PlayerTrack(id = id, title = "Track $id", artist = "Artist $id", duration = "3:00")

    @Test
    fun muestraResultadosYAlPulsarReproduce() = runComposeUiTest {
        var played: Int? = null
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "daft punk", results = listOf(track("a"), track("b"))),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = { played = it },
                    onBack = {},
                )
            }
        }

        onNodeWithText("1. Track a").assertIsDisplayed()
        onNodeWithText("2. Track b").assertIsDisplayed()

        onNodeWithTag("search_result_1").performClick()
        assertEquals(1, played)
    }

    @Test
    fun estadoVacioMuestraMensaje() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "nada"),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_empty").assertIsDisplayed()
    }

    @Test
    fun estadoDeCargaMuestraIndicador() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "x", isLoading = true),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_loading").assertIsDisplayed()
    }

    @Test
    fun estadoDeErrorMuestraElMensaje() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "x", error = "red caída"),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_error").assertIsDisplayed()
    }

    @Test
    fun elBotonVolverInvocaElCallback() = runComposeUiTest {
        var back = false
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = { back = true },
                )
            }
        }

        onNodeWithTag("back_from_SEARCH").performClick()
        assertEquals(true, back)
    }

    @Test
    fun muestraListasYAlPulsarAbreElDetalle() = runComposeUiTest {
        var opened: YouTubePlaylistInfo? = null
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "mix",
                        playlists = listOf(YouTubePlaylistInfo("PL1", "Mix", "DJ", 3, null)),
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                    onOpenPlaylist = { opened = it },
                )
            }
        }

        onNodeWithTag("search_playlist_PL1").assertIsDisplayed()
        onNodeWithTag("search_playlist_PL1").performClick()

        assertEquals("PL1", opened?.playlistId)
    }

    @Test
    fun seccionDeVideosPlegadaOcultaSusResultados() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "x",
                        results = listOf(track("a")),
                        videosExpanded = false,
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_videos_header").assertIsDisplayed()
        onNodeWithTag("search_result_0").assertDoesNotExist()
    }

    @Test
    fun plegarVideosInvocaElCallback() = runComposeUiTest {
        var toggled = false
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "x", results = listOf(track("a"))),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                    onToggleVideos = { toggled = true },
                )
            }
        }

        onNodeWithTag("search_videos_header").performClick()

        assertTrue(toggled)
    }

    @Test
    fun seccionDeListasPlegadaOcultaSusListas() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "x",
                        playlists = listOf(YouTubePlaylistInfo("PL1", "Mix", "DJ", 3, null)),
                        playlistsExpanded = false,
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                    onOpenPlaylist = {},
                )
            }
        }

        onNodeWithTag("search_playlists_header").assertIsDisplayed()
        onNodeWithTag("search_playlist_PL1").assertDoesNotExist()
    }

    // --- M21: paginación («cargar más») ---

    @Test
    fun cargarMasApareceConElUmbralYUnaPaginaDetras() = runComposeUiTest {
        var loaded = false
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "x",
                        results = (1..10).map { track("t$it") },
                        hasMore = true,
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                    onLoadMore = { loaded = true },
                )
            }
        }

        onNodeWithTag("search_load_more").assertIsDisplayed()
        onNodeWithTag("search_load_more").performClick()

        assertTrue(loaded)
    }

    @Test
    fun sinPaginaDetrasNoSeOfreceCargarMas() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "x",
                        results = (1..15).map { track("t$it") },
                        hasMore = false,
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_load_more").assertDoesNotExist()
    }

    @Test
    fun porDebajoDelUmbralNoSeOfreceCargarMas() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "x",
                        results = (1..5).map { track("t$it") },
                        hasMore = true,
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_load_more").assertDoesNotExist()
    }

    @Test
    fun mientrasCargaMasSeMuestraElIndicador() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(
                        query = "x",
                        results = (1..10).map { track("t$it") },
                        hasMore = true,
                        isLoadingMore = true,
                    ),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_loading_more").assertIsDisplayed()
        onNodeWithTag("search_load_more").assertDoesNotExist()
    }

    // --- M22: menú de acciones por pista ---

    @Test
    fun elMenuDeUnResultadoOfreceAcciones() = runComposeUiTest {
        val liked = mutableListOf<PlayerTrack>()
        var queued: PlayerTrack? = null

        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "x", results = listOf(track("a"))),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                    menuActions = TrackMenuActions(
                        isLiked = { false },
                        onToggleLike = { liked += it },
                        onAddToQueue = { queued = it },
                        onShare = {},
                    ),
                )
            }
        }

        onNodeWithTag("search_menu_0").performClick()
        onNodeWithTag("track_like", useUnmergedTree = true).performClick()

        assertEquals(listOf(track("a")), liked)

        onNodeWithTag("search_menu_0").performClick()
        onNodeWithTag("track_add_queue", useUnmergedTree = true).performClick()

        assertEquals(track("a"), queued)
    }

    @Test
    fun sinAccionesNoSeMuestraElMenu() = runComposeUiTest {
        setContent {
            PlyrTheme {
                SearchScreen(
                    state = SearchState(query = "x", results = listOf(track("a"))),
                    onQueryChange = {},
                    onSearch = {},
                    onPlayResult = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("search_menu_0").assertDoesNotExist()
    }
}
