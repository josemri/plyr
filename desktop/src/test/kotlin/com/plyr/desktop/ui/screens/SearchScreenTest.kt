package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.search.SearchState
import com.plyr.desktop.ui.theme.PlyrTheme
import org.junit.Assert.assertEquals
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
}
