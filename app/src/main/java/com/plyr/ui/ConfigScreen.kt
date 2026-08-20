package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.utils.Config
import com.plyr.utils.Translations
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.components.Subtitulo
import com.plyr.ui.components.CollapsibleSection
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import androidx.compose.ui.Alignment

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var selectedLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    // Update checker state
    var updateInfo by remember { mutableStateOf<com.plyr.utils.UpdateChecker.UpdateInfo?>(null) }

    // Check for updates when screen opens
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

    // El idioma se guarda inmediatamente en onLanguageChanged para que Translations.get() 
    // tenga el valor correcto antes de la recomposición

    val haptic = LocalHapticFeedback.current

    // Dimensiones responsivas
    val dimensions = calculateResponsiveDimensionsFallback()

    // Handle back button
    BackHandler {
        onBack()
    }

    // Usar key para forzar la recomposición cuando cambia el idioma
    key(selectedLanguage) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensions.screenPadding)
        ) {
            // Header
            Titulo(Translations.get(context, "config_title"))

            Spacer(modifier = Modifier.height(dimensions.itemSpacing))

            Subtitulo("APPEARANCE")

            // Selector de tema - Desplegable
            ThemeConfigSection(
                context = context,
                selectedTheme = selectedTheme,
                onThemeChanged = { newTheme ->
                    selectedTheme = newTheme
                    onThemeChanged(newTheme)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )


            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Selector de idioma - Desplegable
            LanguageConfigSection(
                context = context,
                selectedLanguage = selectedLanguage,
                onLanguageChanged = { newLanguage ->
                    // Guardar primero para que Translations.get() tenga el valor correcto
                    Config.setLanguage(context, newLanguage)
                    selectedLanguage = newLanguage
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )


            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            Subtitulo("SYSTEM")

            // Sección de nombre de usuario
            UserNicknameConfigSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            GesturesConfigSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Información de uso
            Column {
                Text(
                    text = Translations.get(context, "info"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = dimensions.bodySize,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = dimensions.itemSpacing)
                )

                // Texto de información con estado de actualización
                val infoText = Translations.get(context, "info_text")
                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                } catch (e: Exception) {
                    "1.0"
                }

                val updateStatus = updateInfo?.let { info ->
                    if (info.isUpdateAvailable) {
                        "\n    ● new update available! (v${info.latestVersion})"
                    } else {
                        "\n    ● using latest version (v${currentVersion})"
                    }
                } ?: ""

                Text(
                    text = infoText + updateStatus,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = dimensions.captionSize,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    lineHeight = dimensions.bodySize * 1.3f
                )
            }

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Sección de compartir app
            ShareAppSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
        }
    }
}

/**
 * Sección para compartir la app via QR Dialog
 */
