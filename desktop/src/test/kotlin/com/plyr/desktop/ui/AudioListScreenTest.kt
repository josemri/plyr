package com.plyr.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import com.plyr.desktop.download.DownloadedAudioStore
import com.plyr.desktop.player.FakeAudioPlayer
import com.plyr.desktop.player.PlayerController
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.math.abs

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

    @Test
    fun laRecomposicionNoRecreaElControladorDelReproductor() = runComposeUiTest {
        // B64: en `Main.kt`, `AudioListScreen` se llama desde dentro de
        // `application { Window { … } }`, cuyo contenido se recompone (el tema del
        // sistema, el estado del padre…). Una recomposición de ese padre vuelve a
        // **invocar** `AudioListScreen` y **reevalúa sus parámetros por defecto**:
        // `downloadStore = DownloadedAudioStore()` es un objeto nuevo y, como no
        // define `equals`, `remember(scope, downloadStore)` reconstruía el
        // `PlayerController`. El `DisposableEffect` viejo ejecutaba `release()`
        // sobre el VLC que estaba bufferizando y la pista moría antes de sonar.
        val ready = mutableListOf<PlayerController>()
        var parentState by mutableIntStateOf(0)
        setContent {
            PlyrTheme {
                // El padre lee estado y lo pasa: al cambiar, el padre se
                // recompone y vuelve a invocar `AudioListScreen`.
                val marker = parentState
                AudioListScreen(
                    openDatabase = { DesktopDatabase.inMemory() },
                    checkForUpdate = { null },
                    onPlayerControllerReady = { ready += it },
                    onQuit = { marker },
                )
            }
        }
        waitForIdle()

        val first = ready.last()
        // Cambio de estado del padre: recompone e invoca de nuevo la pantalla.
        parentState = parentState + 1
        waitForIdle()

        assertSame(
            "Recomponer el padre no debe recrear el PlayerController",
            first,
            ready.last(),
        )
    }

    @Test
    fun laBarraFlotanteNoTapaElPanelDeListas() = runComposeUiTest {
        // B65: el panel de listas debe ocupar todo el lateral izquierdo (de
        // arriba a abajo) y la barra flotante vivir solo en la columna de
        // contenido, adaptándose al ancho que le queda a su derecha. Antes la
        // barra se pintaba sobre todo el ancho y tapaba el final del panel.
        val audioDir = Files.createTempDirectory("plyr-rail-test")
        try {
            Files.createFile(audioDir.resolve("railTestVideo.m4a"))
            val ready = mutableListOf<PlayerController>()
            setContent {
                PlyrTheme {
                    AudioListScreen(
                        openDatabase = { DesktopDatabase.inMemory() },
                        downloadStore = DownloadedAudioStore(audioDir),
                        openPlayer = { FakeAudioPlayer() },
                        onPlayerControllerReady = { ready += it },
                    )
                }
            }
            waitForIdle()

            ready.last().play(
                listOf(
                    PlayerTrack(
                        id = "rail-test",
                        title = "Canción",
                        artist = "Artista",
                        videoId = "railTestVideo",
                    ),
                ),
                0,
            )
            waitForIdle()

            val root = onRoot().getBoundsInRoot()
            val rail = onNodeWithTag("playlist_rail")
                .assertIsDisplayed()
                .getBoundsInRoot()
            val controls = onNodeWithTag("floating_controls")
                .assertIsDisplayed()
                .getBoundsInRoot()

            // El panel llega hasta el borde inferior de la ventana...
            assertTrue(
                "El panel de listas debería ocupar todo el alto " +
                    "(bottom=${rail.bottom.value}, ventana=${root.bottom.value})",
                rail.bottom.value >= root.bottom.value - 1f,
            )
            // ...y la barra empieza a su derecha, sin pintarse encima.
            assertTrue(
                "La barra (left=${controls.left.value}) no debe tapar el panel " +
                    "(right=${rail.right.value})",
                controls.left.value >= rail.right.value - 1f,
            )
            // El margen inferior debe ser el mismo que deja a los lados: antes
            // eran 4 dp abajo frente a 16 dp laterales y quedaba pegada al borde.
            val sideInset = controls.left.value - rail.right.value
            val bottomInset = root.bottom.value - controls.bottom.value
            assertTrue(
                "La barra debe dejar abajo el mismo margen que a los lados " +
                    "(abajo=$bottomInset, lateral=$sideInset)",
                abs(bottomInset - sideInset) <= 1f,
            )
        } finally {
            audioDir.toFile().deleteRecursively()
        }
    }
}
