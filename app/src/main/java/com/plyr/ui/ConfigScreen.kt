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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.utils.Config
import com.plyr.utils.Translations
import com.plyr.utils.SpotifyAuthEvent
import com.plyr.network.SpotifyRepository
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.plyr.assistant.AssistantTTSHelper



@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var selectedSearchEngine by remember { mutableStateOf(Config.getSearchEngine(context)) }
    var selectedLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    // Estado para Spotify - se actualiza cada vez que se abre la pantalla
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var spotifyUserName by remember { mutableStateOf(Config.getSpotifyUserName(context)) }
    var connectionMessage by remember { mutableStateOf("") }

    // Actualizar el estado de Spotify cuando la pantalla es visible
    LaunchedEffect(Unit) {
        isSpotifyConnected = Config.isSpotifyConnected(context)
        spotifyUserName = Config.getSpotifyUserName(context)
        android.util.Log.d("ConfigScreen", "🔄 Estado actualizado - Conectado: $isSpotifyConnected, Usuario: $spotifyUserName")
    }

    LaunchedEffect(selectedTheme) {
        Config.setTheme(context, selectedTheme)
        onThemeChanged(selectedTheme)
    }

    LaunchedEffect(selectedSearchEngine) {
        Config.setSearchEngine(context, selectedSearchEngine)
    }

    LaunchedEffect(selectedLanguage) {
        Config.setLanguage(context, selectedLanguage)
    }

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

            // Selector de motor de búsqueda - Desplegable
            SearchEngineConfigSection(
                context = context,
                selectedSearchEngine = selectedSearchEngine,
                onSearchEngineChanged = { newEngine ->
                    selectedSearchEngine = newEngine
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Selector de calidad de audio - Desplegable
            var selectedAudioQuality by remember { mutableStateOf(Config.getAudioQuality(context)) }

            LaunchedEffect(selectedAudioQuality) {
                Config.setAudioQuality(context, selectedAudioQuality)
            }

            AudioQualityConfigSection(
                context = context,
                selectedAudioQuality = selectedAudioQuality,
                onAudioQualityChanged = { newQuality ->
                    selectedAudioQuality = newQuality
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Selector de idioma - Desplegable
            LanguageConfigSection(
                context = context,
                selectedLanguage = selectedLanguage,
                onLanguageChanged = { newLanguage ->
                    selectedLanguage = newLanguage
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            AssistantConfigSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            GesturesConfigSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Escuchar eventos de autenticación de Spotify
            LaunchedEffect(Unit) {
                SpotifyAuthEvent.setAuthCallback { success, message ->
                    isSpotifyConnected = success
                    connectionMessage = message ?: if (success) Translations.get(context, "connected") else "error"
                }
            }

            // Limpiar callback al salir
            DisposableEffect(Unit) {
                onDispose {
                    SpotifyAuthEvent.clearCallback()
                }
            }

            // Configuración de API de Spotify
            SpotifyApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            AcoustidApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            LastfmApiConfigSection(context = context)

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

                Text(
                    text = Translations.get(context, "info_text"),
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
                spotifyId = null,
                spotifyUrl = null,
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
fun SpotifyApiConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(Config.getSpotifyClientId(context) ?: "") }
    var clientSecret by remember { mutableStateOf(Config.getSpotifyClientSecret(context) ?: "") }
    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()

    Column {
        // Campo principal de API - similar al formato del cliente
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.get(context, "spotify_status"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = dimensions.bodySize,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = dimensions.itemSpacing)
            )

            Text(
                text = if (Config.hasSpotifyCredentials(context)) Translations.get(context, "configured") else Translations.get(context, "not_configured"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = dimensions.captionSize,
                    color = if (Config.hasSpotifyCredentials(context)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            )
        }

        // Desplegable con campos de configuración
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
            ) {
                // Campos de entrada
                Text(
                    text = Translations.get(context, "client_id"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = clientId,
                    onValueChange = {
                        clientId = it
                        Config.setSpotifyClientId(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
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
                            text = Translations.get(context, "enter_client_id"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        )
                    }
                )

                Text(
                    text = Translations.get(context, "client_secret"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = {
                        clientSecret = it
                        Config.setSpotifyClientSecret(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = {
                        Text(
                            text = Translations.get(context, "enter_client_secret"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                )

                // Explicación detallada
                Column(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = Translations.get(context, "how_to_get_credentials"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    listOf(
                        "instruction_1", "instruction_2", "instruction_3",
                        "instruction_4", "instruction_5", "instruction_6",
                        "instruction_7", "instruction_8", "instruction_9"
                    ).forEach { instructionKey ->
                        Text(
                            text = "        ${Translations.get(context, instructionKey)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Translations.get(context, "note_local_storage"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón de Spotify Login/Logout
                Text(
                    text = when {
                        Config.hasSpotifyCredentials(context) -> {
                            val userName = Config.getSpotifyUserName(context)
                            if (!userName.isNullOrBlank()) {
                                "Hello $userName!"
                            } else {
                                Translations.get(context, "configured")
                            }
                        }
                        else -> Translations.get(context, "login")
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = when {
                            Config.hasSpotifyCredentials(context) -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Config.hasSpotifyCredentials(context)) {
                                // Desconectar Spotify
                                Config.clearSpotifyTokens(context)
                                Config.clearSpotifyUserName(context)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                // Conectar con Spotify
                                try {
                                    val success = SpotifyRepository.startOAuthFlow(context)
                                    if (success) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                } catch (e: Exception) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun AcoustidApiConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(Config.getAcoustidApiKey(context) ?: "") }
    val haptic = LocalHapticFeedback.current

    Column {
        // Campo principal de API - similar al formato del cliente
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.get(context, "acoustid_status"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )


            Text(
                text = if (Config.hasAcoustidApiKey(context)) Translations.get(context, "configured") else Translations.get(context, "not_configured"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (Config.hasAcoustidApiKey(context)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            )
        }

        // Desplegable con campos de configuración
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        Config.setAcoustidApiKey(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
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
                            text = Translations.get(context, "enter_acoustid_api_key"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        )
                    }
                )

                // Explicación detallada sobre AcoustID
                Column(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = Translations.get(context, "acoustid_info"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LastfmApiConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(Config.getLastfmApiKey(context) ?: "") }
    val haptic = LocalHapticFeedback.current

    Column {
        // Campo principal de API - similar al formato del cliente
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.get(context, "lastfm_status"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (Config.hasLastfmApiKey(context)) Translations.get(context, "configured") else Translations.get(context, "not_configured"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (Config.hasLastfmApiKey(context)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            )
        }

        // Desplegable con campos de configuración
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
            ) {
                // Campo de entrada de API Key
                Text(
                    text = "      api_key:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        Config.setLastfmApiKey(context, it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
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
                            text = Translations.get(context, "enter_lastfm_api_key"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                )

                // Explicación detallada sobre Last.fm
                Column(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = Translations.get(context, "lastfm_info"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AssistantConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var assistantEnabled by remember { mutableStateOf(Config.isAssistantEnabled(context)) }
    var useSameLanguage by remember { mutableStateOf(Config.isAssistantSameLanguage(context)) }
    var ttsEnabled by remember { mutableStateOf(Config.isAssistantTtsEnabled(context)) }
    // assistant-specific language (only used when useSameLanguage == false)
    var assistantLanguage by remember { mutableStateOf(Config.getAssistantLanguage(context)) }
    val haptic = LocalHapticFeedback.current

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.get(context, "assistant_settings"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (assistantEnabled) Translations.get(context, "enabled") else Translations.get(context, "disabled"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (assistantEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
            ) {
                CheckboxOption(
                    label = Translations.get(context, "enable_assistant"),
                    checked = assistantEnabled,
                    onCheckedChange = {
                        assistantEnabled = it
                        Config.setAssistantEnabled(context, it)
                        // When disabling assistant, force it to use app language
                        if (!it) {
                            useSameLanguage = true
                            Config.setAssistantSameLanguage(context, true)
                            // sync assistant language to app language
                            val appLang = Config.getLanguage(context)
                            assistantLanguage = appLang
                            Config.setAssistantLanguage(context, appLang)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                CheckboxOption(
                    label = Translations.get(context, "assistant_same_language"),
                    checked = useSameLanguage,
                    enabled = assistantEnabled,
                    onCheckedChange = {
                        useSameLanguage = it
                        Config.setAssistantSameLanguage(context, it)
                        // if now using same language, sync assistant language to app language
                        if (it) {
                            val appLang = Config.getLanguage(context)
                            assistantLanguage = appLang
                            Config.setAssistantLanguage(context, appLang)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                CheckboxOption(
                    label = Translations.get(context, "enable_tts"),
                    checked = ttsEnabled,
                    enabled = assistantEnabled,
                    onCheckedChange = {
                        ttsEnabled = it
                        Config.setAssistantTtsEnabled(context, it)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Optionally start/stop TTS engine
                        if (it) {
                            AssistantTTSHelper.initializeIfNeeded(context)
                        } else {
                            AssistantTTSHelper.shutdownIfNeeded()
                        }
                    }
                )

                // If not using app language, show a language selector identical to the main one
                if (assistantEnabled && !useSameLanguage) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = Translations.get(context, "language"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Map current assistantLanguage to an index
                    var assistantLangIndex by remember {
                        mutableIntStateOf(
                            when (assistantLanguage) {
                                Config.LANGUAGE_SPANISH -> 0
                                Config.LANGUAGE_ENGLISH -> 1
                                Config.LANGUAGE_CATALAN -> 2
                                Config.LANGUAGE_JAPANESE -> 3
                                else -> 0
                            }
                        )
                    }

                    MultiToggle(
                        options = listOf(
                            Translations.get(context, "lang_spanish"),
                            Translations.get(context, "lang_english"),
                            Translations.get(context, "lang_catalan"),
                            Translations.get(context, "lang_japanese")
                        ),
                        initialIndex = assistantLangIndex,
                        onChange = { selectedIndex ->
                            assistantLangIndex = selectedIndex
                            val newLang = when (selectedIndex) {
                                0 -> Config.LANGUAGE_SPANISH
                                1 -> Config.LANGUAGE_ENGLISH
                                2 -> Config.LANGUAGE_CATALAN
                                3 -> Config.LANGUAGE_JAPANESE
                                else -> Config.LANGUAGE_SPANISH
                            }
                            assistantLanguage = newLang
                            Config.setAssistantLanguage(context, newLang)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                }
            }
        }

        // Ensure assistant language stays synced to app language when using same language
        LaunchedEffect(key1 = Config.getLanguage(context), key2 = useSameLanguage) {
            if (useSameLanguage) {
                val appLang = Config.getLanguage(context)
                assistantLanguage = appLang
                Config.setAssistantLanguage(context, appLang)
            }
        }

    }
}

@Composable
fun GesturesConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedShakeAction by remember { mutableStateOf(Config.getShakeAction(context)) }
    var selectedSwipeLeftAction by remember { mutableStateOf(Config.getSwipeLeftAction(context)) }
    var selectedSwipeRightAction by remember { mutableStateOf(Config.getSwipeRightAction(context)) }
    var selectedOrientationAction by remember { mutableStateOf(Config.getOrientationAction(context)) }
    val haptic = LocalHapticFeedback.current

    Column {
        // Campo principal de configuración de gestos
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = Translations.get(context, "gestures_section"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (selectedShakeAction != Config.SHAKE_ACTION_OFF || selectedOrientationAction != Config.ORIENTATION_ACTION_OFF)
                    Translations.get(context, "enabled")
                else
                    Translations.get(context, "disabled"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (selectedShakeAction != Config.SHAKE_ACTION_OFF || selectedOrientationAction != Config.ORIENTATION_ACTION_OFF)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            )
        }

        // Desplegable con opciones de gestos
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp)
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
                        Translations.get(context, "shake_play_pause"),
                        Translations.get(context, "shake_assistant")
                    ),
                    initialIndex = when (selectedShakeAction) {
                        Config.SHAKE_ACTION_OFF -> 0
                        Config.SHAKE_ACTION_NEXT -> 1
                        Config.SHAKE_ACTION_PREVIOUS -> 2
                        Config.SHAKE_ACTION_PLAY_PAUSE -> 3
                        Config.SHAKE_ACTION_ASSISTANT -> 4
                        else -> 0
                    },
                    onChange = { selectedIndex ->
                        selectedShakeAction = when (selectedIndex) {
                            0 -> Config.SHAKE_ACTION_OFF
                            1 -> Config.SHAKE_ACTION_NEXT
                            2 -> Config.SHAKE_ACTION_PREVIOUS
                            3 -> Config.SHAKE_ACTION_PLAY_PAUSE
                            4 -> Config.SHAKE_ACTION_ASSISTANT
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
                        Translations.get(context, "swipe_action_share"),
                        Translations.get(context, "swipe_action_download")
                    ),
                    initialIndex = when (selectedSwipeLeftAction) {
                        Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
                        Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
                        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
                        Config.SWIPE_ACTION_SHARE -> 3
                        Config.SWIPE_ACTION_DOWNLOAD -> 4
                        else -> 0
                    },
                    onChange = { selectedIndex ->
                        selectedSwipeLeftAction = when (selectedIndex) {
                            0 -> Config.SWIPE_ACTION_ADD_TO_QUEUE
                            1 -> Config.SWIPE_ACTION_ADD_TO_LIKED
                            2 -> Config.SWIPE_ACTION_ADD_TO_PLAYLIST
                            3 -> Config.SWIPE_ACTION_SHARE
                            4 -> Config.SWIPE_ACTION_DOWNLOAD
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
                        Translations.get(context, "swipe_action_share"),
                        Translations.get(context, "swipe_action_download")
                    ),
                    initialIndex = when (selectedSwipeRightAction) {
                        Config.SWIPE_ACTION_ADD_TO_QUEUE -> 0
                        Config.SWIPE_ACTION_ADD_TO_LIKED -> 1
                        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> 2
                        Config.SWIPE_ACTION_SHARE -> 3
                        Config.SWIPE_ACTION_DOWNLOAD -> 4
                        else -> 1
                    },
                    onChange = { selectedIndex ->
                        selectedSwipeRightAction = when (selectedIndex) {
                            0 -> Config.SWIPE_ACTION_ADD_TO_QUEUE
                            1 -> Config.SWIPE_ACTION_ADD_TO_LIKED
                            2 -> Config.SWIPE_ACTION_ADD_TO_PLAYLIST
                            3 -> Config.SWIPE_ACTION_SHARE
                            4 -> Config.SWIPE_ACTION_DOWNLOAD
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
    }
}

@Composable
fun CheckboxOption(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (checked) "[x]" else "[ ]",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = if (enabled) {
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                }
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                }
            )
        )
    }
}

@Composable
fun ThemeConfigSection(
    context: Context,
    selectedTheme: String,
    onThemeChanged: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val themeOptions = listOf(
        Pair("system", Translations.get(context, "theme_system")),
        Pair("dark", Translations.get(context, "theme_dark")),
        Pair("light", Translations.get(context, "theme_light")),
        Pair("auto", Translations.get(context, "theme_auto"))
    )

    val currentThemeLabel = themeOptions.find { it.first == selectedTheme }?.second ?: Translations.get(context, "theme_system")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Translations.get(context, "theme"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = currentThemeLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
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
    }
}

@Composable
fun SearchEngineConfigSection(
    context: Context,
    selectedSearchEngine: String,
    onSearchEngineChanged: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val engineOptions = listOf(
        Pair("spotify", Translations.get(context, "search_spotify")),
        Pair("youtube", Translations.get(context, "search_youtube"))
    )

    val currentEngineLabel = engineOptions.find { it.first == selectedSearchEngine }?.second ?: Translations.get(context, "search_spotify")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Translations.get(context, "search_engine"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = currentEngineLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                MultiToggle(
                    options = listOf(
                        Translations.get(context, "search_spotify"),
                        Translations.get(context, "search_youtube")
                    ),
                    initialIndex = if (selectedSearchEngine == "spotify") 0 else 1,
                    onChange = { selectedIndex ->
                        val newEngine = if (selectedIndex == 0) "spotify" else "youtube"
                        onSearchEngineChanged(newEngine)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
        }
    }
}

@Composable
fun AudioQualityConfigSection(
    context: Context,
    selectedAudioQuality: String,
    onAudioQualityChanged: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val qualityOptions = listOf(
        Pair(Config.AUDIO_QUALITY_WORST, Translations.get(context, "quality_low")),
        Pair(Config.AUDIO_QUALITY_MEDIUM, Translations.get(context, "quality_med")),
        Pair(Config.AUDIO_QUALITY_BEST, Translations.get(context, "quality_high"))
    )

    val currentQualityLabel = qualityOptions.find { it.first == selectedAudioQuality }?.second ?: Translations.get(context, "quality_med")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Translations.get(context, "audio_quality"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = currentQualityLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                MultiToggle(
                    options = listOf(
                        Translations.get(context, "quality_low"),
                        Translations.get(context, "quality_med"),
                        Translations.get(context, "quality_high")
                    ),
                    initialIndex = when (selectedAudioQuality) {
                        Config.AUDIO_QUALITY_WORST -> 0
                        Config.AUDIO_QUALITY_MEDIUM -> 1
                        Config.AUDIO_QUALITY_BEST -> 2
                        else -> 1
                    },
                    onChange = { selectedIndex ->
                        val newQuality = when (selectedIndex) {
                            0 -> Config.AUDIO_QUALITY_WORST
                            1 -> Config.AUDIO_QUALITY_MEDIUM
                            2 -> Config.AUDIO_QUALITY_BEST
                            else -> Config.AUDIO_QUALITY_MEDIUM
                        }
                        onAudioQualityChanged(newQuality)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
        }
    }
}

@Composable
fun LanguageConfigSection(
    context: Context,
    selectedLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val languageOptions = listOf(
        Pair(Config.LANGUAGE_SPANISH, Translations.get(context, "lang_spanish")),
        Pair(Config.LANGUAGE_ENGLISH, Translations.get(context, "lang_english")),
        Pair(Config.LANGUAGE_CATALAN, Translations.get(context, "lang_catalan")),
        Pair(Config.LANGUAGE_JAPANESE, Translations.get(context, "lang_japanese"))
    )

    val currentLanguageLabel = languageOptions.find { it.first == selectedLanguage }?.second ?: Translations.get(context, "lang_spanish")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Translations.get(context, "language"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = currentLanguageLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
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
    }
}
