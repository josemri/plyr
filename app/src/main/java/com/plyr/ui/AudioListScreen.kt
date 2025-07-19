package com.plyr.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.plyr.model.AudioItem
import com.plyr.network.AudioRepository
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyTrack
import com.plyr.utils.Config
import com.plyr.utils.SpotifyAuthEvent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import androidx.compose.ui.draw.clipToBounds

// Estados para navegación
enum class Screen {
    MAIN,
    CONFIG,
    PLAYLISTS
}

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onThemeChanged: (String) -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    
    when (currentScreen) {
        Screen.MAIN -> MainScreen(
            context = context,
            onVideoSelected = onVideoSelected,
            onOpenConfig = { currentScreen = Screen.CONFIG },
            onOpenPlaylists = { currentScreen = Screen.PLAYLISTS }
        )
        Screen.CONFIG -> ConfigScreen(
            context = context,
            onBack = { currentScreen = Screen.MAIN },
            onThemeChanged = onThemeChanged,
            onOpenPlaylists = { currentScreen = Screen.PLAYLISTS }
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { currentScreen = Screen.MAIN }
        )
    }
}

@Composable
fun MainScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onOpenConfig: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Terminal-style header con detección de deslizamiento
        var dragOffsetX by remember { mutableStateOf(0f) }
        var dragOffsetY by remember { mutableStateOf(0f) }
        
        Text(
            text = "$ plyr_search",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .offset(x = dragOffsetX.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetX) > 100 && dragOffsetX > 0) {
                                onOpenConfig()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (abs(dragOffsetX) > 100 && dragOffsetX < 0) {
                                onOpenPlaylists()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffsetX = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffsetX += dragAmount / density
                    }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetY) > 100 && dragOffsetY > 0) {
                                onOpenPlaylists()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffsetY = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffsetY += dragAmount / density
                    }
                }
        )

        // Search field with clear button and enter action
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { 
                Text(
                    "> search_audio",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        results = emptyList()
                        error = null
                    }) {
                        Text(
                            text = "x",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank() && !isLoading) {
                        isLoading = true
                        error = null
                        results = emptyList()

                        // Obtener configuración
                        val baseUrl = Config.getNgrokUrl(context)
                        val apiKey = Config.getApiToken(context)
                        
                        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
                            isLoading = false
                            error = "Configuración incompleta: Verifica URL base y API Key en configuración"
                            return@KeyboardActions
                        }

                        AudioRepository.searchAudios(searchQuery, baseUrl, apiKey) { list, err ->
                            isLoading = false
                            if (err != null) {
                                error = err
                            } else if (list != null) {
                                results = list
                            }
                        }
                    }
                }
            ),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.secondary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
        )

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD93D)
                    )
                )
                Text(
                    "$ loading...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    )
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "ERR: $it",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(results) { item ->
                val id = item.url.toUri().getQueryParameter("v")
                if (id != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .clickable { 
                                // Limpiar resultados y búsqueda al seleccionar
                                searchQuery = ""
                                results = emptyList()
                                error = null
                                onVideoSelected(id, item.title)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "> ",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                color = Color(0xFF4ECDC4)
                            )
                        )
                        MarqueeText(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFFE0E0E0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
            }
        }
    }
}

