package com.plyr.desktop.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.config.ThemeMode
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Test de UI del esqueleto de M5: comprueba que la app abre en Home, que el menú
 * navega entre pantallas y vuelve, que la búsqueda vive en la Home (M28) y que la
 * cola ya no está en el menú (M27), y que cambiar el idioma en Ajustes actualiza
 * las etiquetas.
 */
@OptIn(ExperimentalTestApi::class)
class AudioListScreenTest {

    private var savedTheme = ThemeMode.SYSTEM
    private var savedLanguage = Language.ENGLISH

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
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
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        onNodeWithTag("screen_HOME").assertIsDisplayed()
        // M7: la barra flotante solo aparece cuando hay algo (título, carga o
        // error). Sin reproducción, no se muestra.
        onNodeWithTag("floating_controls").assertDoesNotExist()
    }

    @Test
    fun laBusquedaDeLaHomeAbreLosResultados() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        onNodeWithTag("home_search_input").performTextInput("daft punk")
        onNodeWithTag("home_search_input").performImeAction()
        onNodeWithTag("screen_SEARCH").assertIsDisplayed()
    }

    @Test
    fun elBotonVolverRegresaAHome() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        onNodeWithTag("home_config").performClick()
        onNodeWithTag("screen_CONFIG").assertIsDisplayed()
        onNodeWithTag("back_from_CONFIG").performScrollTo().performClick()
        onNodeWithTag("screen_HOME").assertIsDisplayed()
    }

    @Test
    fun laColaNoEstaEnElMenu() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        onNodeWithTag("screen_HOME").assertIsDisplayed()
        // M27: como en Android, la cola solo se abre con el `^` de los controles.
        onNodeWithTag("menu_QUEUE").assertDoesNotExist()
    }

    @Test
    fun cambiarIdiomaActualizaLasEtiquetas() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        onNodeWithTag("home_config").performClick()
        // En inglés el título es "plyr_config".
        onNodeWithText("$ plyr_config").assertIsDisplayed()
        // Al pasar a español debe re-traducirse a "plyr_ajustes".
        onNodeWithTag("lang_spanish").performClick()
        onNodeWithText("$ plyr_ajustes").assertIsDisplayed()
    }

    @Test
    fun cambiarTemaNoRompeLaPantalla() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        onNodeWithTag("home_config").performClick()
        onNodeWithTag("theme_light").performClick()
        onNodeWithTag("screen_CONFIG").assertIsDisplayed()
    }

    @Test
    fun laFlechaDelPanelAbreListas() = runComposeUiTest {
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }
        // M29: el `>` al final del panel lateral sustituye al botón `plyr_lists`.
        onNodeWithTag("home_more_lists").performClick()
        onNodeWithTag("screen_PLAYLISTS").assertIsDisplayed()
    }

    @Test
    fun laFlechaDelPanelMuestraLaRejillaTrasAbrirUnaLista() = runComposeUiTest {
        // M34: abrir una lista desde el panel y volver a la Home no debe dejar la
        // lista seleccionada; el `>` lleva a la rejilla, no al último detalle.
        val database = DesktopDatabase.inMemory()
        PlaylistRepository(database).savePlaylist(
            PlaylistRecord(
                remoteId = "local_1",
                name = "Rock",
                description = null,
                trackCount = 1,
                imageUrl = null,
                lastSyncTime = 0L,
                source = PlaylistSource.LOCAL,
                sourceId = null,
            ),
            listOf(
                TrackRecord(
                    id = "local_1_t1",
                    playlistId = "local_1",
                    remoteTrackId = "t1",
                    name = "Song",
                    artists = "Artist",
                    youtubeVideoId = "vid",
                    audioUrl = null,
                    position = 0,
                    lastSyncTime = 0L,
                ),
            ),
        )

        setContent { PlyrTheme { AudioListScreen(openDatabase = { database }) } }

        onNodeWithTag("home_playlist_local_1").performClick()
        onNodeWithTag("back_from_playlist_detail").assertIsDisplayed()
        onNodeWithTag("back_from_playlist_detail").performClick()
        onNodeWithTag("screen_HOME").assertIsDisplayed()

        onNodeWithTag("home_more_lists").performClick()
        onNodeWithTag("screen_PLAYLISTS").assertIsDisplayed()
        // Rejilla (botón de alta visible), no el detalle de la lista anterior.
        onNodeWithTag("playlists_create").assertIsDisplayed()
        onNodeWithTag("back_from_playlist_detail").assertDoesNotExist()
    }

    @Test
    fun elPanelDeListasEstaVisibleEnCualquierPantalla() = runComposeUiTest {
        // M36: el panel deja de ser exclusivo de la Home y pasa a componerse en el
        // contenedor, así que sigue visible al cambiar de sección.
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }

        onNodeWithTag("home_config").performClick()
        onNodeWithTag("screen_CONFIG").assertIsDisplayed()

        onNodeWithTag("home_playlists").assertIsDisplayed()
        onNodeWithTag("home_more_lists").assertIsDisplayed()
    }

    @Test
    fun elBotonLogoDelPanelVuelveAlInicioDesdeOtraPantalla() = runComposeUiTest {
        // M36: el logo es el botón de vuelta al inicio, siempre visible.
        setContent { PlyrTheme { AudioListScreen(openDatabase = { DesktopDatabase.inMemory() }) } }

        onNodeWithTag("home_search_input").performTextInput("daft punk")
        onNodeWithTag("home_search_input").performImeAction()
        onNodeWithTag("screen_SEARCH").assertIsDisplayed()

        onNodeWithTag("app_logo_home").assertIsDisplayed()
        onNodeWithTag("app_logo_home").performClick()

        onNodeWithTag("screen_HOME").assertIsDisplayed()
    }
}
