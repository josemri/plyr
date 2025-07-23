package com.plyr.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Stable
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
import com.plyr.network.SpotifyAlbum
import com.plyr.network.SpotifyArtistFull
import com.plyr.network.SpotifySearchAllResponse
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.plyr.service.YouTubeSearchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

// Estados para navegación
enum class Screen {
    HOME,
    SEARCH,
    QUEUE,
    CONFIG,
    PLAYLISTS
}

@Stable
data class MenuOption(val screen: Screen, val title: String)

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
            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                showExitMessage = false
            }
        } else {
            // Exit app
            (context as? Activity)?.finish()
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

        // Lista de opciones disponibles
        val options = remember {
            listOf(
                MenuOption(Screen.SEARCH, "> search"),
                MenuOption(Screen.QUEUE, "> queue"),
                MenuOption(Screen.PLAYLISTS, "> playlists"),
                MenuOption(Screen.CONFIG, "> settings")
            )
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { option ->
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        color = Color.White
                    ),
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(option.screen)
                        }
                        .padding(4.dp)
                )
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
    var spotifyResults by remember { mutableStateOf<SpotifySearchAllResponse?>(null) }
    var showSpotifyResults by remember { mutableStateOf(false) }
    
    // Estados para vista detallada de playlist/álbum
    var selectedSpotifyPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var selectedSpotifyAlbum by remember { mutableStateOf<SpotifyAlbum?>(null) }
    var selectedItemTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    
    // YouTube search manager para búsquedas locales
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    val haptic = LocalHapticFeedback.current

    // Search function
    val performSearch: (String) -> Unit = { searchQuery ->
        if (searchQuery.isNotBlank() && !isLoading) {
            isLoading = true
            error = null
            results = emptyList()
            spotifyResults = null
            showSpotifyResults = false
            
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
                        error = "Query vacía después de procesar prefijo"
                        return@launch
                    }
                    
                    when (finalSearchEngine) {
                        "youtube" -> {
                            // Search YouTube
                            val youtubeResults = youtubeSearchManager.searchYouTubeVideos(finalQuery)
                            // Convert YouTube IDs to AudioItem objects
                            results = youtubeResults.map { videoId ->
                                AudioItem(
                                    title = "Video $videoId", // We'd need actual title from search results
                                    url = "", // Use empty string for url, required by AudioItem
                                    videoId = videoId,
                                    channel = "Unknown", // We'd need actual channel from search results
                                    duration = "Unknown"
                                )
                            }
                            isLoading = false
                        }
                        
                        "spotify" -> {
                            // Search Spotify
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    Log.d("SearchScreen", "🔍 Iniciando búsqueda con paginación en Spotify: '$finalQuery'")
                                    SpotifyRepository.searchAllWithPagination(accessToken, finalQuery) { searchResults: SpotifySearchAllResponse?, searchError: String? ->
                                        // Asegurar que las actualizaciones se ejecuten en el hilo principal
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            if (searchError != null) {
                                                isLoading = false
                                                error = "Error searching Spotify: $searchError"
                                                Log.e("SearchScreen", "Error searching Spotify: $searchError")
                                            } else if (searchResults != null) {
                                                Log.d("SearchScreen", "✅ Resultados actualizados: ${searchResults.tracks.items.size} tracks, ${searchResults.albums.items.size} albums, ${searchResults.artists.items.size} artists, ${searchResults.playlists.items.size} playlists")
                                                Log.d("SearchScreen", "🔄 Actualizando estado spotifyResults...")
                                                isLoading = false
                                                spotifyResults = searchResults
                                                showSpotifyResults = true
                                                Log.d("SearchScreen", "🔄 Estado actualizado - showSpotifyResults=$showSpotifyResults")
                                            }
                                        }
                                    }
                                } else {
                                    isLoading = false
                                    error = "Token de Spotify no disponible"
                                }
                            } else {
                                isLoading = false
                                error = "Spotify no está conectado"
                            }
                        }
                        
                        else -> {
                            isLoading = false
                            error = "Motor de búsqueda no reconocido: $finalSearchEngine"
                            Log.w("SearchScreen", "Motor de búsqueda no reconocido: $finalSearchEngine")
                        }
                    }
                    
                } catch (e: Exception) {
                    isLoading = false
                    error = "Error en búsqueda: ${e.message}"
                    Log.e("SearchScreen", "Error en búsqueda", e)
                }
            }
        }
    }

    // Funciones auxiliares para operaciones de Spotify
    val saveSpotifyPlaylistToLibrary: () -> Unit = {
        coroutineScope.launch {
            try {
                selectedSpotifyPlaylist?.let { playlist ->
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        Log.d("SearchScreen", "💾 Guardando playlist en biblioteca de Spotify: ${playlist.name}")
                        SpotifyRepository.followPlaylist(accessToken, playlist.id) { success, errorMsg ->
                            if (success) {
                                Log.d("SearchScreen", "✅ Playlist seguida exitosamente: ${playlist.name}")
                            } else {
                                Log.e("SearchScreen", "❌ Error siguiendo playlist: $errorMsg")
                            }
                        }
                    } else {
                        Log.e("SearchScreen", "❌ Token de Spotify no disponible")
                    }
                }
                selectedSpotifyAlbum?.let { album ->
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        Log.d("SearchScreen", "💾 Guardando álbum en biblioteca de Spotify: ${album.name}")
                        SpotifyRepository.saveAlbum(accessToken, album.id) { success, errorMsg ->
                            if (success) {
                                Log.d("SearchScreen", "✅ Álbum guardado exitosamente: ${album.name}")
                            } else {
                                Log.e("SearchScreen", "❌ Error guardando álbum: $errorMsg")
                            }
                        }
                    } else {
                        Log.e("SearchScreen", "❌ Token de Spotify no disponible")
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchScreen", "Error guardando en biblioteca de Spotify", e)
            }
        }
    }
    
    val loadSpotifyPlaylistTracks: (SpotifyPlaylist) -> Unit = { playlist ->
        selectedSpotifyPlaylist = playlist
        selectedSpotifyAlbum = null
        isLoadingTracks = true
        error = null
        selectedItemTracks = emptyList()
        
        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    Log.d("SearchScreen", "🎵 Cargando tracks de la playlist: ${playlist.name}")
                    SpotifyRepository.getPlaylistTracks(accessToken, playlist.id) { playlistTracks, errorMsg ->
                        isLoadingTracks = false
                        if (playlistTracks != null) {
                            // Convertir SpotifyPlaylistTrack a SpotifyTrack
                            val tracks = playlistTracks.mapNotNull { it.track }
                            selectedItemTracks = tracks
                            Log.d("SearchScreen", "✅ ${tracks.size} tracks cargados para la playlist: ${playlist.name}")
                        } else {
                            error = "Error cargando tracks de la playlist: $errorMsg"
                            Log.e("SearchScreen", "❌ Error cargando tracks de playlist: $errorMsg")
                        }
                    }
                } else {
                    isLoadingTracks = false
                    error = "Token de Spotify no disponible"
                    Log.e("SearchScreen", "❌ Token de Spotify no disponible")
                }
            } catch (e: Exception) {
                isLoadingTracks = false
                error = "Error cargando tracks de la playlist: ${e.message}"
                Log.e("SearchScreen", "Error cargando playlist tracks", e)
            }
        }
    }
    
    val loadSpotifyAlbumTracks: (SpotifyAlbum) -> Unit = { album ->
        selectedSpotifyAlbum = album
        selectedSpotifyPlaylist = null
        isLoadingTracks = true
        error = null
        selectedItemTracks = emptyList()
        
        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    Log.d("SearchScreen", "🎵 Cargando tracks del álbum: ${album.name}")
                    SpotifyRepository.getAlbumTracks(accessToken, album.id) { tracks, errorMsg ->
                        isLoadingTracks = false
                        if (tracks != null) {
                            selectedItemTracks = tracks
                            Log.d("SearchScreen", "✅ ${tracks.size} tracks cargados para el álbum: ${album.name}")
                        } else {
                            error = "Error cargando tracks del álbum: $errorMsg"
                            Log.e("SearchScreen", "❌ Error cargando tracks de álbum: $errorMsg")
                        }
                    }
                } else {
                    isLoadingTracks = false
                    error = "Token de Spotify no disponible"
                    Log.e("SearchScreen", "❌ Token de Spotify no disponible")
                }
            } catch (e: Exception) {
                isLoadingTracks = false
                error = "Error cargando tracks del álbum: ${e.message}"
                Log.e("SearchScreen", "Error cargando album tracks", e)
            }
        }
    }

    // Handle back button
    BackHandler {
        when {
            selectedSpotifyPlaylist != null || selectedSpotifyAlbum != null -> {
                // Volver de la vista detallada a los resultados de búsqueda
                selectedSpotifyPlaylist = null
                selectedSpotifyAlbum = null
                selectedItemTracks = emptyList()
            }
            else -> onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mostrar vista detallada o búsqueda normal
        when {
            selectedSpotifyPlaylist != null -> {
                SpotifyPlaylistDetailView(
                    playlist = selectedSpotifyPlaylist!!,
                    tracks = selectedItemTracks,
                    isLoading = isLoadingTracks,
                    error = error,
                    onBack = {
                        selectedSpotifyPlaylist = null
                        selectedItemTracks = emptyList()
                    },
                    onStart = {
                        // Reproducir playlist desde el primer track
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🎵 Iniciando reproducción de la playlist: ${selectedSpotifyPlaylist!!.name}")
                            
                            // Convertir SpotifyTrack a TrackEntity
                            val trackEntities = selectedItemTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyPlaylist!!.id}_${spotifyTrack.id}",
                                    playlistId = selectedSpotifyPlaylist!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null, // Se buscará dinámicamente
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }
                            
                            // Establecer playlist y comenzar reproducción
                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)
                            
                            // Buscar y reproducir el primer track
                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error al reproducir playlist", e)
                                    }
                                }
                            }
                        }
                    },
                    onRandom = {
                        // Reproducir playlist en orden aleatorio
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🔀 Iniciando reproducción aleatoria de la playlist: ${selectedSpotifyPlaylist!!.name}")
                            
                            // Convertir SpotifyTrack a TrackEntity y mezclar
                            val shuffledTracks = selectedItemTracks.shuffled()
                            val trackEntities = shuffledTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyPlaylist!!.id}_${spotifyTrack.id}_shuffled",
                                    playlistId = selectedSpotifyPlaylist!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null, // Se buscará dinámicamente
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }
                            
                            // Establecer playlist mezclada y comenzar reproducción
                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)
                            
                            // Buscar y reproducir el primer track de la lista mezclada
                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error al reproducir playlist aleatoria", e)
                                    }
                                }
                            }
                        }
                    },
                    onSave = saveSpotifyPlaylistToLibrary,
                    playerViewModel = playerViewModel
                )
            }
            selectedSpotifyAlbum != null -> {
                SpotifyAlbumDetailView(
                    album = selectedSpotifyAlbum!!,
                    tracks = selectedItemTracks,
                    isLoading = isLoadingTracks,
                    error = error,
                    onBack = {
                        selectedSpotifyAlbum = null
                        selectedItemTracks = emptyList()
                    },
                    onStart = {
                        // Reproducir álbum desde el primer track
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🎵 Iniciando reproducción del álbum: ${selectedSpotifyAlbum!!.name}")
                            
                            // Convertir SpotifyTrack a TrackEntity
                            val trackEntities = selectedItemTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyAlbum!!.id}_${spotifyTrack.id}",
                                    playlistId = selectedSpotifyAlbum!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null, // Se buscará dinámicamente
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }
                            
                            // Establecer playlist y comenzar reproducción
                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)
                            
                            // Buscar y reproducir el primer track
                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error al reproducir álbum", e)
                                    }
                                }
                            }
                        }
                    },
                    onRandom = {
                        // Reproducir álbum en orden aleatorio
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🔀 Iniciando reproducción aleatoria del álbum: ${selectedSpotifyAlbum!!.name}")
                            
                            // Convertir SpotifyTrack a TrackEntity y mezclar
                            val shuffledTracks = selectedItemTracks.shuffled()
                            val trackEntities = shuffledTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyAlbum!!.id}_${spotifyTrack.id}_shuffled",
                                    playlistId = selectedSpotifyAlbum!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null, // Se buscará dinámicamente
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }
                            
                            // Establecer playlist mezclada y comenzar reproducción
                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)
                            
                            // Buscar y reproducir el primer track de la lista mezclada
                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error al reproducir álbum aleatorio", e)
                                    }
                                }
                            }
                        }
                    },
                    onSave = saveSpotifyPlaylistToLibrary,
                    playerViewModel = playerViewModel
                )
            }
            else -> {
                // Vista normal de búsqueda
                SearchMainView(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    results = results,
                    spotifyResults = spotifyResults,
                    showSpotifyResults = showSpotifyResults,
                    isLoading = isLoading,
                    error = error,
                    onVideoSelected = onVideoSelected,
                    onAlbumSelected = loadSpotifyAlbumTracks,
                    onPlaylistSelected = loadSpotifyPlaylistTracks,
                    onSearchTriggered = performSearch,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
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
                    items(
                        count = currentQueue.size,
                        key = { index -> currentQueue[index].id }
                    ) { index ->
                        val track = currentQueue[index]
                        val isCurrentTrack = queueState.currentIndex == index
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        if (queueState.currentIndex != index) {
                                            playerViewModel.playQueueFromIndex(index)
                                        } else {
                                            playerViewModel.resumeIfPaused()
                                        }
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
                    var randomJob by remember { mutableStateOf<Job?>(null) }
                    var startJob by remember { mutableStateOf<Job?>(null) }
                    
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
                            randomJob = GlobalScope.launch {
                                val randomTrack = playlistTracks.random()
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == randomTrack.id }
                                
                                println("� RANDOM: ${randomTrack.getDisplayName()}")
                                
                                if (trackEntity != null && playerViewModel != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel?.initializePlayer()
                                    
                                    // Establecer la playlist completa con el track aleatorio seleccionado
                                    val currentTrackIndex = tracksFromDB.indexOf(trackEntity)
                                    if (currentTrackIndex >= 0) {
                                        playerViewModel?.setCurrentPlaylist(tracksFromDB, currentTrackIndex)
                                    }
                                    
                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel?.loadAudioFromTrack(trackEntity)
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
                            startJob = GlobalScope.launch {
                                val firstTrack = playlistTracks.first()
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == firstTrack.id }
                                
                                println("🎵 START [${selectedPlaylist!!.name}]: ${firstTrack.getDisplayName()}")
                                
                                if (trackEntity != null && playerViewModel != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel?.initializePlayer()
                                    
                                    // Establecer la playlist completa desde el inicio (índice 0)
                                    val trackEntityIndex = tracksFromDB.indexOf(trackEntity)
                                    if (trackEntityIndex >= 0) {
                                        playerViewModel?.setCurrentPlaylist(tracksFromDB, trackEntityIndex)
                                    }
                                    
                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel?.loadAudioFromTrack(trackEntity)
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
                                                audioUrl = null, // Se obtendrá dinámicamente
                                                position = index,
                                                lastSyncTime = System.currentTimeMillis()
                                            )
                                            
                                            // Add to player queue and play
                                            playerViewModel?.let { viewModel ->
                                                CoroutineScope(Dispatchers.Main).launch {
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
                                                                audioUrl = null,
                                                                position = idx,
                                                                lastSyncTime = System.currentTimeMillis()
                                                            )
                                                        }
                                                        
                                                        // Set current playlist and play from index
                                                        viewModel.setCurrentPlaylist(playlistEntities, index)
                                                        viewModel.loadAudioFromTrack(trackEntity)
                                                    } catch (e: Exception) {
                                                        Log.e("PlaylistScreen", "Error playing track: ${e.message}")
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
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        )
                                    )
                                    Text(
                                        text = "  (${playlist.getTrackCount()})",
                                        style = MaterialTheme.typography.bodyMedium.copy(
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

@Composable
fun SpotifyResultsMenus(
    spotifyResults: SpotifySearchAllResponse,
    onTrackSelected: (SpotifyTrack) -> Unit,
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onArtistSelected: (SpotifyArtistFull) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit
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
                        text = " ",
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
                        text = " ",
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
                        text = " ",
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
                // Null safety: playlist can be null, so use safe access
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (playlist != null) onPlaylistSelected(playlist) }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = " ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7FB069)
                        )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist?.name ?: "Sin nombre",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = playlist?.getTrackCount() ?: "0 tracks",
                            style = MaterialTheme.typography.bodyMedium.copy(
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

@Composable
fun SpotifyPlaylistDetailView(
    playlist: SpotifyPlaylist,
    tracks: List<SpotifyTrack>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
    playerViewModel: PlayerViewModel?
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header con botón de retroceso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 8.dp)
            )
            Text(
                text = "$ ${playlist.name}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Información de la playlist
        playlist.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF95A5A6)
                ),
                modifier = Modifier.padding(bottom = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Text(
            text = "Tracks: ${playlist.getTrackCount()}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Botones de acción
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            ActionButton(
                text = "<start>",
                color = Color(0xFF4ECDC4),
                onClick = onStart,
                enabled = tracks.isNotEmpty()
            )
            ActionButton(
                text = "<rand>",
                color = Color(0xFFFFD93D),
                onClick = onRandom,
                enabled = tracks.isNotEmpty()
            )
            ActionButton(
                text = "<save>",
                color = Color(0xFF7FB069),
                onClick = onSave,
                enabled = true
            )
        }
        
        // Estados de carga y error
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "$ loading tracks...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD93D)
                    )
                )
            }
        }
        
        error?.let {
            Text(
                "ERR: $it",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Lista de tracks
        if (tracks.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(tracks.size) { index ->
                    val track = tracks[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Reproducir track específico
                                Log.d("SpotifyPlaylist", "🎵 Track seleccionado: ${track.name}")
                                
                                playerViewModel?.let { viewModel ->
                                    // Convertir todos los tracks de la playlist a TrackEntity
                                    val trackEntities = tracks.mapIndexed { trackIndex, spotifyTrack ->
                                        TrackEntity(
                                            id = "spotify_${playlist.id}_${spotifyTrack.id}",
                                            playlistId = playlist.id,
                                            spotifyTrackId = spotifyTrack.id,
                                            name = spotifyTrack.name,
                                            artists = spotifyTrack.getArtistNames(),
                                            youtubeVideoId = null, // Se buscará dinámicamente
                                            audioUrl = null,
                                            position = trackIndex,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }
                                    
                                    // Establecer la playlist completa y comenzar desde el track seleccionado
                                    viewModel.setCurrentPlaylist(trackEntities, index)
                                    
                                    // Buscar y reproducir el track seleccionado
                                    val selectedTrackEntity = trackEntities[index]
                                    // TODO: Necesitaríamos acceso al scope aquí, por ahora solo log
                                    Log.d("SpotifyPlaylist", "Configurada playlist con ${trackEntities.size} tracks, iniciando desde track ${index + 1}")
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            ),
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.name ?: "Sin título",
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

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = if (enabled) color else Color(0xFF95A5A6)
        ),
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp)
    )
}

@Composable
private fun SearchMainView(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    results: List<AudioItem>,
    spotifyResults: SpotifySearchAllResponse?,
    showSpotifyResults: Boolean,
    isLoading: Boolean,
    error: String?,
    onVideoSelected: (String, String) -> Unit,
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit,
    onSearchTriggered: (String) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
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
        onValueChange = onSearchQueryChange,
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
                    onSearchQueryChange("")
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
                    onSearchTriggered(searchQuery)
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
    android.util.Log.d("SearchMainView", "Renderizando vista principal - showSpotifyResults=$showSpotifyResults, spotifyResults!=null=${spotifyResults != null}")
    if (showSpotifyResults && spotifyResults != null) {
        SpotifySearchResultsView(
            results = spotifyResults,
            onAlbumSelected = onAlbumSelected,
            onPlaylistSelected = onPlaylistSelected
        )
    }

    // === RESULTADOS DE YOUTUBE ===
    if (results.isNotEmpty()) {
        YouTubeSearchResultsView(
            results = results,
            onVideoSelected = onVideoSelected,
            playerViewModel = playerViewModel,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
private fun SpotifySearchResultsView(
    results: SpotifySearchAllResponse?,
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit
) {
    // Debug log para verificar que la UI recibe los datos
    android.util.Log.d("SpotifySearchResultsView", "Rendering with results: tracks=${results?.tracks?.items?.size}, albums=${results?.albums?.items?.size}, artists=${results?.artists?.items?.size}, playlists=${results?.playlists?.items?.size}")
    
    val hasResults = results?.tracks?.items?.isNotEmpty() == true ||
        results?.albums?.items?.isNotEmpty() == true ||
        results?.artists?.items?.isNotEmpty() == true ||
        results?.playlists?.items?.isNotEmpty() == true

    if (!hasResults) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No se encontraron resultados de Spotify.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF95A5A6)
                )
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Desplegables para cada tipo de resultado
            var tracksExpanded by remember { mutableStateOf(false) }
            var albumsExpanded by remember { mutableStateOf(false) }
            var artistsExpanded by remember { mutableStateOf(false) }
            var playlistsExpanded by remember { mutableStateOf(false) }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Tracks
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tracksExpanded = !tracksExpanded }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if(tracksExpanded)"v tracks (${results?.tracks?.items?.size ?: 0})" else "> tracks (${results?.tracks?.items?.size ?: 0})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF4ECDC4)
                                )
                            )
                        }
                        if (tracksExpanded) {
                            results?.tracks?.items?.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { if (track != null) Log.d("SpotifySearch", "Track seleccionado: ${track.name} by ${track.getArtistNames()}") }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = track?.name ?: "Sin nombre",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                // Albums
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { albumsExpanded = !albumsExpanded }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if(albumsExpanded)"v albums (${results?.albums?.items?.size ?: 0})" else "> albums (${results?.albums?.items?.size ?: 0})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFB2B2FF)
                                )
                            )
                        }
                        if (albumsExpanded) {
                            results?.albums?.items?.forEach { album ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { if (album != null) onAlbumSelected(album) }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = album?.name ?: "Sin nombre",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                // Artists
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { artistsExpanded = !artistsExpanded }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (artistsExpanded) "v artists (${results?.artists?.items?.size ?: 0})" else "> artists (${results?.artists?.items?.size ?: 0})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFFD93D)
                                )
                            )
                        }
                        if (artistsExpanded) {
                            results?.artists?.items?.forEach { artist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { if (artist != null) Log.d("SpotifySearch", "Artista seleccionado: ${artist.name}") }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = artist?.name ?: "Sin nombre",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                // Playlists
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playlistsExpanded = !playlistsExpanded }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (playlistsExpanded) "v playlists (${results?.playlists?.items?.size ?: 0})" else "> playlists (${results?.playlists?.items?.size ?: 0})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF7FB069)
                                )
                            )
                        }
                        if (playlistsExpanded) {
                            results?.playlists?.items?.forEach { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { if (playlist != null) onPlaylistSelected(playlist) }
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist?.name ?: "Sin nombre",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE0E0E0)
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = playlist?.getTrackCount() ?: "0 tracks",
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
private fun YouTubeSearchResultsView(
    results: List<AudioItem>,
    onVideoSelected: (String, String) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "> youtube_results (${results.size})",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF7FB069)
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(results.size) { index ->
                val video = results[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onVideoSelected(video.videoId, video.title)
                            
                            // También agregar al ViewModel si está disponible
                            playerViewModel?.let { viewModel ->
                                coroutineScope.launch {
                                    try {
                                        // Crear TrackEntity temporal para reproducción individual
                                        val trackEntity = TrackEntity(
                                            id = "youtube_${video.videoId}",
                                            playlistId = "search_results",
                                            spotifyTrackId = "", // Empty string for YouTube tracks
                                            name = video.title,
                                            artists = video.channel,
                                            youtubeVideoId = video.videoId,
                                            audioUrl = null, // Se obtendrá dinámicamente
                                            position = 0,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                        
                                        // Establecer como playlist de un solo track
                                        viewModel.setCurrentPlaylist(listOf(trackEntity), 0)
                                        
                                        Log.d("YouTubeSearch", "🎵 Video seleccionado: ${video.title}")
                                    } catch (e: Exception) {
                                        Log.e("YouTubeSearch", "Error al configurar reproducción", e)
                                    }
                                }
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "▶ ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7FB069)
                        )
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE0E0E0)
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = video.channel,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF95A5A6)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Text(
                                text = video.duration,
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

@Composable
fun SpotifyAlbumDetailView(
    album: SpotifyAlbum,
    tracks: List<SpotifyTrack>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
    playerViewModel: PlayerViewModel?
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header con botón de retroceso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 8.dp)
            )
            Text(
                text = "$ ${album.name}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Información del álbum
        Text(
            text = "Artist: ${album.getArtistNames()}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier.padding(bottom = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        album.release_date?.let { releaseDate ->
            Text(
                text = "Released: $releaseDate",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF95A5A6)
                ),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        Text(
            text = "Tracks: ${album.total_tracks ?: tracks.size}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Botones de acción
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ActionButton(
                text = "<start>",
                color = Color(0xFF4ECDC4),
                onClick = onStart,
                enabled = tracks.isNotEmpty()
            )
            ActionButton(
                text = "<rand>",
                color = Color(0xFFFFD93D),
                onClick = onRandom,
                enabled = tracks.isNotEmpty()
            )
            ActionButton(
                text = "<save>",
                color = Color(0xFF7FB069),
                onClick = onSave,
                enabled = true
            )
        }
        
        // Estados de carga y error
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "$ loading tracks...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD93D)
                    )
                )
            }
        }
        
        error?.let {
            Text(
                "ERR: $it",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        // Lista de tracks
        if (tracks.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(tracks.size) { index ->
                    val track = tracks[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Reproducir track específico
                                Log.d("SpotifyAlbum", "🎵 Track seleccionado: ${track.name}")
                                
                                playerViewModel?.let { viewModel ->
                                    // Convertir todos los tracks del álbum a TrackEntity
                                    val trackEntities = tracks.mapIndexed { trackIndex, spotifyTrack ->
                                        TrackEntity(
                                            id = "spotify_album_${album.id}_${spotifyTrack.id}",
                                            playlistId = album.id,
                                            spotifyTrackId = spotifyTrack.id,
                                            name = spotifyTrack.name,
                                            artists = spotifyTrack.getArtistNames(),
                                            youtubeVideoId = null, // Se buscará dinámicamente
                                            audioUrl = null,
                                            position = trackIndex,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }
                                    
                                    // Establecer la playlist completa y comenzar desde el track seleccionado
                                    viewModel.setCurrentPlaylist(trackEntities, index)
                                    
                                    // Buscar y reproducir el track seleccionado
                                    val selectedTrackEntity = trackEntities[index]
                                    Log.d("SpotifyAlbum", "Configurada playlist con ${trackEntities.size} tracks, iniciando desde track ${index + 1}")
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            ),
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.name ?: "Sin título",
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