@Composable
fun ShareAppSection(context: Context) {
    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()
    var showShareDialog by remember { mutableStateOf(false) }

    // Mostrar el diálogo de compartir
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

    // Botón centrado
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


@Composable
fun GesturesConfigSection(context: Context) {
    var selectedShakeAction by remember { mutableStateOf(Config.getShakeAction(context)) }
    var selectedSwipeLeftAction by remember { mutableStateOf(Config.getSwipeLeftAction(context)) }
    var selectedSwipeRightAction by remember { mutableStateOf(Config.getSwipeRightAction(context)) }
    var selectedOrientationAction by remember { mutableStateOf(Config.getOrientationAction(context)) }
    val haptic = LocalHapticFeedback.current

    val isEnabled = selectedShakeAction != Config.SHAKE_ACTION_OFF || selectedOrientationAction != Config.ORIENTATION_ACTION_OFF
    val statusText = if (isEnabled) Translations.get(context, "enabled") else Translations.get(context, "disabled")
    val statusColor = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    CollapsibleSection(
        title = Translations.get(context, "gestures_section"),
        statusText = statusText,
        statusColor = statusColor
    ) {
                // Selector de acción para shake
                Text(
                    text = Translations.get(context, "shake_for"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                MultiToggle(
                    options = listOf(
                        Translations.get(context, "shake_off"),
                        Translations.get(context, "shake_next"),
                        Translations.get(context, "shake_previous"),
                        Translations.get(context, "shake_play_pause")
                    ),
                    initialIndex = when (selectedShakeAction) {
                        Config.SHAKE_ACTION_OFF -> 0
                        Config.SHAKE_ACTION_NEXT -> 1
                        Config.SHAKE_ACTION_PREVIOUS -> 2
                        Config.SHAKE_ACTION_PLAY_PAUSE -> 3
                        else -> 0
                    },
                    onChange = { selectedIndex ->
                        selectedShakeAction = when (selectedIndex) {
                            0 -> Config.SHAKE_ACTION_OFF
                            1 -> Config.SHAKE_ACTION_NEXT
                            2 -> Config.SHAKE_ACTION_PREVIOUS
                            3 -> Config.SHAKE_ACTION_PLAY_PAUSE
                            else -> Config.SHAKE_ACTION_OFF
                        }
                        Config.setShakeAction(context, selectedShakeAction)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Selector de acción para swipe izquierdo (swipe song left)
                Text(
                    text = Translations.get(context, "swipe_song_left"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                MultiToggle(
                    options = listOf(
                        Translations.get(context, "swipe_action_queue"),
                        Translations.get(context, "swipe_action_liked"),
                        Translations.get(context, "swipe_action_playlist"),
                        Translations.get(context, "swipe_action_share")
                    ),
                    initialIndex = when (selectedSwipeLeftAction) {
                        Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
                        Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
                        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
                        Config.SWIPE_ACTION_SHARE -> 3
                        else -> 0
                    },
                    onChange = { selectedIndex ->
                        selectedSwipeLeftAction = when (selectedIndex) {
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

                Spacer(modifier = Modifier.height(16.dp))

                // Selector de acción para swipe derecho (swipe song right)
                Text(
                    text = Translations.get(context, "swipe_song_right"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                MultiToggle(
                    options = listOf(
                        Translations.get(context, "swipe_action_queue"),
                        Translations.get(context, "swipe_action_liked"),
                        Translations.get(context, "swipe_action_playlist"),
                        Translations.get(context, "swipe_action_share")
                    ),
                    initialIndex = when (selectedSwipeRightAction) {
                        Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
                        Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
                        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
                        Config.SWIPE_ACTION_SHARE -> 3
                        else -> 1
                    },
                    onChange = { selectedIndex ->
                        selectedSwipeRightAction = when (selectedIndex) {
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

                Spacer(modifier = Modifier.height(16.dp))

                // Selector de acción para orientación (knob)
                Text(
                    text = Translations.get(context, "orientation_for"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                MultiToggle(
                    options = listOf(
                        Translations.get(context, "orientation_off"),
                        Translations.get(context, "orientation_volume"),
                        Translations.get(context, "orientation_skip")
                    ),
                    initialIndex = when (selectedOrientationAction) {
                        Config.ORIENTATION_ACTION_OFF -> 0
                        Config.ORIENTATION_ACTION_VOLUME -> 1
                        Config.ORIENTATION_ACTION_SKIP -> 2
                        else -> 0
                    },
                    onChange = { selectedIndex ->
                        selectedOrientationAction = when (selectedIndex) {
                            0 -> Config.ORIENTATION_ACTION_OFF
                            1 -> Config.ORIENTATION_ACTION_VOLUME
                            2 -> Config.ORIENTATION_ACTION_SKIP
                            else -> Config.ORIENTATION_ACTION_OFF
                        }
                        Config.setOrientationAction(context, selectedOrientationAction)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
    }
}

@Composable
fun ThemeConfigSection(
    context: Context,
    selectedTheme: String,
    onThemeChanged: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val themeOptions = listOf(
        Pair("system", Translations.get(context, "theme_system")),
        Pair("dark", Translations.get(context, "theme_dark")),
        Pair("light", Translations.get(context, "theme_light")),
        Pair("auto", Translations.get(context, "theme_auto"))
    )

    val currentThemeLabel = themeOptions.find { it.first == selectedTheme }?.second ?: Translations.get(context, "theme_system")

    CollapsibleSection(
        title = Translations.get(context, "theme"),
        statusText = currentThemeLabel
    ) {
        MultiToggle(
            options = listOf(
                Translations.get(context, "theme_system"),
                Translations.get(context, "theme_dark"),
                Translations.get(context, "theme_light"),
                Translations.get(context, "theme_auto")
            ),
            initialIndex = when (selectedTheme) {
                "system" -> 0
                "dark" -> 1
                "light" -> 2
                "auto" -> 3
                else -> 0
            },
            onChange = { selectedIndex ->
                val newTheme = when (selectedIndex) {
                    0 -> "system"
                    1 -> "dark"
                    2 -> "light"
                    3 -> "auto"
                    else -> "system"
                }
                onThemeChanged(newTheme)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )
    }
}

@Composable
fun LanguageConfigSection(
    context: Context,
    selectedLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val languageOptions = listOf(
        Pair(Config.LANGUAGE_SPANISH, Translations.get(context, "lang_spanish")),
        Pair(Config.LANGUAGE_ENGLISH, Translations.get(context, "lang_english")),
        Pair(Config.LANGUAGE_CATALAN, Translations.get(context, "lang_catalan")),
        Pair(Config.LANGUAGE_JAPANESE, Translations.get(context, "lang_japanese"))
    )

    val currentLanguageLabel = languageOptions.find { it.first == selectedLanguage }?.second ?: Translations.get(context, "lang_spanish")

    CollapsibleSection(
        title = Translations.get(context, "language"),
        statusText = currentLanguageLabel
    ) {
        MultiToggle(
            options = listOf(
                Translations.get(context, "lang_spanish"),
                Translations.get(context, "lang_english"),
                Translations.get(context, "lang_catalan"),
                Translations.get(context, "lang_japanese")
            ),
            initialIndex = when (selectedLanguage) {
                Config.LANGUAGE_SPANISH -> 0
                Config.LANGUAGE_ENGLISH -> 1
                Config.LANGUAGE_CATALAN -> 2
                Config.LANGUAGE_JAPANESE -> 3
                else -> 0
            },
            onChange = { selectedIndex ->
                val newLanguage = when (selectedIndex) {
                    0 -> Config.LANGUAGE_SPANISH
                    1 -> Config.LANGUAGE_ENGLISH
                    2 -> Config.LANGUAGE_CATALAN
                    3 -> Config.LANGUAGE_JAPANESE
                    else -> Config.LANGUAGE_SPANISH
                }
                onLanguageChanged(newLanguage)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        )
    }
}

@Composable
fun UserNicknameConfigSection(context: Context) {
    var nickname by remember { mutableStateOf(Config.getUserNickname(context) ?: "") }
    val haptic = LocalHapticFeedback.current

    val hasNickname = !nickname.isBlank()
    val statusText = if (hasNickname) nickname else Translations.get(context, "not_configured")
    val statusColor = if (hasNickname) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    CollapsibleSection(
        title = Translations.get(context, "user_nickname"),
        statusText = statusText,
        statusColor = statusColor
    ) {
        Text(
            text = Translations.get(context, "nickname_description"),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = nickname,
            onValueChange = {
                nickname = it
                Config.setUserNickname(context, it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            ),
            placeholder = {
                Text(
                    text = Translations.get(context, "enter_nickname"),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                )
            },
            singleLine = true
        )
    }
}
