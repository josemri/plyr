package com.plyr.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.utils.Config
import com.plyr.utils.Translations
import com.plyr.utils.SpotifyAuthEvent
import com.plyr.network.SpotifyRepository
import com.plyr.ui.components.BinaryToggle
import com.plyr.ui.components.TernaryToggle
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.components.AsciiWaveActionButton
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
                .padding(16.dp)
        ) {
            // Header
            Titulo(Translations.get(context, "config_title"))

            Spacer(modifier = Modifier.height(8.dp))

            // Selector de tema
            Text(
                text = Translations.get(context, "theme"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Reemplazado BinaryToggle por TernaryToggle para soportar "system"
            TernaryToggle(
                option1 = Translations.get(context, "theme_system"),
                option2 = Translations.get(context, "theme_dark"),
                option3 = Translations.get(context, "theme_light"),
                initialValue = when (selectedTheme) {
                    "system" -> 0
                    "dark" -> 1
                    "light" -> 2
                    else -> 0
                },
                onChange = { selectedIndex ->
                    selectedTheme = when (selectedIndex) {
                        0 -> "system"
                        1 -> "dark"
                        2 -> "light"
                        else -> "system"
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Selector de motor de búsqueda
            Text(
                text = Translations.get(context, "search_engine"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            BinaryToggle(
                option1 = Translations.get(context, "search_spotify"),
                option2 = Translations.get(context, "search_youtube"),
                initialValue = selectedSearchEngine == "spotify",
                onChange = { isSpotify ->
                    selectedSearchEngine = if (isSpotify) "spotify" else "youtube"
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Selector de calidad de audio
            Text(
                text = Translations.get(context, "audio_quality"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var selectedAudioQuality by remember { mutableStateOf(Config.getAudioQuality(context)) }

            LaunchedEffect(selectedAudioQuality) {
                Config.setAudioQuality(context, selectedAudioQuality)
            }

            TernaryToggle(
                option1 = Translations.get(context, "quality_low"),
                option2 = Translations.get(context, "quality_med"),
                option3 = Translations.get(context, "quality_high"),
                initialValue = when (selectedAudioQuality) {
                    Config.AUDIO_QUALITY_WORST -> 0
                    Config.AUDIO_QUALITY_MEDIUM -> 1
                    Config.AUDIO_QUALITY_BEST -> 2
                    else -> 1
                },
                onChange = { selectedIndex ->
                    selectedAudioQuality = when (selectedIndex) {
                        0 -> Config.AUDIO_QUALITY_WORST
                        1 -> Config.AUDIO_QUALITY_MEDIUM
                        2 -> Config.AUDIO_QUALITY_BEST
                        else -> Config.AUDIO_QUALITY_MEDIUM
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Selector de idioma
            Text(
                text = Translations.get(context, "language"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                    selectedLanguage = when (selectedIndex) {
                        0 -> Config.LANGUAGE_SPANISH
                        1 -> Config.LANGUAGE_ENGLISH
                        2 -> Config.LANGUAGE_CATALAN
                        3 -> Config.LANGUAGE_JAPANESE
                        else -> Config.LANGUAGE_SPANISH
                    }
                    Config.setLanguage(context, selectedLanguage)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )


            Spacer(modifier = Modifier.height(30.dp))

            // Configuración de acciones de swipe
            Text(
                text = Translations.get(context, "swipe_actions"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Selector de acción para swipe izquierdo
            var selectedSwipeLeftAction by remember { mutableStateOf(Config.getSwipeLeftAction(context)) }

            LaunchedEffect(selectedSwipeLeftAction) {
                Config.setSwipeLeftAction(context, selectedSwipeLeftAction)
            }

            Text(
                text = "    ${Translations.get(context, "swipe_left")}:",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(bottom = 4.dp)
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

            // Selector de acción para swipe derecho
            var selectedSwipeRightAction by remember { mutableStateOf(Config.getSwipeRightAction(context)) }

            LaunchedEffect(selectedSwipeRightAction) {
                Config.setSwipeRightAction(context, selectedSwipeRightAction)
            }

            Text(
                text = "    ${Translations.get(context, "swipe_right")}:",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.padding(bottom = 4.dp)
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

            Spacer(modifier = Modifier.height(30.dp))

            // Información de uso
            Column {
                Text(
                    text = Translations.get(context, "info"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = Translations.get(context, "info_text"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

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

            // Status unificado de plyr y Spotify
            Column {
                // Botón de Spotify Login/Logout
                Text(
                    text = when {
                        isSpotifyConnected && Config.hasSpotifyCredentials(context) -> {
                            val userName = Config.getSpotifyUserName(context)
                            if (!userName.isNullOrBlank()) {
                                "Hello $userName!"
                            } else {
                                Translations.get(context, "configured")
                            }
                        }
                        else -> Translations.get(context, "login")
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = when {
                            isSpotifyConnected && Config.hasSpotifyCredentials(context) -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSpotifyConnected) {
                                // Desconectar Spotify
                                Config.clearSpotifyTokens(context)
                                Config.clearSpotifyUserName(context)
                                isSpotifyConnected = false
                                connectionMessage = Translations.get(context, "disconnected")
                            } else {
                                // Verificar que las credenciales estén configuradas
                                if (!Config.hasSpotifyCredentials(context)) {
                                    connectionMessage = "credentials_required"
                                } else {
                                    // Conectar con Spotify
                                    connectionMessage = Translations.get(context, "opening_browser")
                                    try {
                                        val success = SpotifyRepository.startOAuthFlow(context)
                                        connectionMessage = if (success) {
                                            Translations.get(context, "check_browser")
                                        } else {
                                            Translations.get(context, "error_starting_oauth")
                                        }
                                    } catch (e: Exception) {
                                        connectionMessage = "error: ${e.message}"
                                    }
                                }
                            }
                        }
                )

                // Mostrar mensaje de conexión si existe
                if (connectionMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = connectionMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Configuración de API de Spotify
            SpotifyApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(10.dp))

            AcoustidApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(10.dp))

            LastfmApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(10.dp))

            AssistantConfigSection(context = context)

            Spacer(modifier = Modifier.height(10.dp))

            SensorsConfigSection(context = context)

            Spacer(modifier = Modifier.height(30.dp))

            // Sección de compartir app
            ShareAppSection(context = context)

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

/**
 * Sección para compartir la app via QR Dialog
 */
@Composable
fun ShareAppSection(context: Context) {
    val haptic = LocalHapticFeedback.current
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
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showShareDialog = true
                }
                .padding(vertical = 12.dp, horizontal = 16.dp)
        )
    }
}


@Composable
fun SpotifyApiConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(Config.getSpotifyClientId(context) ?: "") }
    var clientSecret by remember { mutableStateOf(Config.getSpotifyClientSecret(context) ?: "") }
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
                text = Translations.get(context, "spotify_status"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (Config.hasSpotifyCredentials(context)) Translations.get(context, "configured") else Translations.get(context, "not_configured"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
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
                        mutableStateOf(
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

                // Keep description
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = Translations.get(context, "assistant_description"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    lineHeight = 14.sp
                )
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
fun SensorsConfigSection(context: Context) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedShakeAction by remember { mutableStateOf(Config.getShakeAction(context)) }
    val haptic = LocalHapticFeedback.current

    Column {
        // Campo principal de configuración de sensores
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
                text = Translations.get(context, "sensors_section"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (selectedShakeAction != Config.SHAKE_ACTION_OFF)
                    Translations.get(context, "enabled")
                else
                    Translations.get(context, "disabled"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (selectedShakeAction != Config.SHAKE_ACTION_OFF)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            )
        }

        // Desplegable con opciones de sensores
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

                Spacer(modifier = Modifier.height(12.dp))

                // Descripción
                Text(
                    text = Translations.get(context, "sensors_description"),
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
