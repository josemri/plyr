package com.plyr.desktop.ui.screens

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
import com.plyr.domain.RepeatMode
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Ajustes del escritorio (M9): tema, idioma, repetición y rutas del SO. El estado
 * se escribe en [DesktopConfig], así que cada test parte de un almacén en memoria.
 */
@OptIn(ExperimentalTestApi::class)
class ConfigScreenTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
    }

    @Test
    fun cambiarElTemaLoPersiste() = runComposeUiTest {
        setContent { PlyrTheme { ConfigScreen(onBack = {}) } }

        onNodeWithTag("theme_light").performClick()

        assertEquals(ThemeMode.LIGHT, DesktopConfig.theme)
    }

    @Test
    fun cambiarElIdiomaRetraduceLaPantalla() = runComposeUiTest {
        setContent { PlyrTheme { ConfigScreen(onBack = {}) } }

        onNodeWithText("$ plyr_config").assertIsDisplayed()
        onNodeWithTag("lang_spanish").performClick()
        onNodeWithText("$ plyr_ajustes").assertIsDisplayed()
        assertEquals(Language.SPANISH, DesktopConfig.language)
    }

    @Test
    fun cambiarLaRepeticionAvisaAlReproductor() = runComposeUiTest {
        var seleccionado: String? = null
        setContent {
            PlyrTheme { ConfigScreen(onBack = {}, onRepeatModeSelected = { seleccionado = it }) }
        }

        onNodeWithTag("repeat_all").performClick()

        assertEquals(RepeatMode.ALL, seleccionado)
    }

    @Test
    fun elBotonVolverInvocaElCallback() = runComposeUiTest {
        var volvio = false
        setContent { PlyrTheme { ConfigScreen(onBack = { volvio = true }) } }

        onNodeWithTag("back_from_CONFIG").performClick()

        assertEquals(true, volvio)
    }

    @Test
    fun muestraLasRutasDeDatosYCache() = runComposeUiTest {
        setContent { PlyrTheme { ConfigScreen(onBack = {}) } }

        onNodeWithTag("path_data").assertIsDisplayed()
        onNodeWithTag("path_cache").assertIsDisplayed()
    }

    @Test
    fun sinCallbackEscribeEnElConfig() = runComposeUiTest {
        // `onRepeatModeSelected` tiene valor por defecto: escribe en DesktopConfig.
        setContent { PlyrTheme { ConfigScreen(onBack = {}) } }

        onNodeWithTag("repeat_one").performClick()
        assertEquals(RepeatMode.ONE, DesktopConfig.repeatMode)
    }
}
