package com.plyr.ui.screens

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.utils.Config

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var showSpotifyConfig by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(Config.getSpotifyClientId(context) ?: "") }
    var clientSecret by remember { mutableStateOf(Config.getSpotifyClientSecret(context) ?: "") }

    // Estados reactivos para actualización inmediata
    var currentEngine by remember { mutableStateOf(Config.getSearchEngine(context)) }
    var currentTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var currentQuality by remember { mutableStateOf(Config.getAudioQuality(context)) }
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var hasCredentials by remember { mutableStateOf(Config.hasSpotifyCredentials(context)) }

    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Terminal-style header
        Text(
            text = "$ plyr_config",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Search Engine - CENTRADO
        val engines = listOf("spotify", "youtube")

        Text(
            text = "search_engine:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            engines.forEachIndexed { index, engine ->
                Text(
                    text = engine,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = if (currentEngine == engine) Color.White else Color(0xFF95A5A6)
                    ),
                    modifier = Modifier
                        .clickable {
                            Config.setSearchEngine(context, engine)
                            currentEngine = engine // Actualización inmediata
                        }
                        .padding(4.dp)
                )

                if (index < engines.size - 1) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
        }

        // Theme - CENTRADO
        val themes = listOf("dark", "light", "default")

        Text(
            text = "theme:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            themes.forEachIndexed { index, theme ->
                Text(
                    text = theme,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = if (currentTheme == theme) Color.White else Color(0xFF95A5A6)
                    ),
                    modifier = Modifier
                        .clickable {
                            Config.setTheme(context, theme)
                            currentTheme = theme // Actualización inmediata
                            onThemeChanged(theme)
                        }
                        .padding(4.dp)
                )

                if (index < themes.size - 1) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
        }

        // Audio Quality - CENTRADO
        val qualities = listOf("best", "medium", "worst")

        Text(
            text = "audio_quality:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            qualities.forEachIndexed { index, quality ->
                Text(
                    text = quality,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = if (currentQuality == quality) Color.White else Color(0xFF95A5A6)
                    ),
                    modifier = Modifier
                        .clickable {
                            Config.setAudioQuality(context, quality)
                            currentQuality = quality // Actualización inmediata
                        }
                        .padding(4.dp)
                )

                if (index < qualities.size - 1) {
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
        }

        // Spotify Configuration
        Text(
            text = "spotify_config:",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Status - clickeable para abrir Spotify OAuth
        Row(
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "status: ",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF95A5A6)
                )
            )
            Text(
                text = if (isSpotifyConnected) "connected" else "click_to_login",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (isSpotifyConnected) Color(0xFF1DB954) else Color(0xFF4ECDC4)
                ),
                modifier = Modifier.clickable {
                    if (hasCredentials) {
                        // Construir URL de autorización de Spotify
                        val clientId = Config.getSpotifyClientId(context)
                        val redirectUri = Config.SPOTIFY_REDIRECT_URI
                        val scopes = Config.SPOTIFY_SCOPES

                        val authUrl = "https://accounts.spotify.com/authorize?" +
                                "client_id=$clientId" +
                                "&response_type=code" +
                                "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
                                "&scope=${java.net.URLEncoder.encode(scopes, "UTF-8")}"

                        // Abrir navegador con la URL de autorización
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("ConfigScreen", "Error opening Spotify OAuth URL", e)
                        }
                    } else {
                        // Si no hay credenciales, mostrar el desplegable
                        showSpotifyConfig = true
                    }
                }
            )
        }

        // API - desplegable
        Row(
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "api: ",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF95A5A6)
                )
            )
            Text(
                text = "${if (showSpotifyConfig) "▼" else "▶"} ${if (hasCredentials) "configured" else "setup"}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { showSpotifyConfig = !showSpotifyConfig }
                    .padding(4.dp)
            )
        }

        if (showSpotifyConfig) {
            Column(
                modifier = Modifier.padding(start = 32.dp, bottom = 16.dp)
            ) {
                // Client ID
                Text(
                    text = "client_id:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4ECDC4),
                        unfocusedBorderColor = Color(0xFF95A5A6)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Client Secret
                Text(
                    text = "client_secret:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4ECDC4),
                        unfocusedBorderColor = Color(0xFF95A5A6)
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Save/Clear buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "[save]",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF1DB954)
                        ),
                        modifier = Modifier
                            .clickable {
                                if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                                    Config.setSpotifyCredentials(context, clientId, clientSecret)
                                    hasCredentials = true // Actualización inmediata
                                }
                            }
                            .padding(4.dp)
                    )

                    Text(
                        text = "[clear]",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFFE74C3C)
                        ),
                        modifier = Modifier
                            .clickable {
                                Config.clearSpotifyCredentials(context)
                                Config.clearSpotifyTokens(context)
                                clientId = ""
                                clientSecret = ""
                                hasCredentials = false // Actualización inmediata
                                isSpotifyConnected = false // Actualización inmediata
                            }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Explicación en inglés
                Text(
                    text = "How to get your Spotify API credentials:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF7F8C8D)
                    ),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val instructions = listOf(
                    "1. Go to developer.spotify.com/dashboard",
                    "2. Log in with your Spotify account",
                    "3. Click 'Create an App'",
                    "4. Fill in app name and description",
                    "5. Copy the Client ID and Client Secret",
                    "6. In app settings, add redirect URI:",
                    "   plyr://spotify/callback"
                )

                instructions.forEach { instruction ->
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF95A5A6)
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }

        if (isSpotifyConnected) {
            Text(
                text = "    [disconnect]",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFE74C3C)
                ),
                modifier = Modifier
                    .clickable {
                        Config.clearSpotifyTokens(context)
                        isSpotifyConnected = false // Actualización inmediata
                    }
                    .padding(4.dp)
            )
        }
    }
}
