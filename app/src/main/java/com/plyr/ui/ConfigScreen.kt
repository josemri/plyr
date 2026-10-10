package com.plyr.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.PlyrApp
import com.plyr.utils.Config
import com.plyr.utils.SpotifyImporter
import com.plyr.utils.Translations
import com.plyr.viewmodel.ConfigViewModel
import com.plyr.viewmodel.ImportViewModel
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import kotlinx.coroutines.delay

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    importViewModel: ImportViewModel? = null
) {
    val appContext = context.applicationContext
    val viewModel = remember {
        ConfigViewModel(appContext, (appContext as PlyrApp).backgroundScope)
    }

    LaunchedEffect(viewModel.selectedTheme) {
        onThemeChanged(viewModel.selectedTheme)
    }

    val dimensions = calculateResponsiveDimensionsFallback()

    BackHandler { onBack() }

    key(viewModel.selectedLanguage) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensions.screenPadding)
        ) {
            Titulo(Translations.get(context, "config_title"))

            Spacer(modifier = Modifier.height(dimensions.itemSpacing))

            // Theme
            ThemeSettingRow(
                context = context,
                selectedTheme = viewModel.selectedTheme,
                onThemeSelected = { viewModel.selectTheme(it) }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Language
            LanguageSettingRow(
                context = context,
                selectedLanguage = viewModel.selectedLanguage,
                onLanguageSelected = { viewModel.selectLanguage(it) }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Gestures
            GesturesSection(context = context, viewModel = viewModel)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Spotify Import
            SpotifyImportSection(importViewModel = importViewModel)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Sync
            SyncSection(context = context, viewModel = viewModel)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            ShareAppSection(context = context, viewModel = viewModel)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
        }
    }
}

