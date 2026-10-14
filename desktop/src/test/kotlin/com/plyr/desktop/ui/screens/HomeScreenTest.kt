package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Home del escritorio (M18): carrusel horizontal de las listas visibles con su
 * portada. Pulsar una lista avisa para abrir su detalle; sin listas, el carrusel
 * no ocupa sitio y el menú de consola sigue igual.
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
    }

    @Test
    fun sinListasNoMuestraElCarrusel() = runComposeUiTest {
        setContent { PlyrTheme { HomeScreen(onNavigate = {}) } }

        onNodeWithTag("home_playlist_p1").assertDoesNotExist()
        // El menú de consola sigue estando.
        onNodeWithTag("menu_SEARCH").assertIsDisplayed()
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
