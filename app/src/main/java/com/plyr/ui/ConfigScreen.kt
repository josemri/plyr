package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import com.plyr.utils.Config
import com.plyr.utils.SpotifyImporter
import com.plyr.utils.Translations
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import kotlinx.coroutines.launch

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
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
            SpotifyImportSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Update status
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
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
private fun SpotifyImportSection(context: Context) {
    var isImporting by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var playlistUrl by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    if (isImporting) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "spotify",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                )
            },
            text = {
                Column {
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { }) {
                    Text(text = "...", fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    if (resultMessage != null) {
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "spotify",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                )
            },
            text = {
                Text(
                    text = resultMessage!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (resultMessage!!.startsWith("error"))
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) {
                    Text(text = "ok", fontFamily = FontFamily.Monospace)
                }
            }
        )
    }

    fun startImport() {
        val id = SpotifyImporter.extractPlaylistId(playlistUrl) ?: return
        focusManager.clearFocus()
        isImporting = true
        progressMessage = ""
        resultMessage = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val result = SpotifyImporter.importPlaylistByUri(
                context = context,
                playlistUri = id,
                onProgress = { progressMessage = it }
            )
            isImporting = false
            resultMessage = result.getOrElse { e -> "error: ${e.message}" }
            playlistUrl = ""
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
