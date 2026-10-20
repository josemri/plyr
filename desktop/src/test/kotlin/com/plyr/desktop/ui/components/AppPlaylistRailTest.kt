package com.plyr.desktop.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.theme.AppIcon
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Panel lateral de listas (M36): antes vivía en la Home y solo se veía allí;
 * ahora lo compone el contenedor, así que está presente en todas las pantallas.
 * Comprueba que lista las listas visibles, que su `>` abre la rejilla, que al
 * pulsar una se avisa para abrirla y que el botón-logo vuelve al inicio.
 */
@OptIn(ExperimentalTestApi::class)
class AppPlaylistRailTest {

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
    fun muestraLasListasVisiblesYElAtajoALaRejilla() = runComposeUiTest {
        setContent {
            PlyrTheme {
                AppPlaylistRail(
                    onHome = {},
                    onNavigate = {},
                    playlists = listOf(playlist("p1", "Rock"), playlist("p2", "Jazz")),
                )
            }
        }

        onNodeWithTag("home_playlists").assertIsDisplayed()
        onNodeWithTag("home_playlist_p1").assertIsDisplayed()
        onNodeWithTag("home_playlist_p2").assertIsDisplayed()
        onNodeWithTag("home_more_lists").assertIsDisplayed()
    }

    @Test
    fun sinListasElPanelSigueVisiblePeroVacio() = runComposeUiTest {
        setContent { PlyrTheme { AppPlaylistRail(onHome = {}, onNavigate = {}) } }

        // El panel está siempre visible, aunque no haya listas que mostrar.
        onNodeWithTag("home_playlists").assertIsDisplayed()
        onNodeWithTag("home_playlist_p1").assertDoesNotExist()
        onNodeWithTag("home_more_lists").assertIsDisplayed()
    }

    @Test
    fun laFlechaAvisaParaAbrirLaRejillaDeListas() = runComposeUiTest {
        var destino: Screen? = null
        setContent { PlyrTheme { AppPlaylistRail(onHome = {}, onNavigate = { destino = it }) } }

        onNodeWithTag("home_more_lists").performClick()

        assertEquals(Screen.PLAYLISTS, destino)
    }

    @Test
    fun pulsarUnaListaAvisaParaAbrirSuDetalle() = runComposeUiTest {
        var abierta: String? = null
        setContent {
            PlyrTheme {
                AppPlaylistRail(
                    onHome = {},
                    onNavigate = {},
                    playlists = listOf(playlist("p1", "Rock")),
                    onOpenPlaylist = { abierta = it },
                )
            }
        }

        onNodeWithTag("home_playlist_p1").performClick()

        assertEquals("p1", abierta)
    }

    @Test
    fun elBotonLogoEstaSiempreVisibleYAvisaParaVolverAlInicio() = runComposeUiTest {
        var inicios = 0
        setContent {
            PlyrTheme {
                AppPlaylistRail(
                    onHome = { inicios++ },
                    onNavigate = {},
                    playlists = listOf(playlist("p1", "Rock")),
                )
            }
        }

        onNodeWithTag("app_logo_home").assertIsDisplayed()
        onNodeWithTag("app_logo_home").performClick()

        assertEquals(1, inicios)
    }

    @Test
    fun elIconoEsElIcLauncherDeAndroid() {
        // M36: el botón-logo, la ventana y los instaladores usan el mismo icono
        // que Android. Si este test salta, falta resources/icon/plyr.png.
        val icono = AppIcon.load()

        assertNotNull("No se encuentra ${AppIcon.RESOURCE} en el classpath del escritorio", icono)
        // La fuente es `ic_launcher-playstore.png`, 512x512.
        assertEquals(512, icono!!.width)
        assertEquals(512, icono.height)
    }
}
