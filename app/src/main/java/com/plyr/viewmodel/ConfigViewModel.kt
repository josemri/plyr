package com.plyr.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.plyr.utils.BackupConfig
import com.plyr.utils.BackupFolder
import com.plyr.utils.Config
import com.plyr.utils.DataSync
import com.plyr.utils.SwipeConfig
import com.plyr.utils.SyncResult
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Cuánto se deja visible el mensaje de resultado del sync. */
private const val RESULT_TIMEOUT_MS = 4000L

/**
 * Estado y orquestación de la pantalla de ajustes.
 *
 * Extraído de `ConfigScreen.kt`: el estado (tema, idioma, gestos, sync, compartir)
 * es "Compose state" y la lógica de sincronización vive aquí, no en la composición.
 * La copia de seguridad corre en el `backgroundScope` de la aplicación (B23) para
 * que salir de Ajustes con un swipe no la corte a medias.
 */
@Stable
class ConfigViewModel(
    private val context: Context,
    private val backgroundScope: CoroutineScope,
) {
    var selectedTheme by mutableStateOf(Config.getTheme(context))
        private set
    var selectedLanguage by mutableStateOf(Config.getLanguage(context))
        private set
    var swipeLeftAction by mutableStateOf(SwipeConfig.getSwipeLeftAction(context))
        private set
    var swipeRightAction by mutableStateOf(SwipeConfig.getSwipeRightAction(context))
        private set
    var showShareDialog by mutableStateOf(false)
        private set

    // Cambia al elegir carpeta, para repintar el estado.
    var treeUri by mutableStateOf(BackupConfig.getBackupTreeUri(context))
        private set
    var folderName by mutableStateOf<String?>(null)
        private set
    var hasBackupFile by mutableStateOf(false)
        private set
    var isSyncing by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var statusIsError by mutableStateOf(false)
        private set

    private var statusResetJob: Job? = null

    fun selectTheme(theme: String) {
        Config.setTheme(context, theme)
        selectedTheme = theme
    }

    fun selectLanguage(language: String) {
        Config.setLanguage(context, language)
        selectedLanguage = language
    }

    fun selectSwipeLeft(action: String) {
        SwipeConfig.setSwipeLeftAction(context, action)
        swipeLeftAction = action
    }

    fun selectSwipeRight(action: String) {
        SwipeConfig.setSwipeRightAction(context, action)
        swipeRightAction = action
    }

    fun setShareDialog(show: Boolean) {
        showShareDialog = show
    }

    /**
     * Resolver el nombre de la carpeta y comprobar que el ZIP sigue ahí son
     * consultas al proveedor de documentos: con Drive es una llamada de red, así
     * que va fuera de la composición o congelaría la pantalla al abrir los ajustes.
     */
    suspend fun refreshBackupFolder() {
        val info = resolveBackupFolder(context, treeUri)
        folderName = info.folderName
        hasBackupFile = info.hasBackupFile
    }

    /** Etiqueta del botón de sync: `< sync >` sin copia, o `synced w/ <carpeta>`. */
    fun syncButtonLabel(locale: Locale): String =
        buildSyncButtonLabel(context, locale, hasBackupFile, folderName)

    /** ¿El ZIP ya está donde se esperaba? Si no, hay que pedir carpeta al usuario. */
    fun backupTargetExists(): Boolean = existingBackupTarget(context, treeUri) != null

    /** Sincroniza en segundo plano (no corta si se sale de la pantalla, B23). */
    fun startSync(locale: Locale) {
        backgroundScope.launch { sync(locale) }
    }

    /**
     * El usuario eligió carpeta. Elegir carpeta y sincronizar es una sola acción:
     * no tiene sentido pedirla y dejar el archivo sin crear.
     */
    fun onFolderSelected(selected: Uri, locale: Locale) {
        backgroundScope.launch {
            val selection = selectBackupFolder(context, selected, treeUri)
            if (selection.accessDenied) {
                showStatus(Translations.get(context, "sync_folder_denied"), isError = true)
                return@launch
            }

            treeUri = selection.treeUri
            sync(locale)
        }
    }

    private suspend fun sync(locale: Locale) {
        isSyncing = true
        // "force" solo salta la comparación de huellas, no la falta de listas:
        // sin listas no hay nada que copiar.
        val outcome = buildSyncOutcome(context, locale, DataSync.flush(context, force = true))
        showStatus(outcome.message, outcome.isError)
        if (outcome.markBackupPresent) {
            // El botón pasa a indicar que ya hay copia. El nombre se vuelve a
            // resolver porque puede ser una carpeta nueva.
            val tree = treeUri?.let { it.toUri() }
            folderName = tree?.let { loadFolderName(context, it) }
            hasBackupFile = true
        }
        isSyncing = false
    }

    /** Un solo mensaje para la sección, que se apaga solo tras un rato. */
    private fun showStatus(message: String?, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
        statusResetJob?.cancel()
        if (message != null) {
            statusResetJob = backgroundScope.launch {
                delay(RESULT_TIMEOUT_MS)
                statusMessage = null
            }
        }
    }
}

