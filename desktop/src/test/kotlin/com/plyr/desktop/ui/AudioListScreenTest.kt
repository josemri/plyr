package com.plyr.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.config.ThemeMode
import com.plyr.desktop.ui.theme.PlyrTheme
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Test de UI del esqueleto de M5: comprueba que la app abre en Home, que el
 * menú navega entre pantallas y vuelve, que el botón de cola de los controles
 * flotantes navega, y que cambiar el idioma en Ajustes actualiza las etiquetas.
 */
@OptIn(ExperimentalTestApi::class)
class AudioListScreenTest {

    private var savedTheme = ThemeMode.SYSTEM
    private var savedLanguage = Language.ENGLISH

    @Before
    fun setUp() {
        savedTheme = DesktopConfig.theme
        savedLanguage = DesktopConfig.language
        DesktopConfig.theme = ThemeMode.DARK
        DesktopConfig.language = Language.ENGLISH
    }

    @After
    fun tearDown() {
        DesktopConfig.theme = savedTheme
        DesktopConfig.language = savedLanguage
    }

    @Test
    fun arrancaEnHome() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen() } }
        onNodeWithTag("screen_HOME").assertIsDisplayed()
        onNodeWithTag("floating_controls").assertIsDisplayed()
    }

    @Test
    fun elMenuNavegaABuscar() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen() } }
        onNodeWithTag("menu_SEARCH").performClick()
        onNodeWithTag("screen_SEARCH").assertIsDisplayed()
    }

    @Test
    fun elBotonVolverRegresaAHome() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen() } }
        onNodeWithTag("menu_CONFIG").performClick()
        onNodeWithTag("screen_CONFIG").assertIsDisplayed()
        onNodeWithTag("back_from_CONFIG").performClick()
        onNodeWithTag("screen_HOME").assertIsDisplayed()
    }

    @Test
    fun elBotonDeColaNavegaAQueue() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen() } }
        onNodeWithTag("control_queue").performClick()
        onNodeWithTag("screen_QUEUE").assertIsDisplayed()
    }

    @Test
    fun cambiarIdiomaActualizaLasEtiquetas() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen() } }
        onNodeWithTag("menu_CONFIG").performClick()
        // En inglés el título es "plyr_config".
        onNodeWithText("$ plyr_config").assertIsDisplayed()
        // Al pasar a español debe re-traducirse a "plyr_ajustes".
        onNodeWithTag("lang_spanish").performClick()
        onNodeWithText("$ plyr_ajustes").assertIsDisplayed()
    }

    @Test
    fun cambiarTemaNoRompeLaPantalla() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen() } }
        onNodeWithTag("menu_CONFIG").performClick()
        onNodeWithTag("theme_light").performClick()
        onNodeWithTag("screen_CONFIG").assertIsDisplayed()
    }
}
