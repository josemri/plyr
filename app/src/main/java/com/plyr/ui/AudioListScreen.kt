package com.plyr.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.plyr.model.AudioItem
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    HOME,
    SEARCH,
    QUEUE,
    CONFIG,
    PLAYLISTS
}

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onThemeChanged: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }
    
    // Handle back button - always go to HOME, never exit app
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }
    
    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            context = context,
            onNavigateToScreen = { screen -> currentScreen = screen }
        )
        Screen.SEARCH -> SearchScreen(
            context = context,
            onVideoSelected = onVideoSelected,
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
        Screen.QUEUE -> QueueScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
        Screen.CONFIG -> ConfigScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME },
            onThemeChanged = onThemeChanged
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
    }
}

@Composable
fun HomeScreen(
    context: Context,
    onNavigateToScreen: (Screen) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var backPressedTime by remember { mutableStateOf(0L) }
    var showExitMessage by remember { mutableStateOf(false) }
    
    // Handle double back press to exit
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime > 2000) {
            backPressedTime = currentTime
            showExitMessage = true
            // Hide message after 2 seconds
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(2000)
                showExitMessage = false
            }
        } else {
            // Exit app
            (context as? android.app.Activity)?.finish()
        }
    }
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Terminal-style header
        Text(
            text = "$ plyr_home",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Lista de ventanas disponibles
        val windows = listOf(
            Triple(Screen.SEARCH, "> search_audio", "Search for music from Spotify or YouTube"),
            Triple(Screen.QUEUE, "> queue_manager", "Manage playback queue and current playlist"),
            Triple(Screen.PLAYLISTS, "> playlists", "Browse and manage your saved playlists"),
            Triple(Screen.CONFIG, "> settings", "Configure app preferences and connections")
        )
        
        windows.forEach { (screen, title, description) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToScreen(screen)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C3E50).copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF4ECDC4)
                        )
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))

        // Exit message
        if (showExitMessage) {
            Text(
                text = "> Press back again to exit",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE74C3C)
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun SearchScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Estados para resultados de Spotify
    var spotifyResults by remember { mutableStateOf<com.plyr.network.SpotifySearchAllResponse?>(null) }
    var showSpotifyResults by remember { mutableStateOf(false) }
    
    // YouTube search manager para búsquedas locales
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val haptic = LocalHapticFeedback.current

    // Handle back button
    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "$ plyr_search",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
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
                        spotifyResults = null
                        showSpotifyResults = false
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

                        coroutineScope.launch {
                            try {
                                val searchEngine = Config.getSearchEngine(context)
                                
                                // Permitir override temporal con prefijos
                                val (finalSearchEngine, finalQuery) = when {
                                    searchQuery.startsWith("yt:", ignoreCase = true) -> {
                                        "youtube" to searchQuery.substring(3).trim()
                                    }
                                    searchQuery.startsWith("sp:", ignoreCase = true) -> {
                                        "spotify" to searchQuery.substring(3).trim()
                                    }
                                    else -> searchEngine to searchQuery
                                }
                                
                                if (finalQuery.isEmpty()) {
                                    isLoading = false
                                    error = "Búsqueda vacía después del prefijo"
                                    return@launch
                                }
                                
                                when (finalSearchEngine) {
                                    "youtube" -> {
                                        // Búsqueda de YouTube
                                        val videosInfo = youtubeSearchManager.searchYouTubeVideosDetailed(finalQuery, 10)
                                        
                                        // Convertir a AudioItem para compatibilidad con la UI existente
                                        val audioItems = videosInfo.map { videoInfo ->
                                            AudioItem(
                                                title = "${videoInfo.title} - ${videoInfo.uploader}",
                                                url = "https://www.youtube.com/watch?v=${videoInfo.videoId}"
                                            )
                                        }
                                        
                                        isLoading = false
                                        results = audioItems
                                        spotifyResults = null
                                        showSpotifyResults = false
                                        
                                        if (audioItems.isEmpty()) {
                                            error = "No se encontraron videos de YouTube para: $finalQuery"
                                        }
                                    }
                                    
                                    "spotify" -> {
                                        // Búsqueda de Spotify
                                        val accessToken = Config.getSpotifyAccessToken(context)
                                        if (accessToken != null) {
                                            SpotifyRepository.searchAll(accessToken, finalQuery) { searchAllResponse, errorMsg ->
                                                if (searchAllResponse != null) {
                                                    Log.d("MainScreen", "Spotify search all results:")
                                                    Log.d("MainScreen", "- Tracks: ${searchAllResponse.tracks.items.size}")
                                                    Log.d("MainScreen", "- Albums: ${searchAllResponse.albums.items.size}")
                                                    Log.d("MainScreen", "- Artists: ${searchAllResponse.artists.items.size}")
                                                    Log.d("MainScreen", "- Playlists: ${searchAllResponse.playlists.items.size}")
                                                    
                                                    isLoading = false
                                                    spotifyResults = searchAllResponse
                                                    showSpotifyResults = true
                                                    results = emptyList() // Limpiar resultados de YouTube
                                                    
                                                } else {
                                                    Log.e("MainScreen", "Error en búsqueda de Spotify: $errorMsg")
                                                    isLoading = false
                                                    error = "Error buscando en Spotify: $errorMsg"
                                                    spotifyResults = null
                                                    showSpotifyResults = false
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                            error = "Token de Spotify no disponible. Conecta tu cuenta en configuración."
                                            spotifyResults = null
                                            showSpotifyResults = false
                                        }
                                    }
                                    
                                    else -> {
                                        isLoading = false
                                        error = "Motor de búsqueda no reconocido: $finalSearchEngine"
                                    }
                                }
                                
                            } catch (e: Exception) {
                                isLoading = false
                                error = "Error en búsqueda: ${e.message}"
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
                    "$ loading...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD93D)
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

        // === MENÚS DESPLEGABLES DE SPOTIFY ===
        if (showSpotifyResults && spotifyResults != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    SpotifyResultsMenus(
                        spotifyResults = spotifyResults!!,
                        onTrackSelected = { track ->
                            Log.d("MainScreen", "Track seleccionado: ${track.name} by ${track.getArtistNames()}")
                        },
                        onAlbumSelected = { album ->
                            Log.d("MainScreen", "Album seleccionado: ${album.name} by ${album.getArtistNames()}")
                            
                            // Obtener tracks del álbum
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    SpotifyRepository.getAlbumTracks(accessToken, album.id) { tracks, error ->
                                        if (tracks != null && tracks.isNotEmpty()) {
                                            Log.d("MainScreen", "Tracks del álbum '${album.name}':")
                                            tracks.forEachIndexed { index, track ->
                                                Log.d("MainScreen", "  ${index + 1}. ${track.name} (${track.getDurationText()})")
                                            }
                                        } else {
                                            Log.e("MainScreen", "❌ Error obteniendo tracks del álbum: $error")
                                        }
                                    }
                                }
                            }
                        },
                        onArtistSelected = { artist ->
                            Log.d("MainScreen", "Artist seleccionado: ${artist.name}")
                            
                            // Obtener álbumes del artista
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    SpotifyRepository.getArtistAlbums(accessToken, artist.id) { albums, error ->
                                        if (albums != null && albums.isNotEmpty()) {
                                            Log.d("MainScreen", "Álbumes/Singles de '${artist.name}':")
                                            albums.forEachIndexed { index, album ->
                                                Log.d("MainScreen", "  ${index + 1}. ${album.name} (${album.release_date ?: "Fecha desconocida"}) - ${album.total_tracks ?: 0} tracks")
                                            }
                                        } else {
                                            Log.e("MainScreen", "❌ Error obteniendo álbumes del artista: $error")
                                        }
                                    }
                                }
                            }
                        },
                        onPlaylistSelected = { playlist ->
                            Log.d("MainScreen", "Playlist seleccionada: ${playlist.name}")
                            
                            // Obtener tracks de la playlist
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    SpotifyRepository.getPlaylistTracks(accessToken, playlist.id) { playlistTracks, error ->
                                        if (playlistTracks != null && playlistTracks.isNotEmpty()) {
                                            Log.d("MainScreen", "Tracks de la playlist '${playlist.name}':")
                                            playlistTracks.forEachIndexed { index, playlistTrack ->
                                                val track = playlistTrack.track
                                                if (track != null) {
                                                    Log.d("MainScreen", "  ${index + 1}. ${track.name} - ${track.getArtistNames()} (${track.getDurationText()})")
                                                } else {
                                                    Log.d("MainScreen", "  ${index + 1}. [Track eliminado o no disponible]")
                                                }
                                            }
                                        } else {
                                            Log.e("MainScreen", "❌ Error obteniendo tracks de la playlist: $error")
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
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
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Área principal clickable (reproducir inmediatamente)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { 
                                    // Limpiar resultados y búsqueda al seleccionar
                                    searchQuery = ""
                                    results = emptyList()
                                    error = null
                                    spotifyResults = null
                                    showSpotifyResults = false
                                    
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
                        }
                        
                        // Botón de cola
                        Text(
                            text = "<queue>",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color(0xFFFF6B6B)
                            ),
                            modifier = Modifier
                                .clickable {
                                    // Agregar a la cola sin reproducir inmediatamente
                                    coroutineScope.launch {
                                        try {
                                            // Crear un track para la cola
                                            val queueTrack = TrackEntity(
                                                id = "queue_${System.currentTimeMillis()}", // ID único para cola
                                                playlistId = "queue", // Playlist especial para cola
                                                spotifyTrackId = id, // Usar el YouTube ID como referencia
                                                name = item.title.substringBefore(" - "), // Título sin el canal
                                                artists = item.title.substringAfter(" - ", "YouTube"), // Canal como artista
                                                youtubeVideoId = id, // Guardar el ID de YouTube
                                                position = 0, // Se ajustará en la cola
                                                lastSyncTime = System.currentTimeMillis()
                                            )
                                            
                                            // Agregar a la cola del PlayerViewModel
                                            playerViewModel?.addToQueue(queueTrack)
                                            
                                            Log.d("MainScreen", "Track agregado a la cola - ID: $id, Título: ${item.title}")
                                        } catch (e: Exception) {
                                            Log.e("MainScreen", "Error agregando a la cola", e)
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // Handle back button
    BackHandler {
        onBack()
    }
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "$ queue_manager",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Contenido de la cola
        if (playerViewModel != null) {
            val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
            val currentQueue = queueState.queue
            
            if (currentQueue.isNotEmpty()) {
                // Header de la cola
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current queue [${currentQueue.size}]",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = Color(0xFFFFD93D)
                        )
                    )
                    
                    // Botón para limpiar la cola
                    TextButton(
                        onClick = { 
                            playerViewModel.clearQueue()
                            Log.d("QueueScreen", "Cola limpiada por el usuario")
                        }
                    ) {
                        Text(
                            text = "clear",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Lista de tracks en la cola
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(currentQueue.size) { index ->
                        val track = currentQueue[index]
                        val isCurrentTrack = queueState.currentIndex == index
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clickable {
                                    // Reproducir desde este track y activar modo cola para toda la secuencia
                                    coroutineScope.launch {
                                        playerViewModel.playQueueFromIndex(index)
                                    }
                                    Log.d("QueueScreen", "Iniciando cola desde índice: $index")
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Indicador de posición y estado
                            Text(
                                text = if (isCurrentTrack) "♪ " else "${index + 1}. ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = if (isCurrentTrack) Color(0xFF4ECDC4) else Color(0xFF95A5A6)
                                ),
                                modifier = Modifier.width(32.dp)
                            )
                            
                            // Nombre del track
                            MarqueeText(
                                text = track.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = if (isCurrentTrack) Color(0xFFE0E0E0) else Color(0xFFBDC3C7)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Botón para remover de la cola
                            TextButton(
                                onClick = { 
                                    playerViewModel.removeFromQueue(index)
                                    Log.d("QueueScreen", "Track removido de la cola en índice: $index")
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp,
                                        color = Color(0xFF95A5A6)
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Estado vacío
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
                    )
                    
                    Text(
                        text = "Add tracks from search to start playing",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7F8C8D)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            // PlayerViewModel no disponible
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Player not available",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    )
                )
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
                fontSize = 20.sp,
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
                text = "    ● don't pirate music!",
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
    
    // Manejar botón de retroceso del sistema
    BackHandler {
        if (selectedPlaylist != null) {
            selectedPlaylist = null
            playlistTracks = emptyList()
        } else {
            onBack()
        }
    }
    
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = if (selectedPlaylist == null) "$ plyr_lists" else "$ ${selectedPlaylist!!.name}",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
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
                        // Cancelar espera de canción y pausar el reproductor
                        playerViewModel?.cancelWaitForSong()
                        playerViewModel?.pausePlayer()
                    }
                    
                    
                    // Función para randomización simplificada - solo reproduce un track aleatorio
                    fun startRandomizing() {
                        stopAllPlayback()
                        isRandomizing = true
                        
                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            randomJob = kotlinx.coroutines.GlobalScope.launch {
                                val randomTrack = playlistTracks.random()
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == randomTrack.id }
                                
                                println("� RANDOM: ${randomTrack.getDisplayName()}")
                                
                                if (trackEntity != null && playerViewModel != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel.initializePlayer()
                                    
                                    // Establecer la playlist completa con el track aleatorio seleccionado
                                    val currentTrackIndex = tracksFromDB.indexOf(trackEntity)
                                    if (currentTrackIndex >= 0) {
                                        playerViewModel.setCurrentPlaylist(tracksFromDB, currentTrackIndex)
                                    }
                                    
                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel.loadAudioFromTrack(trackEntity)
                                } else {
                                    println("⚠️ TrackEntity no encontrado para: ${randomTrack.getDisplayName()}")
                                }
                                
                                isRandomizing = false
                            }
                        }
                    }
                    
                    // Función para reproducción ordenada simplificada - solo inicia desde el primer track
                    fun startOrderedPlayback() {
                        stopAllPlayback()
                        isStarting = true
                        
                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            startJob = kotlinx.coroutines.GlobalScope.launch {
                                val firstTrack = playlistTracks.first()
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == firstTrack.id }
                                
                                println("🎵 START [${selectedPlaylist!!.name}]: ${firstTrack.getDisplayName()}")
                                
                                if (trackEntity != null && playerViewModel != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel.initializePlayer()
                                    
                                    // Establecer la playlist completa desde el inicio (índice 0)
                                    val trackEntityIndex = tracksFromDB.indexOf(trackEntity)
                                    if (trackEntityIndex >= 0) {
                                        playerViewModel.setCurrentPlaylist(tracksFromDB, trackEntityIndex)
                                    }
                                    
                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel.loadAudioFromTrack(trackEntity)
                                } else {
                                    println("⚠️ TrackEntity no encontrado para: ${firstTrack.getDisplayName()}")
                                }
                                
                                isStarting = false
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Create TrackEntity from SpotifyTrack and play
                                            val trackEntity = TrackEntity(
                                                id = "spotify_${System.currentTimeMillis()}_${track.id}",
                                                playlistId = "spotify_playlist",
                                                spotifyTrackId = track.id,
                                                name = track.name,
                                                artists = track.getArtistNames(),
                                                youtubeVideoId = null, // Will be resolved during playback
                                                position = index,
                                                lastSyncTime = System.currentTimeMillis()
                                            )
                                            
                                            // Add to player queue and play
                                            playerViewModel?.let { viewModel ->
                                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                                    try {
                                                        // Create playlist from current tracks
                                                        val playlistEntities = playlistTracks.mapIndexed { idx, spotifyTrack ->
                                                            TrackEntity(
                                                                id = "spotify_${System.currentTimeMillis()}_${spotifyTrack.id}_$idx",
                                                                playlistId = "spotify_playlist",
                                                                spotifyTrackId = spotifyTrack.id,
                                                                name = spotifyTrack.name,
                                                                artists = spotifyTrack.getArtistNames(),
                                                                youtubeVideoId = null,
                                                                position = idx,
                                                                lastSyncTime = System.currentTimeMillis()
                                                            )
                                                        }
                                                        
                                                        // Set current playlist and play from index
                                                        viewModel.setCurrentPlaylist(playlistEntities, index)
                                                        viewModel.loadAudioFromTrack(trackEntity)
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("PlaylistScreen", "Error playing track: ${e.message}")
                                                    }
                                                }
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}. ",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF95A5A6)
                                        )
                                    )
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE0E0E0)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Text(
                                            text = track.getArtistNames(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF95A5A6)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Text(
                                        text = track.getDurationText(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF95A5A6)
                                        )
                                    )
                                }
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
                                color = Color(0xFFFFD93D)
                            )
                        )
                    }
                } else {
                    // Estado cuando no está cargando ni sincronizando
                    if (playlists.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No playlists found",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF95A5A6)
                                )
                            )
                        }
                    } else {
                        // Lista de playlists
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists.size) { index ->
                                val playlist = playlists[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlaylist = playlist
                                            loadPlaylistTracks(playlist)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "> ",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF4ECDC4)
                                        )
                                    )
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE0E0E0)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Text(
                                            text = playlist.getTrackCount(),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF95A5A6)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyResultsMenus(
    spotifyResults: com.plyr.network.SpotifySearchAllResponse,
    onTrackSelected: (com.plyr.network.SpotifyTrack) -> Unit,
    onAlbumSelected: (com.plyr.network.SpotifyAlbum) -> Unit,
    onArtistSelected: (com.plyr.network.SpotifyArtistFull) -> Unit,
    onPlaylistSelected: (com.plyr.network.SpotifyPlaylist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Tracks section
        if (spotifyResults.tracks.items.isNotEmpty()) {
            Text(
                text = "> tracks (${spotifyResults.tracks.items.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF7FB069)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            spotifyResults.tracks.items.forEach { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackSelected(track) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♪ ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7FB069)
                        )
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = track.getArtistNames(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Text(
                        text = track.getDurationText(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Albums section
        if (spotifyResults.albums.items.isNotEmpty()) {
            Text(
                text = "> albums (${spotifyResults.albums.items.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF7FB069)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            spotifyResults.albums.items.forEach { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumSelected(album) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♫ ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7FB069)
                        )
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = album.getArtistNames(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Text(
                        text = "${album.total_tracks ?: 0} tracks",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Artists section
        if (spotifyResults.artists.items.isNotEmpty()) {
            Text(
                text = "> artists (${spotifyResults.artists.items.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF7FB069)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            spotifyResults.artists.items.forEach { artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistSelected(artist) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♪ ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7FB069)
                        )
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "${artist.followers?.total ?: 0} followers",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                    
                    if (!artist.genres.isNullOrEmpty()) {
                        Text(
                            text = artist.genres.first(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Playlists section
        if (spotifyResults.playlists.items.isNotEmpty()) {
            Text(
                text = "> playlists (${spotifyResults.playlists.items.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF7FB069)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            spotifyResults.playlists.items.forEach { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistSelected(playlist) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "♪ ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7FB069)
                        )
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = playlist.getTrackCount(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
            }
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
