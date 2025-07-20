package com.plyr.ui

import android.content.Context
import android.util.Log
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
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.PlaylistEntity
import com.plyr.database.TrackEntity
import com.plyr.database.toSpotifyPlaylist
import com.plyr.database.toSpotifyTrack
import com.plyr.viewmodel.PlayerViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.asFlow
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
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.plyr.service.YouTubeSearchManager

// Estados para navegación
enum class Screen {
    MAIN,
    CONFIG,
    PLAYLISTS,
    BACKEND_CONFIG
}

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onThemeChanged: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null
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
            onOpenPlaylists = { currentScreen = Screen.PLAYLISTS },
            onOpenBackendConfig = { currentScreen = Screen.BACKEND_CONFIG }
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { currentScreen = Screen.MAIN },
            playerViewModel = playerViewModel
        )
        Screen.BACKEND_CONFIG -> BackendConfigScreen(
            context = context,
            onBack = { currentScreen = Screen.CONFIG }
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
    
    // YouTube search manager para búsquedas locales
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
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

                        // Usar NewPipe para buscar videos
                        coroutineScope.launch {
                            try {
                                // Buscar videos con información detallada usando NewPipe
                                val videosInfo = youtubeSearchManager.searchYouTubeVideosDetailed(searchQuery, 10)
                                
                                // Convertir a AudioItem para compatibilidad con la UI existente
                                val audioItems = videosInfo.map { videoInfo ->
                                    AudioItem(
                                        title = "${videoInfo.title} - ${videoInfo.uploader}",
                                        url = "https://www.youtube.com/watch?v=${videoInfo.videoId}"
                                    )
                                }
                                
                                isLoading = false
                                results = audioItems
                                
                                if (audioItems.isEmpty()) {
                                    error = "No se encontraron videos para: $searchQuery"
                                }
                                
                            } catch (e: Exception) {
                                isLoading = false
                                error = "Error buscando videos: ${e.message}"
                                Log.e("MainScreen", "Error en búsqueda", e)
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
                                
                                // Guardar el ID de YouTube en la base de datos para búsquedas futuras
                                coroutineScope.launch {
                                    try {
                                        // Crear un track temporal para búsquedas manuales
                                        val searchTrack = TrackEntity(
                                            id = "search_${System.currentTimeMillis()}", // ID único para búsquedas
                                            playlistId = "manual_search", // Playlist especial para búsquedas manuales
                                            spotifyTrackId = id, // Usar el YouTube ID como referencia
                                            name = item.title.substringBefore(" - "), // Título sin el canal
                                            artists = item.title.substringAfter(" - ", "YouTube"), // Canal como artista
                                            youtubeVideoId = id, // Guardar el ID de YouTube
                                            position = 0, // Posición por defecto
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                        
                                        // Opcionalmente, guardar en base de datos para futuras referencias
                                        // localRepository.insertTrack(searchTrack)
                                        
                                        Log.d("MainScreen", "Video seleccionado - ID: $id, Título: ${item.title}")
                                    } catch (e: Exception) {
                                        Log.e("MainScreen", "Error guardando referencia de búsqueda", e)
                                    }
                                }
                                
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
    onOpenPlaylists: () -> Unit,
    onOpenBackendConfig: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(Config.getTheme(context)) }
    
    // Estado para Spotify
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf("") }
    
    // Estado para mostrar el estado de plyr (solo lectura)
    var plyrStatus by remember { mutableStateOf("unknown") }
    
    // Verificar el estado de plyr
    LaunchedEffect(Unit) {
        val ngrokUrl = Config.getNgrokUrl(context)
        val apiToken = Config.getApiToken(context)
        
        if (ngrokUrl.isNotBlank() && apiToken.isNotBlank()) {
            plyrStatus = "checking..."
            AudioRepository.whoami(ngrokUrl, apiToken) { user, error ->
                plyrStatus = if (error != null) {
                    "error"
                } else if (user != null) {
                    "configured ($user)"
                } else {
                    "unknown"
                }
            }
        } else {
            plyrStatus = "pending"
        }
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
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // Información de uso
        Column {
            Text(
                text = "$ info",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp, // Tamaño aumentado
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "• considera pagarme algo por esto, porfa\n• si tienes esta apk y no me conoces alguien tiene unos cojones muy grandes",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp, // Tamaño aumentado
                    color = Color(0xFF95A5A6)
                ),
                lineHeight = 18.sp
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
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
                text = "$ status",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Estado de plyr (clickeable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        onOpenBackendConfig()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(vertical = 4.dp),
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
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicador de estado
                    Text(
                        text = when (plyrStatus) {
                            "checking..." -> "⏳ "
                            "error" -> "✗ "
                            "pending" -> "○ "
                            else -> if (plyrStatus.startsWith("configured")) "✓ " else "○ "
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = when (plyrStatus) {
                                "checking..." -> Color(0xFFFFD93D)
                                "error" -> Color(0xFFFF6B6B)
                                "pending" -> Color(0xFFFFD93D)
                                else -> if (plyrStatus.startsWith("configured")) MaterialTheme.colorScheme.primary else Color(0xFF95A5A6)
                            }
                        )
                    )
                    
                    // Estado de conexión
                    Text(
                        text = plyrStatus,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = when (plyrStatus) {
                                "checking..." -> Color(0xFFFFD93D)
                                "error" -> Color(0xFFFF6B6B)
                                "pending" -> Color(0xFFFFD93D)
                                else -> if (plyrStatus.startsWith("configured")) MaterialTheme.colorScheme.primary else Color(0xFF95A5A6)
                            }
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
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
                    .padding(vertical = 4.dp),
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
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicador de estado
                    Text(
                        text = when {
                            isConnecting -> "⏳ "
                            isSpotifyConnected -> "✓ "
                            else -> "○ "
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = when {
                                isConnecting -> Color(0xFFFFD93D)
                                isSpotifyConnected -> Color(0xFF1DB954)
                                else -> Color(0xFF95A5A6)
                            }
                        )
                    )
                    
                    // Estado de conexión
                    Text(
                        text = when {
                            connectionMessage.isNotEmpty() -> connectionMessage
                            isSpotifyConnected -> "connected"
                            else -> "disconnected"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = if (isSpotifyConnected) Color(0xFF1DB954) else Color(0xFF95A5A6)
                        )
                    )
                }
            }
        }
        
        //Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun PlaylistsScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val haptic = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }
    
    // Repositorio local y manager de búsqueda
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Estado para las playlists y autenticación
    val playlistsFromDB by localRepository.getAllPlaylistsLiveData().asFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // Convertir entidades a SpotifyPlaylist para compatibilidad con UI existente
    val playlists = playlistsFromDB.map { it.toSpotifyPlaylist() }
    
    // Estado para mostrar tracks de una playlist
    var selectedPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var selectedPlaylistEntity by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    var isSearchingYouTubeIds by remember { mutableStateOf(false) }
    
    // Tracks observados desde la base de datos
    val tracksFromDB by if (selectedPlaylistEntity != null) {
        localRepository.getTracksByPlaylistLiveData(selectedPlaylistEntity!!.spotifyId)
            .asFlow()
            .collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { mutableStateOf(emptyList<TrackEntity>()) }
    }
    
    // Actualizar tracks cuando cambien en la DB
    LaunchedEffect(tracksFromDB) {
        if (selectedPlaylistEntity != null) {
            playlistTracks = tracksFromDB.map { it.toSpotifyTrack() }
        }
    }
    
    // Función para cargar playlists con sincronización automática
    val loadPlaylists = {
        if (!isSpotifyConnected) {
            error = "Spotify no está conectado"
        } else {
            isLoading = true
            error = null
            
            // Usar corrutina para operaciones asíncronas
            coroutineScope.launch {
                try {
                    val playlistEntities = localRepository.getPlaylistsWithAutoSync()
                    isLoading = false
                    // Las playlists se actualizan automáticamente a través del LiveData
                } catch (e: Exception) {
                    isLoading = false
                    error = "Error cargando playlists: ${e.message}"
                }
            }
        }
    }
    
    // Función para cargar tracks de una playlist
    val loadPlaylistTracks: (SpotifyPlaylist) -> Unit = { playlist ->
        selectedPlaylist = playlist
        selectedPlaylistEntity = playlistsFromDB.find { it.spotifyId == playlist.id }
        isLoadingTracks = true
        
        if (selectedPlaylistEntity == null) {
            isLoadingTracks = false
            error = "Playlist no encontrada en base de datos local"
        } else {
            // Usar corrutina para operaciones asíncronas
            coroutineScope.launch {
                try {
                    val trackEntities = localRepository.getTracksWithAutoSync(playlist.id)
                    isLoadingTracks = false
                    // Los tracks se actualizan automáticamente a través del LiveData
                    
                    // NOTA: Ya no se necesita búsqueda masiva de YouTube IDs
                    // Los IDs se obtienen automáticamente cuando el usuario hace click en cada canción
                    Log.d("PlaylistScreen", "✅ Tracks cargados para playlist: ${playlist.name}. IDs de YouTube se obtendrán bajo demanda.")
                } catch (e: Exception) {
                    isLoadingTracks = false
                    error = "Error cargando tracks: ${e.message}"
                }
            }
        }
    }
    
    // Función para forzar sincronización completa
    val forceSyncAll = {
        if (!isSpotifyConnected) {
            error = "Spotify no está conectado"
        } else {
            isSyncing = true
            error = null
            
            coroutineScope.launch {
                try {
                    val success = localRepository.forceSyncAll()
                    isSyncing = false
                    if (!success) {
                        error = "Error en la sincronización"
                    }
                } catch (e: Exception) {
                    isSyncing = false
                    error = "Error en sincronización: ${e.message}"
                }
            }
        }
    }
    
    // Cargar playlists al iniciar si está conectado
    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected) {
            loadPlaylists()
        }
    }
    
    // Cleanup del YouTubeSearchManager
    DisposableEffect(Unit) {
        onDispose {
            youtubeSearchManager.cleanup()
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
        
        // Botón de sincronización manual (solo visible si está conectado y no es una playlist individual)
        if (isSpotifyConnected && selectedPlaylist == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botón de sincronización
                Text(
                    text = if (isSyncing) "<syncing...>" else "<sync>",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (isSyncing) Color(0xFFFFD93D) else Color(0xFF4ECDC4)
                    ),
                    modifier = Modifier
                        .clickable(enabled = !isSyncing) { 
                            forceSyncAll()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(8.dp)
                )
                
                // Indicador de estado
                Text(
                    text = when {
                        isSyncing -> "Sincronizando..."
                        playlists.isNotEmpty() -> "${playlists.size} playlists"
                        else -> "Sin datos locales"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        
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
                    // Estados para los botones de control
                    var isRandomizing by remember { mutableStateOf(false) }
                    var isStarting by remember { mutableStateOf(false) }
                    var randomJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    var startJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    
                    // Función para parar todas las reproducciones
                    fun stopAllPlayback() {
                        isRandomizing = false
                        isStarting = false
                        randomJob?.cancel()
                        startJob?.cancel()
                        randomJob = null
                        startJob = null
                        // Pausar el reproductor actual
                        playerViewModel?.pausePlayer()
                    }
                    
                    // Función para esperar a que termine una canción usando ExoPlayer listeners
                    suspend fun waitForSongToFinish(playerViewModel: PlayerViewModel): Boolean {
                        return try {
                            println("⏳ Esperando a que la canción termine usando listeners de ExoPlayer...")
                            
                            // Esperar un poco para que ExoPlayer se estabilice
                            kotlinx.coroutines.delay(2000)
                            
                            // Usar la nueva función del PlayerViewModel que tiene listeners
                            val finished = playerViewModel.waitForCurrentSongToFinish()
                            
                            if (finished) {
                                println("✅ Canción terminada, pasando a la siguiente")
                            } else {
                                println("⚠️ Canción cancelada o error, pasando a la siguiente")
                            }
                            
                            true // Siempre continuar a la siguiente canción
                            
                        } catch (e: Exception) {
                            println("❌ Error esperando fin de canción: ${e.message}")
                            true // En caso de error, continuar con la siguiente canción
                        }
                    }
                    
                    // Función para randomización
                    fun startRandomizing() {
                        stopAllPlayback()
                        isRandomizing = true
                        
                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            randomJob = kotlinx.coroutines.GlobalScope.launch {
                                while (isRandomizing) {
                                    val randomTrack = playlistTracks.random()
                                    val trackEntity = tracksFromDB.find { it.spotifyTrackId == randomTrack.id }
                                    
                                    println("🎵 RANDOM [${selectedPlaylist!!.name}]: ${randomTrack.getDisplayName()}")
                                    
                                    if (trackEntity != null) {
                                        // Reproducir la canción usando PlayerViewModel
                                        playerViewModel.initializePlayer()
                                        val loadSuccess = playerViewModel.loadAudioFromTrack(trackEntity)
                                        
                                        if (loadSuccess) {
                                            // Solo esperar si la carga fue exitosa
                                            waitForSongToFinish(playerViewModel)
                                        } else {
                                            println("⚠️ Error cargando audio para: ${randomTrack.getDisplayName()}")
                                            kotlinx.coroutines.delay(2000) // Esperar antes de la siguiente
                                        }
                                    } else {
                                        println("⚠️ TrackEntity no encontrado para: ${randomTrack.getDisplayName()}")
                                        kotlinx.coroutines.delay(2000) // Esperar 2 segundos antes de intentar la siguiente
                                    }
                                }
                            }
                        }
                    }
                    
                    // Función para reproducción ordenada
                    fun startOrderedPlayback() {
                        stopAllPlayback()
                        isStarting = true
                        
                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            startJob = kotlinx.coroutines.GlobalScope.launch {
                                var currentIndex = 0
                                while (isStarting && currentIndex < playlistTracks.size) {
                                    val track = playlistTracks[currentIndex]
                                    val trackEntity = tracksFromDB.find { it.spotifyTrackId == track.id }
                                    
                                    println("🎵 START [${selectedPlaylist!!.name}] ${currentIndex + 1}/${playlistTracks.size}: ${track.getDisplayName()}")
                                    
                                    if (trackEntity != null) {
                                        // Reproducir la canción usando PlayerViewModel
                                        playerViewModel.initializePlayer()
                                        val loadSuccess = playerViewModel.loadAudioFromTrack(trackEntity)
                                        
                                        if (loadSuccess) {
                                            // Solo esperar si la carga fue exitosa
                                            waitForSongToFinish(playerViewModel)
                                        } else {
                                            println("⚠️ Error cargando audio para: ${track.getDisplayName()}")
                                            kotlinx.coroutines.delay(2000) // Esperar antes de la siguiente
                                        }
                                    } else {
                                        println("⚠️ TrackEntity no encontrado para: ${track.getDisplayName()}")
                                        kotlinx.coroutines.delay(2000) // Esperar 2 segundos antes de intentar la siguiente
                                    }
                                    
                                    currentIndex++
                                    if (currentIndex >= playlistTracks.size) {
                                        currentIndex = 0 // Reiniciar desde el principio
                                    }
                                }
                            }
                        }
                    }
                    
                    // Limpiar jobs al salir
                    DisposableEffect(selectedPlaylist) {
                        onDispose {
                            randomJob?.cancel()
                            startJob?.cancel()
                        }
                    }
                    
                    Column {
                        // Botones de control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Botón <start>
                            Text(
                                text = if (isStarting) "<stop>" else "<start>",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    color = if (isStarting) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        if (isStarting) {
                                            stopAllPlayback()
                                        } else {
                                            startOrderedPlayback()
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(8.dp)
                            )
                            
                            // Botón <rand>
                            Text(
                                text = if (isRandomizing) "<stop>" else "<rand>",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    color = if (isRandomizing) Color(0xFFFF6B6B) else Color(0xFFFFD93D)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        if (isRandomizing) {
                                            stopAllPlayback()
                                        } else {
                                            startRandomizing()
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(8.dp)
                            )
                        }
                        // Lista de tracks
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlistTracks.size) { index ->
                                val track = playlistTracks[index]
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == track.id }
                                TrackItem(
                                    track = track,
                                    trackEntity = trackEntity,
                                    playerViewModel = playerViewModel
                                )
                            }
                        }
                    }
                }
            }
            
            else -> {
                // Vista principal de playlists
                if (isLoading || isSyncing) {
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
                            text = if (isSyncing) "$ syncing_from_spotify..." else "$ loading_playlists...",
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
            .padding(vertical = 12.dp, horizontal = 4.dp), // Padding aumentado
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de playlist (cambiado a >)
        Text(
            text = "> ",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp, // Tamaño aumentado
                color = Color(0xFF4ECDC4) // Color terminal como en main
            )
        )
        
        // Nombre de la playlist (solo el nombre, sin contador)
        MarqueeText(
            text = playlist.name,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp, // Tamaño aumentado
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TrackItem(
    track: SpotifyTrack,
    trackEntity: TrackEntity? = null,
    playerViewModel: PlayerViewModel? = null
) {
    val haptic = LocalHapticFeedback.current
    val hasYouTubeId = trackEntity?.youtubeVideoId != null
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                println("🎵 TRACK CLICKED: ${track.getDisplayName()}")
                
                // Reproducir usando PlayerViewModel de forma transparente
                // Si el track no tiene YouTube ID, se buscará automáticamente y se guardará localmente
                // El usuario no verá diferencia entre tener el ID guardado o no
                if (trackEntity != null && playerViewModel != null) {
                    println("✅ Iniciando reproducción transparente para: ${track.getDisplayName()}")
                    playerViewModel.initializePlayer()
                    
                    // Para clicks individuales, lanzar en una corrutina para manejar la carga asíncrona
                    kotlinx.coroutines.GlobalScope.launch {
                        val success = playerViewModel.loadAudioFromTrack(trackEntity)
                        if (!success) {
                            println("⚠️ Error cargando audio para: ${track.getDisplayName()}")
                        }
                    }
                } else {
                    println("⚠️ PlayerViewModel no disponible para: ${track.getDisplayName()}")
                }
                
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            .padding(vertical = 6.dp, horizontal = 4.dp), // Padding aumentado para mejor touch
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de track con indicador de estado
        Text(
            text = if (hasYouTubeId) "> " else "> ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = if (hasYouTubeId) Color(0xFF1DB954) else Color(0xFF4ECDC4) // Verde si tiene YouTube ID
            )
        )
        
        // Información del track
        MarqueeText(
            text = track.getDisplayName(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = if (hasYouTubeId) MaterialTheme.colorScheme.onSurface else Color(0xFF95A5A6)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun BackendConfigScreen(
    context: Context,
    onBack: () -> Unit
) {
    var ngrokUrl by remember { mutableStateOf(Config.getNgrokUrl(context)) }
    var apiToken by remember { mutableStateOf(Config.getApiToken(context)) }
    
    // Estado para el resultado de whoami
    var whoamiResult by remember { mutableStateOf<String?>(null) }
    var whoamiError by remember { mutableStateOf<String?>(null) }
    var isCheckingAuth by remember { mutableStateOf(false) }
    var showSaveMessage by remember { mutableStateOf(false) }
    
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
    
    // Función para guardar configuración
    fun saveConfig() {
        Config.setNgrokUrl(context, ngrokUrl)
        Config.setApiToken(context, apiToken)
        showSaveMessage = true
        checkAuth()
    }
    
    // Ocultar mensaje de guardado después de un tiempo
    LaunchedEffect(showSaveMessage) {
        if (showSaveMessage) {
            kotlinx.coroutines.delay(2000)
            showSaveMessage = false
        }
    }
    
    val haptic = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp) // Padding aumentado
    ) {
        // Header con detección de deslizamiento
        Text(
            text = "$ backend_config",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 22.sp, // Tamaño aumentado
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .padding(bottom = 20.dp)
                .offset(x = dragOffsetX.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetX) > 100 && dragOffsetX > 0) {
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
        
        // Campo Ngrok URL
        Text(
            text = "> ngrok_url",
            style = MaterialTheme.typography.bodyLarge.copy( // Tamaño aumentado
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        OutlinedTextField(
            value = ngrokUrl,
            onValueChange = { ngrokUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp), // Altura aumentada para mejor touch
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy( // Tamaño aumentado
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            ),
            placeholder = {
                Text(
                    "https://abc123.ngrok.io",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Campo API Token
        Text(
            text = "> api_token",
            style = MaterialTheme.typography.bodyLarge.copy( // Tamaño aumentado
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp), // Altura aumentada para mejor touch
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy( // Tamaño aumentado
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            ),
            placeholder = {
                Text(
                    "token_abc123xyz",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Botón de guardar con mayor tamaño
        Button(
            onClick = {
                saveConfig()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // Altura aumentada para mejor touch
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "$ save_config",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Botón de test con mayor tamaño
        OutlinedButton(
            onClick = {
                checkAuth()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp), // Altura aumentada para mejor touch
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text(
                text = "$ test_connection",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp
                )
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Status section
        Column {
            Text(
                text = "$ status",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Estado de conexión
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "    > connection:",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF95A5A6)
                    )
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicador de estado
                    Text(
                        text = when {
                            isCheckingAuth -> "⏳ "
                            whoamiError != null -> "✗ "
                            whoamiResult != null -> "✓ "
                            ngrokUrl.isBlank() || apiToken.isBlank() -> "○ "
                            else -> "○ "
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = when {
                                isCheckingAuth -> Color(0xFFFFD93D)
                                whoamiError != null -> Color(0xFFFF6B6B)
                                whoamiResult != null -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFF95A5A6)
                            }
                        )
                    )
                    
                    // Estado de conexión
                    Text(
                        text = when {
                            isCheckingAuth -> "checking..."
                            whoamiError != null -> "error"
                            whoamiResult != null -> "ok ($whoamiResult)"
                            ngrokUrl.isBlank() || apiToken.isBlank() -> "incomplete"
                            else -> "unknown"
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = when {
                                isCheckingAuth -> Color(0xFFFFD93D)
                                whoamiError != null -> Color(0xFFFF6B6B)
                                whoamiResult != null -> MaterialTheme.colorScheme.primary
                                else -> Color(0xFF95A5A6)
                            }
                        )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mensaje de guardado
        if (showSaveMessage) {
            Text(
                text = "✓ configuración guardada",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Mostrar error si existe
        whoamiError?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ERR: $error",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFFFF6B6B)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Información de ayuda
        Column {
            Text(
                text = "$ help",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = "• Configura tu servidor backend plyr\n• URL: endpoint público de tu servidor\n• Token: clave de autenticación API\n• Usa 'test_connection' para verificar",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF95A5A6)
                ),
                lineHeight = 20.sp
            )
        }
    }
}