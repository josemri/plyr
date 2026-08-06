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
import com.plyr.network.SupabaseClient
import com.plyr.ui.components.MultiToggle
import com.plyr.ui.components.Titulo
import com.plyr.ui.components.Subtitulo
import com.plyr.ui.components.CollapsibleSection
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    var selectedLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    // Estado para Spotify - se actualiza cada vez que se abre la pantalla
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var spotifyUserName by remember { mutableStateOf(Config.getSpotifyUserName(context)) }
    var connectionMessage by remember { mutableStateOf("") }

    // Update checker state
    var updateInfo by remember { mutableStateOf<com.plyr.utils.UpdateChecker.UpdateInfo?>(null) }

    // Actualizar el estado de Spotify cuando la pantalla es visible
    LaunchedEffect(Unit) {
        isSpotifyConnected = Config.isSpotifyConnected(context)
        spotifyUserName = Config.getSpotifyUserName(context)
        android.util.Log.d("ConfigScreen", "🔄 Estado actualizado - Conectado: $isSpotifyConnected, Usuario: $spotifyUserName")

        // Check for updates
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

            Subtitulo("SERVICES")

            // Toggle Automatic/Manual para API Keys
            var apiKeyMode by remember { mutableStateOf(Config.getApiKeyMode(context)) }
            var isLoadingKeys by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            // Estado del botón de login - declarado antes para poder actualizarlo
            var hasSpotifyCredentials by remember { mutableStateOf(Config.hasSpotifyCredentials(context)) }
            var spotifyUserNameLocal by remember { mutableStateOf(Config.getSpotifyUserName(context)) }
            
            // Cargar keys automáticas al inicio SOLO si está en modo automatic y no tiene credenciales guardadas
            LaunchedEffect(Unit) {
                if (apiKeyMode == "automatic" && !Config.hasSpotifyCredentials(context)) {
                    isLoadingKeys = true
                    val keys = SupabaseClient.getAutomaticKeys()
                    if (keys.isNotEmpty()) {
                        // Mapear nombres de Supabase a nombres de Config
                        keys["client_id"]?.let { Config.setSpotifyClientId(context, it) }
                        keys["client_secret"]?.let { Config.setSpotifyClientSecret(context, it) }
                        keys["acust_id"]?.let { Config.setAcoustidApiKey(context, it) }
                        keys["lastfm"]?.let { Config.setLastfmApiKey(context, it) }
                        // Actualizar el estado del botón después de cargar las credenciales
                        hasSpotifyCredentials = Config.hasSpotifyCredentials(context)
                        android.util.Log.d("ConfigScreen", "✅ Keys cargadas automáticamente, hasSpotifyCredentials: $hasSpotifyCredentials")
                    }
                    isLoadingKeys = false
                }
            }
            MultiToggle(
                options = listOf("Automatic", "Manual"),
                initialIndex = if (apiKeyMode == "automatic") 0 else 1,
                onChange = { index ->
                    val newMode = if (index == 0) "automatic" else "manual"
                    
                    if (newMode == "automatic" && apiKeyMode == "manual") {
                        // Solo cargar keys cuando se cambia de manual a automatic
                        isLoadingKeys = true
                        coroutineScope.launch {
                            val keys = SupabaseClient.getAutomaticKeys()
                            if (keys.isNotEmpty()) {
                                // Mapear nombres de Supabase a nombres de Config
                                keys["client_id"]?.let { Config.setSpotifyClientId(context, it) }
                                keys["client_secret"]?.let { Config.setSpotifyClientSecret(context, it) }
                                keys["acust_id"]?.let { Config.setAcoustidApiKey(context, it) }
                                keys["lastfm"]?.let { Config.setLastfmApiKey(context, it) }
                                // Actualizar el estado del botón después de cargar las credenciales
                                hasSpotifyCredentials = Config.hasSpotifyCredentials(context)
                                android.util.Log.d("ConfigScreen", "✅ Keys cargadas al cambiar a automatic, hasSpotifyCredentials: $hasSpotifyCredentials")
                            }
                            isLoadingKeys = false
                        }
                    } else if (apiKeyMode == "automatic" && newMode == "manual") {
                        // Cambio de automatic a manual: limpiar keys
                        Config.clearAllApiKeys(context)
                        hasSpotifyCredentials = false
                    }
                    
                    apiKeyMode = newMode
                    Config.setApiKeyMode(context, newMode)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Botón de Login/Logout de Spotify
            // Actualizar estado cuando cambian las keys o se termina de cargar
            LaunchedEffect(apiKeyMode, isLoadingKeys) {
                hasSpotifyCredentials = Config.hasSpotifyCredentials(context)
                spotifyUserNameLocal = Config.getSpotifyUserName(context)
            }
            
            Text(
                text = when {
                    hasSpotifyCredentials -> {
                        if (!spotifyUserNameLocal.isNullOrBlank()) {
                            "Hello $spotifyUserNameLocal!"
                        } else {
                            Translations.get(context, "login")
                        }
                    }
                    else -> Translations.get(context, "login")
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = if (hasSpotifyCredentials && !spotifyUserNameLocal.isNullOrBlank()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        android.util.Log.d("ConfigScreen", "🔘 Login button clicked!")
                        android.util.Log.d("ConfigScreen", "   hasSpotifyCredentials: $hasSpotifyCredentials")
                        android.util.Log.d("ConfigScreen", "   spotifyUserNameLocal: $spotifyUserNameLocal")
                        android.util.Log.d("ConfigScreen", "   Config.hasSpotifyCredentials: ${Config.hasSpotifyCredentials(context)}")
                        android.util.Log.d("ConfigScreen", "   Config.getSpotifyClientId: ${Config.getSpotifyClientId(context)}")
                        android.util.Log.d("ConfigScreen", "   Config.getSpotifyClientSecret: ${Config.getSpotifyClientSecret(context)?.take(5)}...")
                        
                        if (hasSpotifyCredentials && !spotifyUserNameLocal.isNullOrBlank()) {
                            // Ya está logueado, hacer logout
                            android.util.Log.d("ConfigScreen", "   ➡️ Doing LOGOUT")
                            Config.clearSpotifyTokens(context)
                            Config.clearSpotifyUserName(context)
                            spotifyUserNameLocal = null
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (hasSpotifyCredentials) {
                            // Tiene credenciales pero no está logueado, iniciar login
                            android.util.Log.d("ConfigScreen", "   ➡️ Starting OAuth LOGIN flow")
                            try {
                                val success = SpotifyRepository.startOAuthFlow(context)
                                android.util.Log.d("ConfigScreen", "   OAuth flow started: $success")
                                if (success) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ConfigScreen", "   ❌ OAuth flow error: ${e.message}", e)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            android.util.Log.d("ConfigScreen", "   ⚠️ No credentials - button click ignored")
                        }
                    }
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

            // Configuración de API (solo en modo manual)
            if (apiKeyMode == "manual") {
                SpotifyApiConfigSection(context = context)

                Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

                AcoustidApiConfigSection(context = context)

                Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

                LastfmApiConfigSection(context = context)

                Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
            }

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
    var clientId by remember { mutableStateOf(Config.getSpotifyClientId(context) ?: "") }
    var clientSecret by remember { mutableStateOf(Config.getSpotifyClientSecret(context) ?: "") }
    val haptic = LocalHapticFeedback.current

    val hasCredentials = Config.hasSpotifyCredentials(context)
    val statusText = if (hasCredentials) Translations.get(context, "configured") else Translations.get(context, "not_configured")
    val statusColor = if (hasCredentials) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    CollapsibleSection(
        title = Translations.get(context, "spotify_status"),
        statusText = statusText,
        statusColor = statusColor
    ) {
        // Client ID field
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

        // Client Secret field
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

        // Instructions
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
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

@Composable
fun AcoustidApiConfigSection(context: Context) {
    var apiKey by remember { mutableStateOf(Config.getAcoustidApiKey(context) ?: "") }
    val haptic = LocalHapticFeedback.current

    val hasApiKey = Config.hasAcoustidApiKey(context)
    val statusText = if (hasApiKey) Translations.get(context, "configured") else Translations.get(context, "not_configured")
    val statusColor = if (hasApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    CollapsibleSection(
        title = Translations.get(context, "acoustid_status"),
        statusText = statusText,
        statusColor = statusColor
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

        // Info text
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

@Composable
fun LastfmApiConfigSection(context: Context) {
    var apiKey by remember { mutableStateOf(Config.getLastfmApiKey(context) ?: "") }
    val haptic = LocalHapticFeedback.current

    val hasApiKey = Config.hasLastfmApiKey(context)
    val statusText = if (hasApiKey) Translations.get(context, "configured") else Translations.get(context, "not_configured")
    val statusColor = if (hasApiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    CollapsibleSection(
        title = Translations.get(context, "lastfm_status"),
        statusText = statusText,
        statusColor = statusColor
    ) {
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

        // Info text
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
