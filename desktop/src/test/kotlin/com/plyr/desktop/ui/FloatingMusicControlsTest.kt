package com.plyr.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Controles flotantes (M5/M27): comprueba que el `^` es el acceso a la cola en
 * escritorio (equivalente al Android) y que la barra no aparece sin reproducción.
 */
@OptIn(ExperimentalTestApi::class)
class FloatingMusicControlsTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
    }

    @Test
    fun elBotonDeColaAvisaParaMostrarLaCola() = runComposeUiTest {
        var mostrada = false
        setContent {
            PlyrTheme {
                FloatingMusicControls(
                    state = PlayerUiState(title = "Canción", isPlaying = true),
                    onShowQueue = { mostrada = true },
                )
            }
        }

        onNodeWithTag("control_queue").assertIsDisplayed().performClick()

        assertTrue("El `^` debería pedir mostrar la cola", mostrada)
    }

    @Test
    fun sinReproduccionNoAparecenLosControles() = runComposeUiTest {
        setContent { PlyrTheme { FloatingMusicControls(state = PlayerUiState()) } }

        onNodeWithTag("floating_controls").assertDoesNotExist()
        onNodeWithTag("control_queue").assertDoesNotExist()
    }
}
