package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.config.ThemeMode
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.spotify.SpotifyImportController
import com.plyr.desktop.spotify.SpotifyImportService
import com.plyr.desktop.sync.BackupConfig
import com.plyr.desktop.sync.BackupController
import com.plyr.desktop.sync.DataExporter
import com.plyr.desktop.sync.DataImporter
import com.plyr.desktop.sync.DataSync
import com.plyr.desktop.ui.theme.PlyrTheme
import com.plyr.desktop.utils.CoverCache
import com.plyr.ui.components.ShareUrlPolicy
import com.plyr.utils.SpotifyImport
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Ajustes del escritorio (M9): tema, idioma y rutas del SO. El estado se escribe
 * en [DesktopConfig], así que cada test parte de un almacén en memoria.
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
    fun muestraLaSeccionDeCopiaDeSeguridad() = runComposeUiTest {
        val tmp = Files.createTempDirectory("plyr-sync-test")
        val database = DesktopDatabase.inMemory()
        val tombstones = InMemoryTombstoneStore()
        val repository = PlaylistRepository(database, tombstones)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sync = DataSync(
            exporter = DataExporter(
                repository,
                tombstones,
                CoverCache(tmp.resolve("cache"), download = { null }),
            ),
            importer = DataImporter(repository, tombstones, tmp.resolve("covers")),
            config = BackupConfig(MapSettings()),
        )
        val controller = BackupController(sync, scope)

        try {
            setContent { PlyrTheme { ConfigScreen(onBack = {}, backupController = controller) } }

            // Sin `backupController` la sección no se pinta; con él, el botón está.
            onNodeWithTag("sync_button").assertIsDisplayed()
        } finally {
            scope.cancel()
            sync.shutdown()
            database.close()
        }
    }

    @Test
    fun muestraLaSeccionDeImportacionDeSpotify() = runComposeUiTest {
        val database = DesktopDatabase.inMemory()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = SpotifyImportController(
            scope = scope,
            service = SpotifyImportService(
                repository = PlaylistRepository(database, InMemoryTombstoneStore()),
                fetchPlaylist = { Result.success(SpotifyImport.SpotifyPlaylist("id22", "Lista", null, emptyList())) },
                searchTrack = { null },
            ),
        )

        try {
            setContent { PlyrTheme { ConfigScreen(onBack = {}, spotifyImportController = controller) } }

            // Sin `spotifyImportController` la sección no se pinta; con él, el campo
            // está y pulsar Intro con una URL vacía muestra el aviso de inmediato.
            onNodeWithTag("spotify_url").assertIsDisplayed()
            onNodeWithTag("spotify_url").performImeAction()
            onNodeWithTag("spotify_status").assertIsDisplayed()
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun comparteLaAppConSuQrDeDescarga() = runComposeUiTest {
        setContent { PlyrTheme { ConfigScreen(onBack = {}) } }

        // La sección «compartir app» (M24) está siempre y abre el QR de descarga
        // de plyr (la URL la arma `ShareUrlPolicy.APP_DOWNLOAD_URL`).
        onNodeWithTag("share_app_button").assertIsDisplayed()
        onNodeWithTag("share_app_button").performClick()

        onNodeWithTag("qr_url").assertIsDisplayed()
        onNodeWithText(ShareUrlPolicy.APP_DOWNLOAD_URL).assertIsDisplayed()
    }
}
