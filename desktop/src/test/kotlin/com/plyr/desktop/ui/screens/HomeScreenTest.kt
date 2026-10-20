package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Home del escritorio (M18/M28/M29/M36): el menú se reduce a `feed`, los ajustes
 * viven en un engranaje junto al buscador y el logo va debajo con su versión.
 *
 * M36: el panel lateral de listas ya no es parte de esta pantalla —se compone en
 * el contenedor— y se testea en `AppPlaylistRailTest`.
 */
@OptIn(ExperimentalTestApi::class)
class HomeScreenTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
    }

    @Test
    fun elMenuSeReduceAFeedYTodoLoDemasViveEnLaHome() = runComposeUiTest {
        setContent { PlyrTheme { HomeScreen(onNavigate = {}) } }

        // El menú de consola se reduce a `feed`; búsqueda y ajustes viven en la Home.
        onNodeWithTag("menu_FEED").assertIsDisplayed()
        onNodeWithTag("home_search_input").assertIsDisplayed()
        onNodeWithTag("home_config").assertIsDisplayed()
        onNodeWithTag("menu_PLAYLISTS").assertDoesNotExist()
        onNodeWithTag("menu_CONFIG").assertDoesNotExist()
        onNodeWithTag("menu_SEARCH").assertDoesNotExist()
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
    fun elLogoAsciiSeVeConSuVersionDebajoYAlineadaALaDerecha() = runComposeUiTest {
        // `logoIndex` fija el logo para que el test sea determinista (M32).
        setContent { PlyrTheme { HomeScreen(onNavigate = {}, logoIndex = 1) } }

        val logo = onNodeWithTag("home_logo").assertIsDisplayed().getBoundsInRoot()
        val version = onNodeWithTag("home_version").assertIsDisplayed().getBoundsInRoot()

        // La versión queda justo debajo del logo...
        assertTrue(
            "La versión (top=${version.top.value}) debería ir bajo el logo (bottom=${logo.bottom.value})",
            version.top.value >= logo.bottom.value,
        )
        // ...y pegada a su borde derecho.
        assertTrue(
            "La versión (right=${version.right.value}) debería alinearse con el logo (right=${logo.right.value})",
            abs((version.right - logo.right).value) <= 1f,
        )
    }
}
