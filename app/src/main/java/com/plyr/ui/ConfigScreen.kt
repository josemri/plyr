package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.Color
import com.plyr.ui.components.BinaryToggle
import com.plyr.ui.components.TernaryToggle
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo


@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var selectedSearchEngine by remember { mutableStateOf(Config.getSearchEngine(context)) }
    var selectedLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    // Estado para Spotify
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var connectionMessage by remember { mutableStateOf("") }

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

            BinaryToggle(
                option1 = Translations.get(context, "theme_dark"),
                option2 = Translations.get(context, "theme_light"),
                initialValue = selectedTheme == "dark",
                onChange = { isDark ->
                    selectedTheme = if (isDark) "dark" else "light"
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
                    color = Color(0xFF95A5A6)
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
                    color = Color(0xFF95A5A6)
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
                        color = Color(0xFF95A5A6)
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
                Text(
                    text = Translations.get(context, "sptfy_status"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Estado de Spotify (clickeable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSpotifyConnected) {
                                // Desconectar Spotify
                                Config.clearSpotifyTokens(context)
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Translations.get(context, "client"),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Estado de conexión
                        Text(
                            text = when {
                                connectionMessage == "credentials_required" -> Translations.get(context, "configure_credentials_first")
                                connectionMessage.isNotEmpty() -> connectionMessage
                                isSpotifyConnected && Config.hasSpotifyCredentials(context) -> Translations.get(context, "connected")
                                Config.hasSpotifyCredentials(context) -> Translations.get(context, "disconnected")
                                else -> Translations.get(context, "credentials_required")
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = when {
                                    connectionMessage == "credentials_required" -> Color(0xFFE74C3C)
                                    !Config.hasSpotifyCredentials(context) -> Color(0xFFE74C3C)
                                    isSpotifyConnected -> Color(0xFF1DB954)
                                    else -> Color(0xFF95A5A6)
                                }
                            )
                        )
                    }
                }
            }

            // Configuración de API de Spotify
            SpotifyApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(30.dp))

            // Título y configuración de AcoustID
            Column {
                Text(
                    text = Translations.get(context, "acoustid_status"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Configuración de AcoustID API Key
            AcoustidApiConfigSection(context = context)

            Spacer(modifier = Modifier.height(30.dp))
        }
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
                text = Translations.get(context, "api"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF95A5A6)
                )
            )

            Text(
                text = if (Config.hasSpotifyCredentials(context)) Translations.get(context, "configured") else Translations.get(context, "not_configured"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (Config.hasSpotifyCredentials(context)) Color(0xFF1DB954) else Color(0xFFE74C3C)
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
                        color = Color(0xFF95A5A6)
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
                        fontSize = 11.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF95A5A6),
                        focusedTextColor = Color(0xFFECF0F1),
                        unfocusedTextColor = Color(0xFFBDC3C7)
                    ),
                    placeholder = {
                        Text(
                            text = Translations.get(context, "enter_client_id"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF7F8C8D)
                            )
                        )
                    }
                )

                Text(
                    text = Translations.get(context, "client_secret"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF95A5A6)
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
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF95A5A6),
                        focusedTextColor = Color(0xFFECF0F1),
                        unfocusedTextColor = Color(0xFFBDC3C7)
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = {
                        Text(
                            text = Translations.get(context, "enter_client_secret"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF7F8C8D)
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
                            color = Color(0xFF3498DB)
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
                                color = Color(0xFF95A5A6)
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
                            color = Color(0xFF7F8C8D)
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
                text = Translations.get(context, "acoustid_api_key"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF95A5A6)
                )
            )

            Text(
                text = if (Config.hasAcoustidApiKey(context)) Translations.get(context, "configured") else Translations.get(context, "not_configured"),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (Config.hasAcoustidApiKey(context)) Color(0xFF1DB954) else Color(0xFFE74C3C)
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
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

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
                        fontSize = 11.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1DB954),
                        unfocusedBorderColor = Color(0xFF95A5A6),
                        focusedTextColor = Color(0xFFECF0F1),
                        unfocusedTextColor = Color(0xFFBDC3C7)
                    ),
                    placeholder = {
                        Text(
                            text = Translations.get(context, "enter_acoustid_api_key"),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF7F8C8D)
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
                            color = Color(0xFF95A5A6)
                        ),
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
