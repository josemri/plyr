package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Home del escritorio (M18/M28/M29): panel vertical de listas visibles con su
 * portada y un `>` que abre la pantalla de listas; el menú se reduce a `feed` y
 * los ajustes pasan a un engranaje junto al buscador. Pulsar una lista avisa para
 * abrir su detalle; sin listas, el panel sigue visible pero vacío.
 */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
    }

    private fun playlist(remoteId: String, name: String, imageUrl: String? = null) = PlaylistRecord(
        remoteId = remoteId,
        name = name,
        description = null,
        trackCount = 3,
        imageUrl = imageUrl,
        lastSyncTime = 0L,
        source = PlaylistSource.LOCAL,
        sourceId = null,
    )

    @Test
    fun muestraElCarruselConLasListasVisibles() = runComposeUiTest {
        setContent {
            PlyrTheme {
                HomeScreen(
                    onNavigate = {},
                    playlists = listOf(playlist("p1", "Rock"), playlist("p2", "Jazz")),
                )
            }
        }

        onNodeWithTag("home_playlists").assertIsDisplayed()
        onNodeWithTag("home_playlist_p1").assertIsDisplayed()
        onNodeWithTag("home_playlist_p2").assertIsDisplayed()
        // El `>` de la pantalla de listas cierra el panel (M29).
        onNodeWithTag("home_more_lists").assertIsDisplayed()
    }

    @Test
    fun sinListasElPanelSigueVisiblePeroVacio() = runComposeUiTest {
        setContent { PlyrTheme { HomeScreen(onNavigate = {}) } }

        // El panel lateral está siempre visible, aunque no haya listas que mostrar.
        onNodeWithTag("home_playlists").assertIsDisplayed()
        onNodeWithTag("home_playlist_p1").assertDoesNotExist()
        // El menú de consola se reduce a `feed`; búsqueda y ajustes viven en la Home.
        onNodeWithTag("menu_FEED").assertIsDisplayed()
        onNodeWithTag("home_search_input").assertIsDisplayed()
        onNodeWithTag("home_config").assertIsDisplayed()
        onNodeWithTag("menu_PLAYLISTS").assertDoesNotExist()
        onNodeWithTag("menu_CONFIG").assertDoesNotExist()
        onNodeWithTag("menu_SEARCH").assertDoesNotExist()
    }

    @Test
    fun laFlechaDelPanelAvisaParaAbrirListas() = runComposeUiTest {
        var destino: Screen? = null
        setContent { PlyrTheme { HomeScreen(onNavigate = { destino = it }) } }

        onNodeWithTag("home_more_lists").performClick()

        assertEquals(Screen.PLAYLISTS, destino)
    }

    @Test
    fun elEngranajeAvisaParaAbrirAjustes() = runComposeUiTest {
        var destino: Screen? = null
        setContent { PlyrTheme { HomeScreen(onNavigate = { destino = it }) } }

        onNodeWithTag("home_config").performClick()

        assertEquals(Screen.CONFIG, destino)
    }

    @Test
    fun escribirYBuscarAvisaConLaConsulta() = runComposeUiTest {
        var consulta: String? = null
        setContent { PlyrTheme { HomeScreen(onNavigate = {}, onSearch = { consulta = it }) } }

        onNodeWithTag("home_search_input").performTextInput("daft punk")
        onNodeWithTag("home_search_input").performImeAction()

        assertEquals("daft punk", consulta)
    }

    @Test
    fun pulsarUnaListaAvisaParaAbrirSuDetalle() = runComposeUiTest {
        var abierta: String? = null
        setContent {
            PlyrTheme {
                HomeScreen(
                    onNavigate = {},
                    playlists = listOf(playlist("p1", "Rock")),
                    onOpenPlaylist = { abierta = it },
                )
            }
        }

        onNodeWithTag("home_playlist_p1").performClick()

        assertEquals("p1", abierta)
    }
}
