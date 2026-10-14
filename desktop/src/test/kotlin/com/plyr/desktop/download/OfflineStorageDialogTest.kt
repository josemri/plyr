package com.plyr.desktop.download

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.ui.theme.PlyrTheme
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Diálogo de almacenamiento offline (M17): muestra cuántas pistas hay y cuánto
 * ocupan, y el botón «borrar todo» vacía el almacén.
 */
@OptIn(ExperimentalTestApi::class)
class OfflineStorageDialogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        DesktopConfig.language = Language.ENGLISH
    }

    private fun store() = DownloadedAudioStore(directory = tmp.newFolder().toPath())

    private fun controller(store: DownloadedAudioStore): Pair<DownloadController, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = DownloadController(
            scope = scope,
            store = store,
            downloader = AudioDownloader(store = store),
        )
        return controller to scope
    }

    @Test
    fun muestraElResumenYBorraTodo() = runComposeUiTest {
        val store = store()
        runBlocking {
            store.write("vidA") { it.write(ByteArray(1024)); true }
            store.write("vidB") { it.write(ByteArray(1024)); true }
        }
        val (controller, scope) = controller(store)

        try {
            setContent { PlyrTheme { OfflineStorageDialog(controller = controller, onDismiss = {}) } }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("audio: 2 tracks · 2.0 KB").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("audio: 2 tracks · 2.0 KB").assertIsDisplayed()

            onNodeWithTag("storage_delete_all").performClick()

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("no downloaded audio").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals(0, store.summary().count)
            onNodeWithTag("storage_message").assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun sinAudioNoOfreceBorrar() = runComposeUiTest {
        val store = store()
        val (controller, scope) = controller(store)

        try {
            setContent { PlyrTheme { OfflineStorageDialog(controller = controller, onDismiss = {}) } }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("no downloaded audio").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("storage_message").assertIsDisplayed()
            onNodeWithTag("storage_delete_all").assertDoesNotExist()
        } finally {
            scope.cancel()
        }
    }
}