private data class BackupFolderInfo(val folderName: String?, val hasBackupFile: Boolean)

private suspend fun resolveBackupFolder(context: Context, treeUri: String?): BackupFolderInfo {
    val tree = treeUri?.let { it.toUri() }
        ?: return BackupFolderInfo(folderName = null, hasBackupFile = false)
    val folderName = loadFolderName(context, tree)
    val hasBackupFile = runCatching {
        BackupFolder.findExistingBackupFile(context, tree) != null
    }.getOrDefault(false)
    return BackupFolderInfo(folderName = folderName, hasBackupFile = hasBackupFile)
}

private data class FolderSelection(val treeUri: String?, val accessDenied: Boolean)

private fun selectBackupFolder(
    context: Context,
    selected: Uri,
    previousTreeUri: String?
): FolderSelection {
    // Sin este permiso el acceso se pierde al reiniciar y la copia
    // automática solo funcionaría hasta que apagues el móvil.
    if (!BackupFolder.persistAccess(context, selected)) {
        return FolderSelection(treeUri = previousTreeUri, accessDenied = true)
    }

    // Se suelta la carpeta anterior: dejar permisos huérfanos en el
    // sistema solo ocupa cuota y confunde al usuario.
    previousTreeUri?.takeIf { it != selected.toString() }?.let { previous ->
        runCatching { BackupFolder.releaseAccess(context, previous.toUri()) }
    }

    BackupConfig.setBackupTree(context, selected.toString(), documentId = null)
    return FolderSelection(treeUri = selected.toString(), accessDenied = false)
}

private fun existingBackupTarget(context: Context, treeUri: String?): Uri? =
    treeUri?.let {
        runCatching { BackupFolder.findExistingBackupFile(context, it.toUri()) }.getOrNull()
    }

private data class SyncOutcome(
    val message: String?,
    val isError: Boolean,
    val markBackupPresent: Boolean
)

private fun buildSyncOutcome(context: Context, locale: Locale, result: SyncResult): SyncOutcome = when (result) {
    is SyncResult.Written -> {
        val copiadas = Translations.get(context, "sync_done")
            .format(locale, result.summary.playlistCount, result.summary.trackCount)
        val recuperadas = result.merged
            ?.let { merged ->
                Translations.get(context, "sync_merged")
                    .format(
                        locale,
                        merged.importedPlaylists, merged.mergedLikedTracks, merged.deletedPlaylists
                    )
            }
        SyncOutcome(
            message = listOfNotNull(recuperadas, copiadas).joinToString(" "),
            isError = false,
            markBackupPresent = true
        )
    }
    SyncResult.UpToDate -> SyncOutcome(
        message = Translations.get(context, "sync_empty"),
        isError = false,
        markBackupPresent = false
    )
    SyncResult.NotConfigured -> SyncOutcome(
        message = Translations.get(context, "sync_need_folder"),
        isError = true,
        markBackupPresent = false
    )
    is SyncResult.ArchiveUnreadable -> SyncOutcome(
        message = Translations.get(context, "sync_archive_unreadable"),
        isError = true,
        markBackupPresent = false
    )
    is SyncResult.Failed -> SyncOutcome(
        message = Translations.get(context, "sync_error"),
        isError = true,
        markBackupPresent = false
    )
}

private fun buildSyncButtonLabel(
    context: Context,
    locale: Locale,
    hasBackupFile: Boolean,
    folderName: String?
): String = if (hasBackupFile) {
    Translations.get(context, "sync_synced")
        .format(locale, folderName ?: BackupFolder.BACKUP_FILE_NAME)
} else {
    Translations.get(context, "sync")
}

/**
 * Nombre de la **última** carpeta de [treeUri] ("plyr" de "primary:Download/plyr"),
 * o null si no hay forma de averiguarlo.
 *
 * Hace una consulta al `DocumentsProvider`, que con Drive es una llamada de red:
 * nunca llamar a esto desde la composición. El respaldo es el último segmento
 * del `documentId`, nunca el Uri entero, porque en el botón solo cabe el nombre
 * de la carpeta: una ruta completa lo deja ilegible.
 */
private suspend fun loadFolderName(context: Context, treeUri: Uri): String? = withContext(Dispatchers.IO) {
    val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
        .getOrNull()
        ?.substringAfterLast(':')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }

    val displayName = try {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            ),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    displayName?.trim()?.takeIf { it.isNotBlank() } ?: treeDocumentId
}
