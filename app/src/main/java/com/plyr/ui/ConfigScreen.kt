package com.plyr.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.utils.BackupFolder
import com.plyr.utils.Config
import com.plyr.utils.DataExporter
import com.plyr.utils.DataImporter
import com.plyr.utils.DataSync
import com.plyr.utils.EmptyExportException
import com.plyr.utils.ExportManifest
import com.plyr.utils.ImportSummary
import com.plyr.utils.ManifestFormatException
import com.plyr.utils.SpotifyImporter
import com.plyr.utils.SyncResult
import com.plyr.utils.Translations
import com.plyr.utils.getPackageInfoCompat
import com.plyr.viewmodel.ImportViewModel
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Subtitulo
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cuánto se deja visible el mensaje de resultado de importar/exportar datos. */
private const val RESULT_TIMEOUT_MS = 4000L

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    importViewModel: ImportViewModel? = null
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var selectedLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    var updateInfo by remember { mutableStateOf<com.plyr.utils.UpdateChecker.UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val info = com.plyr.utils.UpdateChecker.checkForUpdate(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                updateInfo = info
            }
        }
    }

    LaunchedEffect(selectedTheme) {
        Config.setTheme(context, selectedTheme)
        onThemeChanged(selectedTheme)
    }

    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()

    BackHandler { onBack() }

    key(selectedLanguage) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensions.screenPadding)
        ) {
            Titulo(Translations.get(context, "config_title"))

            Spacer(modifier = Modifier.height(dimensions.itemSpacing))

            // Theme
            SettingRow(
                title = Translations.get(context, "theme"),
                options = listOf(
                    Translations.get(context, "theme_system"),
                    Translations.get(context, "theme_dark"),
                    Translations.get(context, "theme_light"),
                    Translations.get(context, "theme_auto")
                ),
                selectedIndex = when (selectedTheme) {
                    "system" -> 0
                    "dark" -> 1
                    "light" -> 2
                    "auto" -> 3
                    else -> 0
                },
                onSelected = { idx ->
                    selectedTheme = when (idx) {
                        0 -> "system"
                        1 -> "dark"
                        2 -> "light"
                        3 -> "auto"
                        else -> "system"
                    }
                    onThemeChanged(selectedTheme)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Language
            SettingRow(
                title = Translations.get(context, "language"),
                options = listOf(
                    Translations.get(context, "lang_spanish"),
                    Translations.get(context, "lang_english"),
                    Translations.get(context, "lang_catalan"),
                    Translations.get(context, "lang_japanese")
                ),
                selectedIndex = when (selectedLanguage) {
                    Config.LANGUAGE_SPANISH -> 0
                    Config.LANGUAGE_ENGLISH -> 1
                    Config.LANGUAGE_CATALAN -> 2
                    Config.LANGUAGE_JAPANESE -> 3
                    else -> 0
                },
                onSelected = { idx ->
                    val newLang = when (idx) {
                        0 -> Config.LANGUAGE_SPANISH
                        1 -> Config.LANGUAGE_ENGLISH
                        2 -> Config.LANGUAGE_CATALAN
                        3 -> Config.LANGUAGE_JAPANESE
                        else -> Config.LANGUAGE_SPANISH
                    }
                    Config.setLanguage(context, newLang)
                    selectedLanguage = newLang
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Gestures
            GesturesSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Spotify Import
            SpotifyImportSection(context = context, importViewModel = importViewModel)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Export data
            ExportDataSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Import data
            ImportDataSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Copia de seguridad automática
            AutoBackupSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Update status
            val currentVersion = try {
                context.packageManager.getPackageInfoCompat(context.packageName).versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = updateInfo?.let { info ->
                        if (info.isUpdateAvailable) {
                            "● new update available! (v${info.latestVersion})"
                        } else {
                            "● using latest version (v${currentVersion})"
                        }
                    } ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = dimensions.bodySize,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            ShareAppSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        MultiToggle(
            options = options,
            initialIndex = selectedIndex,
            onChange = onSelected
        )
    }
}

@Composable
private fun GesturesSection(context: Context) {
    var selectedSwipeLeftAction by remember { mutableStateOf(Config.getSwipeLeftAction(context)) }
    var selectedSwipeRightAction by remember { mutableStateOf(Config.getSwipeRightAction(context)) }
    val haptic = LocalHapticFeedback.current

    SettingRow(
        title = Translations.get(context, "swipe_left"),
        options = listOf(
            Translations.get(context, "swipe_action_queue"),
            Translations.get(context, "swipe_action_liked"),
            Translations.get(context, "swipe_action_playlist"),
            Translations.get(context, "swipe_action_share")
        ),
        selectedIndex = when (selectedSwipeLeftAction) {
            Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
            Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
            Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
            Config.SWIPE_ACTION_SHARE -> 3
            else -> 0
        },
        onSelected = { idx ->
            selectedSwipeLeftAction = when (idx) {
                0 -> Config.SWIPE_ACTION_ADD_TO_QUEUE
                1 -> Config.SWIPE_ACTION_ADD_TO_LIKED
                2 -> Config.SWIPE_ACTION_ADD_TO_PLAYLIST
                3 -> Config.SWIPE_ACTION_SHARE
                else -> Config.SWIPE_ACTION_ADD_TO_QUEUE
            }
            Config.setSwipeLeftAction(context, selectedSwipeLeftAction)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    )

    Spacer(modifier = Modifier.height(8.dp))

    SettingRow(
        title = Translations.get(context, "swipe_right"),
        options = listOf(
            Translations.get(context, "swipe_action_queue"),
            Translations.get(context, "swipe_action_liked"),
            Translations.get(context, "swipe_action_playlist"),
            Translations.get(context, "swipe_action_share")
        ),
        selectedIndex = when (selectedSwipeRightAction) {
            Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
            Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
            Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
            Config.SWIPE_ACTION_SHARE -> 3
            else -> 1
        },
        onSelected = { idx ->
            selectedSwipeRightAction = when (idx) {
                0 -> Config.SWIPE_ACTION_ADD_TO_QUEUE
                1 -> Config.SWIPE_ACTION_ADD_TO_LIKED
                2 -> Config.SWIPE_ACTION_ADD_TO_PLAYLIST
                3 -> Config.SWIPE_ACTION_SHARE
                else -> Config.SWIPE_ACTION_ADD_TO_LIKED
            }
            Config.setSwipeRightAction(context, selectedSwipeRightAction)
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    )
}

@Composable
private fun SpotifyImportSection(context: Context, importViewModel: ImportViewModel? = null) {
    val vm = importViewModel ?: return
    val isImporting by vm.isImporting.collectAsState()
    val progress by vm.progress.collectAsState()
    val message by vm.message.collectAsState()
    val resultMessage by vm.resultMessage.collectAsState()
    var playlistUrl by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(resultMessage) {
        if (resultMessage != null) {
            delay(3000)
            vm.dismissResult()
        }
    }

    fun startImport() {
        val id = SpotifyImporter.extractPlaylistId(playlistUrl) ?: return
        focusManager.clearFocus()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        playlistUrl = ""
        vm.startImport(id)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isImporting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (resultMessage != null) {
            Text(
                text = resultMessage!!,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (resultMessage!!.startsWith("error"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            OutlinedTextField(
                value = playlistUrl,
                onValueChange = { playlistUrl = it },
                placeholder = {
                    Text(
                        text = "spotify playlist url or id",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { startImport() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun ExportDataSection(context: Context) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportManifest.ZIP_MIME_TYPE)
    ) { uri ->
        if (uri == null || isExporting) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isExporting = true
            statusMessage = null
            DataExporter.exportTo(context, uri).fold(
                onSuccess = { summary ->
                    statusIsError = false
                    statusMessage = Translations.get(context, "export_data_done")
                        .format(summary.playlistCount, summary.trackCount)
                },
                onFailure = { error ->
                    statusIsError = true
                    statusMessage = Translations.get(
                        context,
                        if (error is EmptyExportException) "export_data_empty" else "export_data_error"
                    )
                }
            )
            isExporting = false
        }
    }

    DataActionRow(
        context = context,
        label = Translations.get(context, "export_data"),
        workingKey = "export_data_working",
        isWorking = isExporting,
        statusText = statusMessage,
        isError = statusIsError,
        onStatusCleared = { statusMessage = null },
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            createDocumentLauncher.launch(ExportManifest.suggestedFileName())
        }
    )
}

@Composable
private fun ImportDataSection(context: Context) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || isImporting) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            isImporting = true
            statusMessage = null
            DataImporter.importFrom(context, uri).fold(
                onSuccess = { summary ->
                    statusIsError = false
                    statusMessage = importSummaryText(context, summary)
                },
                onFailure = { error ->
                    statusIsError = true
                    statusMessage = Translations.get(
                        context,
                        if (error is ManifestFormatException) {
                            "import_data_bad_file"
                        } else {
                            "import_data_error"
                        }
                    )
                }
            )
            isImporting = false
        }
    }

    DataActionRow(
        context = context,
        label = Translations.get(context, "import_data"),
        workingKey = "import_data_working",
        isWorking = isImporting,
        statusText = statusMessage,
        isError = statusIsError,
        onStatusCleared = { statusMessage = null },
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            openDocumentLauncher.launch(ExportManifest.ZIP_MIME_TYPES)
        }
    )
}

/**
 * Copia de seguridad automática: la app mantiene un único `plyr-sync.zip` al
 * día dentro de una carpeta que el usuario elige una sola vez.
 *
 * Es distinta de [ExportDataSection] y [ImportDataSection] a propósito: en lugar
 * de un ZIP nuevo cada vez que el usuario lo pide, aquí el archivo se reescribe
 * solo. Por eso usa `OpenDocumentTree` (una **carpeta** con permiso
 * persistente) y no `CreateDocument` (un archivo con permiso de un solo uso).
 *
 * Los botones manuales siguen existiendo: se necesitan para hacer un archivo
 * con fecha para compartir, o para importar una copia hecha en otro dispositivo.
 */
@Composable
private fun AutoBackupSection(context: Context) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val dimensions = calculateResponsiveDimensionsFallback()
    var isSyncing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    val treeUriString = remember { mutableStateOf(Config.getBackupTreeUri(context)) }
    var autoSyncOn by remember { mutableStateOf(Config.isAutoSyncEnabled(context)) }

    // Resolver el nombre de la carpeta es una consulta al proveedor de
    // documentos: con Drive es una llamada de red, así que va fuera de la
    // composición o congelaría la pantalla al abrir los ajustes.
    var folderName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(treeUriString.value) {
        folderName = treeUriString.value?.let { loadFolderName(context, Uri.parse(it)) }
    }

    // Un solo mensaje para todas las filas: si cada una guardara el suyo, el
    // resultado de "restaurar" aparecería debajo del botón de "sincronizar".
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(RESULT_TIMEOUT_MS)
            statusMessage = null
        }
    }

    fun report(message: String, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            // Sin este permiso el acceso se pierde al reiniciar y la copia
            // automática solo funcionaría hasta que apagues el móvil.
            if (!BackupFolder.persistAccess(context, treeUri)) {
                report(Translations.get(context, "backup_folder_denied"), isError = true)
                return@launch
            }

            // Se suelta la carpeta anterior: dejar permisos huérfanos en el
            // sistema solo ocupa cuota y confunde al usuario.
            val previous = Config.getBackupTreeUri(context)
            if (previous != null && previous != treeUri.toString()) {
                runCatching { BackupFolder.releaseAccess(context, Uri.parse(previous)) }
            }

            Config.setBackupTree(context, treeUri.toString(), documentId = null)
            treeUriString.value = treeUri.toString()
            autoSyncOn = true
            Config.setAutoSyncEnabled(context, true)
        }
    }

    Subtitulo(Translations.get(context, "backup_section"))

    Spacer(modifier = Modifier.height(dimensions.itemSpacing))

    val folder = treeUriString.value
    if (folder == null) {
        DataActionRow(
            context = context,
            label = Translations.get(context, "backup_pick_folder"),
            workingKey = "backup_pick_folder",
            isWorking = false,
            statusText = null,
            isError = false,
            onStatusCleared = {},
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                folderLauncher.launch(null)
            }
        )
    } else {
        Text(
            text = Translations.get(context, "backup_folder_set")
                .format(folderName ?: folder),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(dimensions.itemSpacing))

        // Interruptor de copia automática
        DataActionRow(
            context = context,
            label = Translations.get(context, "backup_toggle") + if (autoSyncOn) " [x]" else " [ ]",
            workingKey = "backup_syncing",
            isWorking = false,
            statusText = null,
            isError = false,
            onStatusCleared = {},
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val enabled = !autoSyncOn
                autoSyncOn = enabled
                Config.setAutoSyncEnabled(context, enabled)
                report(
                    Translations.get(context, if (enabled) "backup_toggle_on" else "backup_toggle_off"),
                    isError = false
                )
            }
        )

        Spacer(modifier = Modifier.height(dimensions.itemSpacing))

        DataActionRow(
            context = context,
            label = Translations.get(context, "backup_sync_now"),
            workingKey = "backup_syncing",
            isWorking = isSyncing,
            statusText = null,
            isError = false,
            onStatusCleared = {},
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isSyncing) return@DataActionRow
                coroutineScope.launch {
                    isSyncing = true
                    when (val result = DataSync.flush(context, force = true)) {
                        is SyncResult.Written -> report(
                            Translations.get(context, "backup_sync_done")
                                .format(result.summary.playlistCount, result.summary.trackCount),
                            isError = false
                        )
                        // "force" solo salta la comparación de huellas, no la
                        // falta de listas: sin listas no hay nada que copiar.
                        SyncResult.UpToDate -> report(
                            Translations.get(context, "backup_sync_uptodate"), isError = false
                        )
                        SyncResult.NotConfigured -> report(
                            Translations.get(context, "backup_restore_none"), isError = true
                        )
                        is SyncResult.Failed -> report(
                            Translations.get(context, "backup_sync_error"), isError = true
                        )
                    }
                    isSyncing = false
                }
            }
        )

        Spacer(modifier = Modifier.height(dimensions.itemSpacing))

        DataActionRow(
            context = context,
            label = Translations.get(context, "backup_restore"),
            workingKey = "import_data_working",
            isWorking = isRestoring,
            statusText = null,
            isError = false,
            onStatusCleared = {},
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isRestoring) return@DataActionRow
                coroutineScope.launch {
                    isRestoring = true
                    DataSync.importFromBackupFolder(context).fold(
                        onSuccess = { report(importSummaryText(context, it), isError = false) },
                        onFailure = { error ->
                            report(
                                Translations.get(
                                    context,
                                    if (error is ManifestFormatException) {
                                        "import_data_bad_file"
                                    } else {
                                        "import_data_error"
                                    }
                                ),
                                isError = true
                            )
                        }
                    )
                    isRestoring = false
                }
            }
        )

        Spacer(modifier = Modifier.height(dimensions.itemSpacing))

        DataActionRow(
            context = context,
            label = Translations.get(context, "backup_stop"),
            workingKey = "backup_stopped",
            isWorking = false,
            statusText = null,
            isError = false,
            onStatusCleared = {},
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                DataSync.forgetBackupFolder(context)
                treeUriString.value = null
                autoSyncOn = false
                report(Translations.get(context, "backup_stopped"), isError = false)
            }
        )
    }

    // Mensaje único de la sección, debajo de todo lo anterior.
    statusMessage?.let { message ->
        Spacer(modifier = Modifier.height(dimensions.itemSpacing))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

/**
 * Nombre legible de la carpeta elegida, o su Uri entero si el proveedor no lo
 * resuelve. Hace una consulta al `DocumentsProvider`, que con Drive es una
 * llamada de red: nunca llamar a esto desde la composición.
 */
private suspend fun loadFolderName(context: Context, treeUri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: documentId.substringAfterLast(':').ifBlank { treeUri.toString() }
        } catch (e: Exception) {
            treeUri.toString()
        }
    }