// Composable para texto marquee mejorado
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var textWidth by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }
    val shouldAnimate = textWidth > containerWidth && containerWidth > 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) -(textWidth - containerWidth).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (shouldAnimate) maxOf(text.length * 100, 3000) else 0,
                easing = LinearEasing,
                delayMillis = 1500 // Pausa al inicio
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_animation"
    )
    
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidth = size.width
            }
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { size ->
                    textWidth = size.width
                }
                .offset(x = with(density) { animatedOffset.toDp() })
        )
    }
}

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit = {},
    onOpenPlaylists: () -> Unit
) {
    var ngrokUrl by remember { mutableStateOf(Config.getNgrokUrl(context)) }
    var apiToken by remember { mutableStateOf(Config.getApiToken(context)) }
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    
    // Estado para Spotify
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("") }
    
    // Estado para el resultado de whoami
    var whoamiResult by remember { mutableStateOf<String?>(null) }
    var whoamiError by remember { mutableStateOf<String?>(null) }
    var isCheckingAuth by remember { mutableStateOf(false) }
    
    // Función para verificar autenticación
    fun checkAuth() {
        if (ngrokUrl.isNotBlank() && apiToken.isNotBlank()) {
            isCheckingAuth = true
            whoamiResult = null
            whoamiError = null
            
            AudioRepository.whoami(ngrokUrl, apiToken) { user, error ->
                isCheckingAuth = false
                if (error != null) {
                    whoamiError = error
                    whoamiResult = null
                } else {
                    whoamiResult = user
                    whoamiError = null
                }
            }
        } else {
            whoamiResult = null
            whoamiError = null
        }
    }
    
    // Guardar automáticamente cuando cambien los valores
    LaunchedEffect(ngrokUrl) {
        if (ngrokUrl.isNotBlank()) {
            Config.setNgrokUrl(context, ngrokUrl)
            checkAuth()
        }
    }
    
    LaunchedEffect(apiToken) {
        if (apiToken.isNotBlank()) {
            Config.setApiToken(context, apiToken)
            checkAuth()
        }
    }
    
    // Verificación inicial al cargar la pantalla
    LaunchedEffect(Unit) {
        checkAuth()
    }
    
    LaunchedEffect(selectedTheme) {
        Config.setTheme(context, selectedTheme)
        onThemeChanged(selectedTheme)
    }
    
    val haptic = LocalHapticFeedback.current

    var dragOffsetX by remember { mutableStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header de configuración con detección de deslizamiento
        Text(
            text = "$ plyr_config",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .offset(x = dragOffsetX.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetX) > 100 && dragOffsetX < 0) {
                                onBack()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffsetX = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffsetX += dragAmount / density
                    }
                }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Selector de tema
        Text(
            text = "> theme",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    text = if (selectedTheme == "dark") "●" else "○",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = if (selectedTheme == "dark") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "dark",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedTheme == "dark") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
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
                    text = if (selectedTheme == "light") "●" else "○",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = if (selectedTheme == "light") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
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
        
        // Campo Ngrok URL
        Text(
            text = "> ngrok_url",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = ngrokUrl,
            onValueChange = { ngrokUrl = it },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            placeholder = {
                Text(
                    "https://abc123.com",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Campo API Token
        Text(
            text = "> api_token",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            placeholder = {
                Text(
                    "token_abc123xyz",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // Información de uso
        Column {
            Text(
                text = "$ info",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "• considera pagarme algo por esto, porfa\n• API URL: endpoint de tu servidor\n• API Token: para autenticación futura\n• Desliza ↓ en main para playlists",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF95A5A6)
                ),
                lineHeight = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Botón para vincular cuenta de Spotify
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "> spotify_account",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
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
                            // Conectar con Spotify
                            isConnecting = true
                            connectionMessage = "opening_browser..."
                            try {
                                SpotifyRepository.startOAuthFlow(context)
                                connectionMessage = "check_browser"
                            } catch (e: Exception) {
                                connectionMessage = "error: ${e.message}"
                                isConnecting = false
                            }
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isConnecting -> "⏳ "
                        isSpotifyConnected -> "✓ "
                        else -> "○ "
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = when {
                            isConnecting -> MaterialTheme.colorScheme.secondary
                            isSpotifyConnected -> Color(0xFF1DB954)
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
                )
                Text(
                    text = when {
                        connectionMessage.isNotEmpty() -> connectionMessage
                        isSpotifyConnected -> "spotify_connected"
                        else -> "connect_spotify"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (isSpotifyConnected) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Color(0xFF1DB954)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Status unificado de plyr y Spotify
        Column {
            Text(
                text = "$ status",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Estado de plyr
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "    > plyr:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    )
                )
                
                when {
                    isCheckingAuth -> {
                        Text(
                            text = "checking...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFFFD93D)
                            )
                        )
                    }
                    whoamiError != null -> {
                        Text(
                            text = "error",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFFF6B6B)
                            )
                        )
                    }
                    whoamiResult != null -> {
                        Text(
                            text = "configured ($whoamiResult)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    ngrokUrl.isBlank() || apiToken.isBlank() -> {
                        Text(
                            text = "pending",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFFFD93D)
                            )
                        )
                    }
                    else -> {
                        Text(
                            text = "unknown",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Estado de Spotify
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "    > sptfy:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    )
                )
                
                Text(
                    text = if (isSpotifyConnected) "connected" else "disconnected",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (isSpotifyConnected) Color(0xFF1DB954) else Color(0xFF95A5A6)
                    )
                )
            }
        }
        
        //Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun PlaylistsScreen(
    context: Context,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    
    // Estado para las playlists y autenticación
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    
    // Estado para mostrar tracks de una playlist
    var selectedPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var playlistTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    
    // Función para obtener un token válido
    fun getValidAccessToken(callback: (String?) -> Unit) {
        val accessToken = Config.getSpotifyAccessToken(context)
        if (accessToken != null) {
            callback(accessToken)
            return
        }
        
        val refreshToken = Config.getSpotifyRefreshToken(context)
        if (refreshToken != null) {
            SpotifyRepository.refreshAccessToken(refreshToken) { newToken, refreshError ->
                if (newToken != null) {
                    Config.setSpotifyTokens(context, newToken, refreshToken, 3600)
                    callback(newToken)
                } else {
                    error = "Error renovando token: $refreshError"
                    Config.clearSpotifyTokens(context)
                    isSpotifyConnected = false
                    callback(null)
                }
            }
        } else {
            callback(null)
        }
    }
    
    // Función para cargar playlists
    fun loadPlaylists() {
        if (!isSpotifyConnected) {
            error = "Spotify no está conectado"
            return
        }
        
        isLoading = true
        error = null
        
        getValidAccessToken { token ->
            if (token != null) {
                SpotifyRepository.getUserPlaylists(token) { playlistList, playlistError ->
                    isLoading = false
                    if (playlistError != null) {
                        error = playlistError
                        if (playlistError.contains("401") || playlistError.contains("403")) {
                            Config.clearSpotifyTokens(context)
                            isSpotifyConnected = false
                        }
                    } else if (playlistList != null) {
                        playlists = playlistList
                    }
                }
            } else {
                isLoading = false
                error = "No se pudo obtener token de acceso"
            }
        }
    }
    
    // Función para cargar tracks de una playlist
    fun loadPlaylistTracks(playlist: SpotifyPlaylist) {
        selectedPlaylist = playlist
        isLoadingTracks = true
        
        getValidAccessToken { token ->
            if (token != null) {
                SpotifyRepository.getPlaylistTracks(token, playlist.id) { tracks, tracksError ->
                    isLoadingTracks = false
                    if (tracksError != null) {
                        error = tracksError
                    } else if (tracks != null) {
                        playlistTracks = tracks
                    }
                }
            } else {
                isLoadingTracks = false
                error = "No se pudo obtener token de acceso"
            }
        }
    }
    
    // Cargar playlists al iniciar si está conectado
    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected && playlists.isEmpty()) {
            loadPlaylists()
        }
    }
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header con detección de deslizamiento para volver
        Text(
            text = if (selectedPlaylist == null) "$ plyr_lists" else "$ ${selectedPlaylist!!.name}",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .offset(x = dragOffsetX.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetX) > 100 && dragOffsetX > 0) {
                                if (selectedPlaylist != null) {
                                    selectedPlaylist = null
                                    playlistTracks = emptyList()
                                } else {
                                    onBack()
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffsetX = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffsetX += dragAmount / density
                    }
                }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when {
            !isSpotifyConnected -> {
                // Estado no conectado
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "● ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                    Text(
                        text = "$ spotify_not_connected",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Conecta tu cuenta en Config primero",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
            
            selectedPlaylist != null -> {
                // Vista de tracks de playlist
                if (isLoadingTracks) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "● ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFD93D)
                            )
                        )
                        Text(
                            text = "$ loading_tracks...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlistTracks) { track ->
                            TrackItem(track = track)
                        }
                    }
                }
            }
            
            else -> {
                // Vista principal de playlists
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "● ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFD93D)
                            )
                        )
                        Text(
                            text = "$ loading_playlists...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                } else if (playlists.isEmpty() && error == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$ no_playlists_found",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(playlists) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                onClick = {
                                    loadPlaylistTracks(playlist)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Mostrar error si existe
        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ERR: $it",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B6B)
                )
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: SpotifyPlaylist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de playlist
        Text(
            text = "♫ ",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                color = Color(0xFF1DB954)
            )
        )
        
        // Información de la playlist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            MarqueeText(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = playlist.getTrackCount(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF95A5A6)
                )
            )
        }
        
        // Flecha indicadora
        Text(
            text = ">",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = Color(0xFF95A5A6)
            )
        )
    }
}

@Composable
fun TrackItem(
    track: SpotifyTrack
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de track
        Text(
            text = "♪ ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFF1DB954)
            )
        )
        
        // Información del track
        MarqueeText(
            text = track.getDisplayName(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f)
        )
    }
}