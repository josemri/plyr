package com.plyr.desktop.sync

import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.database.TombstoneStore
import com.plyr.desktop.utils.CoverCache
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Lógica de la sección de copia de seguridad de los ajustes (M10): elegir carpeta
 * la primera vez, sincronizar cuando ya hay copia y traducir el resultado.
 *
 * El selector de carpeta se inyecta, así que no se abre ningún diálogo nativo.
 */
class BackupControllerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: DesktopDatabase
    private lateinit var tombstones: TombstoneStore
    private lateinit var repository: PlaylistRepository
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        database = DesktopDatabase.inMemory()
        tombstones = InMemoryTombstoneStore()
        repository = PlaylistRepository(database, tombstones)
        repository.savePlaylist(
            PlaylistRecord(
                remoteId = "p1",
                name = "Rock",
                description = null,
                trackCount = 0,
                imageUrl = null,
                lastSyncTime = TIME,
                source = PlaylistSource.LOCAL,
                sourceId = null,
            ),
            emptyList(),
        )
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    private fun controller(
        config: BackupConfig,
        chooseFolder: (String) -> Path? = { null },
    ): Pair<BackupController, DataSync> {
        val dir = temp.newFolder().toPath()
        val sync = DataSync(
            exporter = DataExporter(repository, tombstones, CoverCache(dir, download = { null })),
            importer = DataImporter(repository, tombstones, temp.newFolder().toPath()),
            config = config,
        )
        return BackupController(sync, scope, chooseFolder) to sync
    }

    @Test
    fun sinCopiaElBotonPideCarpetaYSincroniza() = runBlocking {
        val folder = temp.newFolder().toPath()
        val config = BackupConfig(MapSettings())
        var requested = false
        val (control, sync) = controller(config) {
            requested = true
            folder
        }

        control.refresh()
        control.onSyncPressed()
        val state = awaitState(control) { it.statusMessage != null }

        assertTrue("Debe pedir carpeta cuando no hay copia", requested)
        assertEquals(folder.toString(), config.folder)
        assertTrue("El ZIP debe quedar escrito", Files.isRegularFile(BackupFolder.backupFile(folder)))
        assertEquals(folder.toString(), state.folderPath)
        assertFalse(state.statusIsError)
        sync.shutdown()
    }

    @Test
    fun conCopiaElBotonSincronizaSinPedirCarpeta() = runBlocking {
        val folder = temp.newFolder().toPath()
        val config = BackupConfig(MapSettings()).also { it.folder = folder.toString() }
        var requested = false
        val (control, sync) = controller(config) {
            requested = true
            folder
        }

        // Primera pasada para que exista el ZIP en la carpeta.
        control.refresh()
        control.onSyncPressed()
        awaitState(control) { it.statusMessage != null }
        assertTrue(control.state.value.hasBackupFile)

        // Segunda pasada: ya hay copia, no debe volver a pedir carpeta.
        requested = false
        control.onSyncPressed()
        val state = awaitState(control) { it.statusMessage != null && !it.isSyncing }

        assertFalse("No debe pedir carpeta si ya hay copia", requested)
        assertNotNull(state.statusMessage)
        sync.shutdown()
    }

    @Test
    fun cancelarElSelectorNoHaceNada() = runBlocking {
        val config = BackupConfig(MapSettings())
        val (control, sync) = controller(config) { null }

        control.refresh()
        control.onSyncPressed()

        assertEquals(null, config.folder)
        assertTrue(control.state.value.statusMessage == null)
        sync.shutdown()
    }

    @Test
    fun laEtiquetaDelBotonMuestraLaCarpetaCuandoHayCopia() {
        val folder = temp.newFolder().toPath()
        val config = BackupConfig(MapSettings()).also { it.folder = folder.toString() }
        val (control, sync) = controller(config)

        control.refresh()
        // Sin ZIP todavía: etiqueta genérica de sync.
        assertEquals(
            com.plyr.desktop.i18n.Translations.get("sync"),
            control.buttonLabel(control.state.value),
        )

        Files.write(BackupFolder.backupFile(folder), "no importa".toByteArray())
        control.refresh()
        assertTrue(control.buttonLabel(control.state.value).contains(folder.fileName.toString()))
        sync.shutdown()
    }

    private suspend fun awaitState(
        controller: BackupController,
        predicate: (BackupState) -> Boolean,
    ): BackupState = withTimeout(5_000) { controller.state.first(predicate) }

    private companion object {
        const val TIME = 1_700_000_000_000L
    }
}
