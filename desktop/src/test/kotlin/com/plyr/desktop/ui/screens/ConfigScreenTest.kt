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
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.download.AudioDownloader
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadedAudioStore
import com.plyr.desktop.spotify.SpotifyImportController
import com.plyr.desktop.spotify.SpotifyImportService
import com.plyr.desktop.sync.BackupConfig
import com.plyr.desktop.sync.BackupController
import com.plyr.desktop.sync.DataExporter
import com.plyr.desktop.sync.DataImporter
import com.plyr.desktop.sync.DataSync
import com.plyr.desktop.ui.theme.PlyrTheme
import com.plyr.desktop.utils.CoverCache
import com.plyr.domain.RepeatMode
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
            // y el botón están, y una URL vacía muestra el aviso de inmediato.
            onNodeWithTag("spotify_url").assertIsDisplayed()
            onNodeWithTag("spotify_import_button").performClick()
            onNodeWithTag("spotify_status").assertIsDisplayed()
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun muestraLaSeccionDeAlmacenamientoOffline() = runComposeUiTest {
        val store = DownloadedAudioStore(directory = Files.createTempDirectory("plyr-audio-test"))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DownloadController(scope, store, AudioDownloader(store = store))

        try {
            setContent { PlyrTheme { ConfigScreen(onBack = {}, downloadController = controller) } }

            // Sin `downloadController` la sección no se pinta; con él, el resumen y
            // el botón que abre el diálogo de borrado están.
            onNodeWithTag("offline_storage_summary").assertIsDisplayed()
            onNodeWithTag("offline_storage_button").performClick()
            onNodeWithTag("storage_dialog").assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }
}
