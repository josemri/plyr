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
import com.plyr.PlyrApp
import com.plyr.utils.BackupFolder
import com.plyr.utils.Config
import com.plyr.utils.DataSync
import com.plyr.utils.SpotifyImporter
import com.plyr.utils.SyncResult
import com.plyr.utils.Translations
import com.plyr.viewmodel.ImportViewModel
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cuánto se deja visible el mensaje de resultado del sync. */
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

            // Sync
            SyncSection(context = context)

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

/**
 * Sync - Un único botón para todo lo relacionado con la copia de seguridad.
 *
 * Mantiene `plyr-sync.zip` al día dentro de una carpeta que el usuario eligió
 * una vez. Sustituye a los antiguos botones de exportar, importar y copiar: no
 * hay nada que elegir más que la carpeta, y el resto ya pasa solo.
 *
 * El botón hace una de dos cosas según el estado, sin preguntar nada:
 *
 * - **No hay carpeta guardada**, o el archivo ya no está en ella: se abre el
 *   selector para elegirla. Es el caso de una instalación nueva, y también del
 *   usuario que borró el ZIP a mano desde Drive.
 * - **El archivo está donde se esperaba**: sincroniza y listo.
 *
 * El botón también dice en qué estado está: `< sync >` mientras no haya un ZIP
 * escrito, y `synced w/ <carpeta>` cuando ya lo hay, con el nombre de la última
 * carpeta de la ruta, no la ruta entera.
 *
 * Al margen de este botón, la app ya sola: marca los cambios y los vuelca al
 * salir (`DataSync.flushOnStop`).
 */
@Composable
private fun SyncSection(context: Context) {
    val haptic = LocalHapticFeedback.current
    // Scope de aplicación (B23): si el usuario sale de Ajustes con un swipe
    // mientras se sincroniza, la escritura del ZIP no debe cortarse a medias.
    val coroutineScope = remember { (context.applicationContext as PlyrApp).backgroundScope }
    val dimensions = calculateResponsiveDimensionsFallback()
    var isSyncing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    // Cambia al elegir carpeta, para repintar el estado.
    var treeUri by remember { mutableStateOf(Config.getBackupTreeUri(context)) }

    // Resolver el nombre de la carpeta y comprobar que el ZIP sigue ahí son
    // consultas al proveedor de documentos: con Drive es una llamada de red, así
    // que va fuera de la composición o congelaría la pantalla al abrir los
    // ajustes.
    var folderName by remember { mutableStateOf<String?>(null) }
    var hasBackupFile by remember { mutableStateOf(false) }
    LaunchedEffect(treeUri) {
        val tree = treeUri?.let { Uri.parse(it) }
        folderName = tree?.let { loadFolderName(context, it) }
        hasBackupFile = tree != null && runCatching {
            BackupFolder.findExistingBackupFile(context, tree) != null
        }.getOrDefault(false)
    }

    // Un solo mensaje para la sección, en vez de uno por botón.
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(RESULT_TIMEOUT_MS)
            statusMessage = null
        }
    }

    suspend fun syncNow() {
        isSyncing = true
        when (val result = DataSync.flush(context, force = true)) {
            is SyncResult.Written -> {
                statusIsError = false
                val copiadas = Translations.get(context, "sync_done")
                    .format(result.summary.playlistCount, result.summary.trackCount)
                val recuperadas = result.merged
                    ?.let { merged ->
                        Translations.get(context, "sync_merged")
                            .format(merged.importedPlaylists, merged.mergedLikedTracks, merged.deletedPlaylists)
                    }
                statusMessage = listOfNotNull(recuperadas, copiadas).joinToString(" ")
                // El botón pasa a indicar que ya hay copia. El nombre se vuelve
                // a resolver porque puede ser una carpeta nueva.
                val tree = treeUri?.let { Uri.parse(it) }
                folderName = tree?.let { loadFolderName(context, it) }
                hasBackupFile = true
            }
            // "force" solo salta la comparación de huellas, no la falta de
            // listas: sin listas no hay nada que copiar. El estado del botón no
            // se toca, porque no se ha escrito nada.
            SyncResult.UpToDate -> {
                statusIsError = false
                statusMessage = Translations.get(context, "sync_empty")
            }
            SyncResult.NotConfigured -> {
                statusIsError = true
                statusMessage = Translations.get(context, "sync_need_folder")
            }
            is SyncResult.ArchiveUnreadable -> {
                statusIsError = true
                statusMessage = Translations.get(context, "sync_archive_unreadable")
            }
            is SyncResult.Failed -> {
                statusIsError = true
                statusMessage = Translations.get(context, "sync_error")
            }
        }
        isSyncing = false
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { selected ->
        if (selected == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            // Sin este permiso el acceso se pierde al reiniciar y la copia
            // automática solo funcionaría hasta que apagues el móvil.
            if (!BackupFolder.persistAccess(context, selected)) {
                statusIsError = true
                statusMessage = Translations.get(context, "sync_folder_denied")
                return@launch
            }

            // Se suelta la carpeta anterior: dejar permisos huérfanos en el
            // sistema solo ocupa cuota y confunde al usuario.
            treeUri?.takeIf { it != selected.toString() }?.let { previous ->
                runCatching { BackupFolder.releaseAccess(context, Uri.parse(previous)) }
            }

            Config.setBackupTree(context, selected.toString(), documentId = null)
            treeUri = selected.toString()

            // Elegir carpeta y sincronizar es una sola acción: no tiene
            // sentido pedirla y dejar el archivo sin crear.
            syncNow()
        }
    }

    // El estado vive en la propia etiqueta del botón: sin ZIP escrito pone
    // "< sync >", y con ZIP puesto el nombre de la carpeta donde vive. Si el
    // proveedor no resuelve el nombre, se muestra el del archivo, que al menos
    // dice dónde está la copia.
    val syncLabel = if (hasBackupFile) {
        Translations.get(context, "sync_synced")
            .format(folderName ?: BackupFolder.BACKUP_FILE_NAME)
    } else {
        Translations.get(context, "sync")
    }

    DataActionRow(
        context = context,
        label = syncLabel,
        workingKey = "sync_working",
        isWorking = isSyncing,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (isSyncing) return@DataActionRow

            val known = treeUri
            val target = known?.let {
                runCatching { BackupFolder.findExistingBackupFile(context, Uri.parse(it)) }.getOrNull()
            }

            if (target == null) {
                // O no hay carpeta, o el archivo ya no está donde se esperaba.
                // En ambos casos solo el usuario puede decir dónde escribir.
                folderLauncher.launch(null)
            } else {
                coroutineScope.launch { syncNow() }
            }
        }
    )

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
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
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

/**
 * Fila de acción de ajustes con dos estados: lista para pulsar y trabajando.
 * Comparte con [SyncSection] tanto el estilo como el indicador de progreso.
 */
@Composable
private fun DataActionRow(
    context: Context,
    label: String,
    workingKey: String,
    isWorking: Boolean,
    onClick: () -> Unit
) {
    val dimensions = calculateResponsiveDimensionsFallback()

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
