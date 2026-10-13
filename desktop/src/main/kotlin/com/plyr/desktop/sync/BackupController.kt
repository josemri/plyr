package com.plyr.desktop.sync

import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.FileDialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

/** Cuánto tiempo se queda el mensaje de estado en pantalla antes de apagarse. */
private const val STATUS_TIMEOUT_MS = 6_000L

/** Estado de la sección de copia de seguridad de los ajustes. */
data class BackupState(
    val isSyncing: Boolean = false,
    val isConfigured: Boolean = false,
    val hasBackupFile: Boolean = false,
    val folderName: String? = null,
    val folderPath: String? = null,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
)

/**
 * Lógica de la sección de copia de seguridad del escritorio (M10).
 *
 * Es el equivalente de `ConfigViewModel` (SyncSection) de Android: resuelve qué
 * hacer al pulsar el botón —elegir carpeta si no hay copia, sincronizar si la
 * hay— y traduce el [SyncResult] a un mensaje. El selector de carpeta se inyecta
 * para poder testear la sección sin abrir un diálogo nativo.
 */
class BackupController(
    private val sync: DataSync,
    private val scope: CoroutineScope,
    private val chooseFolder: (String) -> Path? = { title ->
        FileDialogs.chooseFolder(title)?.toPath()
    },
) {

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    private var statusResetJob: Job? = null

    /**
     * Relee el estado de la carpeta (nombre y si el ZIP sigue ahí). Barato en
     * escritorio, pero se llama fuera de la composición por coherencia con
     * Android, donde resolver la carpeta es una llamada de red.
     */
    fun refresh() {
        val folder = sync.folder()
        val hasFile = folder?.let { Files.isRegularFile(BackupFolder.backupFile(it)) } ?: false
        _state.update {
            it.copy(
                isConfigured = sync.isConfigured,
                hasBackupFile = hasFile,
                folderName = folder?.fileName?.toString(),
                folderPath = folder?.toString(),
            )
        }
    }

    /**
     * El botón hace una de dos cosas: si ya hay copia, sincroniza; si no, pide
     * carpeta (instalación nueva, o el ZIP se borró a mano).
     */
    fun onSyncPressed() {
        if (_state.value.isSyncing) return
        if (_state.value.hasBackupFile) syncNow() else chooseFolderAndSync()
    }

    /**
     * El usuario eligió carpeta. Elegir carpeta y sincronizar es una sola acción:
     * no tiene sentido pedirla y dejar el archivo sin crear.
     */
    fun chooseFolderAndSync() {
        val folder = chooseFolder(Translations.get("backup_choose_folder")) ?: return
        sync.setFolder(folder)
        // La carpeta ya es conocida: se refleja de inmediato para que la
        // etiqueta/estado la muestren aunque el volcado tarde un poco.
        refresh()
        syncNow()
    }

    /** Sincroniza en segundo plano, sin cortar si se sale de la pantalla. */
    fun syncNow() {
        if (_state.value.isSyncing) return
        scope.launch {
            _state.update { it.copy(isSyncing = true) }
            val result = sync.flush(force = true)
            val outcome = outcomeFor(result)
            _state.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = outcome.message,
                    statusIsError = outcome.isError,
                )
            }
            if (outcome.markBackupPresent) refresh()
            scheduleStatusClear()
        }
    }

    /** Etiqueta del botón: `< sync >` sin copia, o `synced w/ <carpeta>`. */
    fun buttonLabel(state: BackupState): String =
        if (state.hasBackupFile) {
            Translations.get("sync_synced").format(state.folderName ?: BackupFolder.BACKUP_FILE_NAME)
        } else {
            Translations.get("sync")
        }

    private fun scheduleStatusClear() {
        statusResetJob?.cancel()
        statusResetJob = scope.launch {
            delay(STATUS_TIMEOUT_MS)
            _state.update { it.copy(statusMessage = null) }
        }
    }

    private fun outcomeFor(result: SyncResult): SyncOutcome = when (result) {
        is SyncResult.Written -> {
            val copied = Translations.get("sync_done")
                .format(result.summary.playlistCount, result.summary.trackCount)
            val recovered = result.merged?.let { merged ->
                Translations.get("sync_merged")
                    .format(merged.importedPlaylists, merged.mergedLikedTracks, merged.deletedPlaylists)
            }
            SyncOutcome(
                message = listOfNotNull(recovered, copied).joinToString(" "),
                isError = false,
                markBackupPresent = true,
            )
        }
        SyncResult.UpToDate -> SyncOutcome(
            message = Translations.get("sync_empty"),
            isError = false,
            markBackupPresent = false,
        )
        SyncResult.NotConfigured -> SyncOutcome(
            message = Translations.get("sync_need_folder"),
            isError = true,
            markBackupPresent = false,
        )
        is SyncResult.ArchiveUnreadable -> SyncOutcome(
            message = Translations.get("sync_archive_unreadable"),
            isError = true,
            markBackupPresent = false,
        )
        is SyncResult.Failed -> SyncOutcome(
            message = Translations.get("sync_error"),
            isError = true,
            markBackupPresent = false,
        )
    }

    private data class SyncOutcome(
        val message: String?,
        val isError: Boolean,
        val markBackupPresent: Boolean,
    )
}