/**
 * Mensaje de resumen de una importación. Compartido por el import manual y el
 * que viene de la carpeta, porque cuenta exactamente lo mismo.
 */
private fun importSummaryText(context: Context, summary: ImportSummary): String = buildString {
    append(
        Translations.get(context, "import_data_done")
            .format(summary.importedPlaylists, summary.importedTracks)
    )
    if (summary.mergedLikedTracks > 0 || summary.skippedPlaylists > 0) {
        append(' ')
        append(
            Translations.get(context, "import_data_extra")
                .format(summary.mergedLikedTracks, summary.skippedPlaylists)
        )
    }
}

/**
 * Fila de acción de ajustes con los tres estados que comparten la exportación y
 * la importación: lista para pulsar, trabajando y mensaje de resultado (que se
 * borra solo a los [RESULT_TIMEOUT_MS] para dejar sitio a reintentar).
 */
@Composable
private fun DataActionRow(
    context: Context,
    label: String,
    workingKey: String,
    isWorking: Boolean,
    statusText: String?,
    isError: Boolean,
    onStatusCleared: () -> Unit,
    onClick: () -> Unit
) {
    val dimensions = calculateResponsiveDimensionsFallback()

    LaunchedEffect(statusText) {
        if (statusText != null) {
            delay(RESULT_TIMEOUT_MS)
            onStatusCleared()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isWorking) {
            Text(
                text = Translations.get(context, workingKey),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (statusText != null) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    }
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = dimensions.bodySize,
                    color = MaterialTheme.colorScheme.primary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(vertical = dimensions.itemSpacing, horizontal = dimensions.contentPadding)
            )
        }
    }
}

@Composable
fun ShareAppSection(context: Context) {
    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()
    var showShareDialog by remember { mutableStateOf(false) }

    if (showShareDialog) {
        com.plyr.ui.components.ShareDialog(
            item = com.plyr.ui.components.ShareableItem(
                remoteId = null,
                shareUrl = null,
                youtubeId = null,
                title = "plyr",
                artist = "",
                type = com.plyr.ui.components.ShareType.APP
            ),
            onDismiss = { showShareDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = Translations.get(context, "share_me"),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = dimensions.bodySize,
                color = MaterialTheme.colorScheme.primary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showShareDialog = true
                }
                .padding(vertical = dimensions.itemSpacing, horizontal = dimensions.contentPadding)
        )
    }
}