@Composable
private fun ThemeSettingRow(
    context: Context,
    selectedTheme: String,
    onThemeSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    SettingRow(
        title = Translations.get(context, "theme"),
        options = listOf(
            Translations.get(context, "theme_system"),
            Translations.get(context, "theme_dark"),
            Translations.get(context, "theme_light"),
            Translations.get(context, "theme_auto")
        ),
        selectedIndex = themeIndex(selectedTheme),
        onSelected = { idx ->
            onThemeSelected(themeAt(idx))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    )
}

@Composable
private fun LanguageSettingRow(
    context: Context,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    SettingRow(
        title = Translations.get(context, "language"),
        options = listOf(
            Translations.get(context, "lang_spanish"),
            Translations.get(context, "lang_english"),
            Translations.get(context, "lang_catalan"),
            Translations.get(context, "lang_japanese")
        ),
        selectedIndex = languageIndex(selectedLanguage),
        onSelected = { idx ->
            onLanguageSelected(languageAt(idx))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    )
}

private fun themeIndex(theme: String): Int = when (theme) {
    "system" -> 0
    "dark" -> 1
    "light" -> 2
    "auto" -> 3
    else -> 0
}

private fun themeAt(index: Int): String = when (index) {
    0 -> "system"
    1 -> "dark"
    2 -> "light"
    3 -> "auto"
    else -> "system"
}

private fun languageIndex(language: String): Int = when (language) {
    Config.LANGUAGE_SPANISH -> 0
    Config.LANGUAGE_ENGLISH -> 1
    Config.LANGUAGE_CATALAN -> 2
    Config.LANGUAGE_JAPANESE -> 3
    else -> 0
}

private fun languageAt(index: Int): String = when (index) {
    0 -> Config.LANGUAGE_SPANISH
    1 -> Config.LANGUAGE_ENGLISH
    2 -> Config.LANGUAGE_CATALAN
    3 -> Config.LANGUAGE_JAPANESE
    else -> Config.LANGUAGE_SPANISH
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
private fun GesturesSection(context: Context, viewModel: ConfigViewModel) {
    SwipeActionSettingRow(
        context = context,
        titleKey = "swipe_left",
        selectedAction = viewModel.swipeLeftAction,
        defaultIndex = 0,
        defaultAction = Config.SWIPE_ACTION_ADD_TO_QUEUE,
        onActionSelected = { viewModel.selectSwipeLeft(it) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    SwipeActionSettingRow(
        context = context,
        titleKey = "swipe_right",
        selectedAction = viewModel.swipeRightAction,
        defaultIndex = 1,
        defaultAction = Config.SWIPE_ACTION_ADD_TO_LIKED,
        onActionSelected = { viewModel.selectSwipeRight(it) }
    )
}

@Composable
private fun SwipeActionSettingRow(
    context: Context,
    titleKey: String,
    selectedAction: String,
    defaultIndex: Int,
    defaultAction: String,
    onActionSelected: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    SettingRow(
        title = Translations.get(context, titleKey),
        options = listOf(
            Translations.get(context, "swipe_action_queue"),
            Translations.get(context, "swipe_action_liked"),
            Translations.get(context, "swipe_action_playlist"),
            Translations.get(context, "swipe_action_share")
        ),
        selectedIndex = swipeActionIndex(selectedAction, defaultIndex),
        onSelected = { idx ->
            onActionSelected(swipeActionAt(idx, defaultAction))
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    )
}

private fun swipeActionIndex(action: String, defaultIndex: Int): Int = when (action) {
    Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
    Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
    Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
    Config.SWIPE_ACTION_SHARE -> 3
    else -> defaultIndex
}

private fun swipeActionAt(index: Int, defaultAction: String): String = when (index) {
    0 -> Config.SWIPE_ACTION_ADD_TO_QUEUE
    1 -> Config.SWIPE_ACTION_ADD_TO_LIKED
    2 -> Config.SWIPE_ACTION_ADD_TO_PLAYLIST
    3 -> Config.SWIPE_ACTION_SHARE
    else -> defaultAction
}

@Composable
private fun SpotifyImportSection(importViewModel: ImportViewModel? = null) {
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
            SpotifyImportProgress(message = message, progress = progress)
        } else if (resultMessage != null) {
            SpotifyImportResult(importResult = resultMessage ?: "")
        } else {
            SpotifyUrlField(
                playlistUrl = playlistUrl,
                onUrlChange = { playlistUrl = it },
                onGo = { startImport() }
            )
        }
    }
}

@Composable
private fun SpotifyImportProgress(message: String, progress: Float) {
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
}

@Composable
private fun SpotifyImportResult(importResult: String) {
    Text(
        text = importResult,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (importResult.startsWith("error"))
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SpotifyUrlField(
    playlistUrl: String,
    onUrlChange: (String) -> Unit,
    onGo: () -> Unit
) {
    OutlinedTextField(
        value = playlistUrl,
        onValueChange = onUrlChange,
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
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    )
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
 * El estado y la lógica viven en [ConfigViewModel] (B23); aquí solo se pinta.
 */
@Composable
private fun SyncSection(context: Context, viewModel: ConfigViewModel) {
    val haptic = LocalHapticFeedback.current
    // Locale observable de Compose (lint: NonObservableLocale); lo usa el
    // etiquetado del botón y el formato de los textos de sync.
    val locale = LocalConfiguration.current.locales[0]

    // Resolver el nombre de la carpeta y comprobar que el ZIP sigue ahí son
    // consultas al proveedor de documentos: con Drive es una llamada de red, así
    // que va fuera de la composición o congelaría la pantalla al abrir los ajustes.
    LaunchedEffect(viewModel.treeUri) {
        viewModel.refreshBackupFolder()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { selected ->
        if (selected == null) return@rememberLauncherForActivityResult
        viewModel.onFolderSelected(selected, locale)
    }

    DataActionRow(
        context = context,
        label = viewModel.syncButtonLabel(locale),
        workingKey = "sync_working",
        isWorking = viewModel.isSyncing,
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (viewModel.isSyncing) return@DataActionRow

            if (viewModel.backupTargetExists()) {
                viewModel.startSync(locale)
            } else {
                // O no hay carpeta, o el archivo ya no está donde se esperaba.
                // En ambos casos solo el usuario puede decir dónde escribir.
                folderLauncher.launch(null)
            }
        }
    )

    SyncStatusMessage(message = viewModel.statusMessage, isError = viewModel.statusIsError)
}

@Composable
private fun SyncStatusMessage(message: String?, isError: Boolean) {
    if (message == null) return

    val dimensions = calculateResponsiveDimensionsFallback()
    Spacer(modifier = Modifier.height(dimensions.itemSpacing))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (isError) {
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
fun ShareAppSection(context: Context, viewModel: ConfigViewModel) {
    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()

    if (viewModel.showShareDialog) {
        com.plyr.ui.components.ShareDialog(
            item = com.plyr.ui.components.ShareableItem(
                remoteId = null,
                shareUrl = null,
                youtubeId = null,
                title = "plyr",
                artist = "",
                type = com.plyr.ui.components.ShareType.APP
            ),
            onDismiss = { viewModel.setShareDialog(false) }
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
                    viewModel.setShareDialog(true)
                }
                .padding(vertical = dimensions.itemSpacing, horizontal = dimensions.contentPadding)
        )
    }
}
