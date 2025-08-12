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
import com.plyr.utils.SpotifyAuthEvent
import com.plyr.network.SpotifyRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var selectedSearchEngine by remember { mutableStateOf(Config.getSearchEngine(context)) }

    // Estado para Spotify
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("") }

    LaunchedEffect(selectedTheme) {
        Config.setTheme(context, selectedTheme)
        onThemeChanged(selectedTheme)
    }

    LaunchedEffect(selectedSearchEngine) {
        Config.setSearchEngine(context, selectedSearchEngine)
    }

    val haptic = LocalHapticFeedback.current

    // Handle back button
    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "$ plyr_config",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selector de tema
        Text(
            text = "> theme",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                //color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            // Opción Dark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedTheme = "dark"
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {

                Text(
                    text = "dark",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedTheme == "dark") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ){
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            // Opción Light
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedTheme = "light"
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "light",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedTheme == "light") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Selector de motor de búsqueda
        Text(
            text = "> search_engine",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                //color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            // Opción Spotify
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedSearchEngine = "spotify"
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "spotify",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedSearchEngine == "spotify") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ){
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color =MaterialTheme.colorScheme.secondary
                    )
                )
            }
            // Opción YouTube
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedSearchEngine = "youtube"
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "youtube",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedSearchEngine == "youtube") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Selector de calidad de audio
        Text(
            text = "> audio_quality",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // Estado para la calidad de audio
        var selectedAudioQuality by remember { mutableStateOf(Config.getAudioQuality(context)) }

        // Aplicar cambios cuando cambie la selección
        LaunchedEffect(selectedAudioQuality) {
            Config.setAudioQuality(context, selectedAudioQuality)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            // Opción Worst
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedAudioQuality = Config.AUDIO_QUALITY_WORST
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "low",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedAudioQuality == Config.AUDIO_QUALITY_WORST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ){
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            // Opción Medium
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedAudioQuality = Config.AUDIO_QUALITY_MEDIUM
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "med",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedAudioQuality == Config.AUDIO_QUALITY_MEDIUM) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ){
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            // Opción Best
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedAudioQuality = Config.AUDIO_QUALITY_BEST
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = "high",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedAudioQuality == Config.AUDIO_QUALITY_BEST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Información de uso
        Column {
            Text(
                text = "> info",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    //color = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "    ● don't pirate music!\n    ● Change engine with yt: / sp:",
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
                isConnecting = false
                isSpotifyConnected = success
                connectionMessage = message ?: if (success) "connected" else "error"
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
                text = "> sptfy_status",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    //color = MaterialTheme.colorScheme.secondary
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
                            connectionMessage = "disconnected"
                        } else {
                            // Verificar que las credenciales estén configuradas
                            if (!Config.hasSpotifyCredentials(context)) {
                                connectionMessage = "credentials_required"
                            } else {
                                // Conectar con Spotify
                                isConnecting = true
                                connectionMessage = "opening_browser..."
                                try {
                                    val success = SpotifyRepository.startOAuthFlow(context)
                                    if (success) {
                                        connectionMessage = "check_browser"
                                    } else {
                                        connectionMessage = "error_starting_oauth"
                                        isConnecting = false
                                    }
                                } catch (e: Exception) {
                                    connectionMessage = "error: ${e.message}"
                                    isConnecting = false
                                }
                            }
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "    ● client:",
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
                            connectionMessage == "credentials_required" -> "configure credentials first"
                            connectionMessage.isNotEmpty() -> connectionMessage
                            isSpotifyConnected && Config.hasSpotifyCredentials(context) -> "connected"
                            Config.hasSpotifyCredentials(context) -> "disconnected"
                            else -> "credentials required"
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
                text = "    ● api:",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF95A5A6)
                )
            )

            Text(
                text = if (Config.hasSpotifyCredentials(context)) "configured" else "not_configured",
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
                    text = "      client_id:",
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
                            text = "enter your spotify client id",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF7F8C8D)
                            )
                        )
                    }
                )

                Text(
                    text = "      client_secret:",
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
                            text = "enter your spotify client secret",
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
                        text = "      > how to get spotify api credentials:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF3498DB)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val instructions = listOf(
                        "1. go to https://developer.spotify.com/dashboard",
                        "2. log in with your spotify account",
                        "3. click 'create app'",
                        "4. fill app name (e.g., 'plyr mobile')",
                        "5. set redirect uri: 'plyr://spotify/callback'",
                        "6. select 'mobile' and 'web api'",
                        "7. click 'save'",
                        "8. copy client id and client secret",
                        "9. paste them in the fields above"
                    )

                    instructions.forEach { instruction ->
                        Text(
                            text = "        $instruction",
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
                        text = "      note: these credentials are stored locally",
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

/**
 * Función helper para obtener información descriptiva sobre cada calidad de audio
 */
private fun getAudioQualityInfo(quality: String): String {
    return when (quality) {
        Config.AUDIO_QUALITY_WORST -> "64kbps • ~0.5MB/min • saves data"
        Config.AUDIO_QUALITY_MEDIUM -> "128kbps • ~1MB/min • balanced (default)"
        Config.AUDIO_QUALITY_BEST -> "192kbps+ • ~1.5MB/min • best quality"
        else -> "unknown quality"
    }
}
