package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.plyr.model.AudioItem
import com.plyr.network.*
import com.plyr.utils.Config
import com.plyr.database.TrackEntity
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import com.plyr.ui.components.search.SpotifyArtistDetailView
import com.plyr.ui.components.search.YouTubePlaylistDetailView
import com.plyr.ui.components.search.YouTubeSearchResults
import com.plyr.ui.components.QrScannerDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@Composable
fun SearchScreen(
    context: Context,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
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

    // Estados para resultados de YouTube (NUEVOS)
    var youtubeAllResults by remember { mutableStateOf<YouTubeSearchManager.YouTubeSearchAllResult?>(null) }
    var showYouTubeAllResults by remember { mutableStateOf(false) }

    // Estados para vista detallada de playlist de YouTube (NUEVOS)
    var selectedYouTubePlaylist by remember { mutableStateOf<YouTubeSearchManager.YouTubePlaylistInfo?>(null) }

    // Estados para añadir canciones a playlist
    var showPlaylistSelectionDialog by remember { mutableStateOf(false) }
    var selectedTrackToAdd by remember { mutableStateOf<SpotifyTrack?>(null) }
    var userPlaylists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }

    // Estados para vista detallada de playlist/álbum/artista
    var selectedSpotifyPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var selectedSpotifyAlbum by remember { mutableStateOf<SpotifyAlbum?>(null) }
    var selectedSpotifyArtist by remember { mutableStateOf<SpotifyArtistFull?>(null) }
    var selectedItemTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var selectedArtistAlbums by remember { mutableStateOf<List<SpotifyAlbum>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    var isLoadingArtistAlbums by remember { mutableStateOf(false) }

    // YouTube search manager para búsquedas locales
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Definir el estado en el composable principal
    var showQrScanner by remember { mutableStateOf(false) }

    // Search function with pagination support
    val performSearch: (String, Boolean) -> Unit = { searchQuery, isLoadMore ->
        if (searchQuery.isNotBlank() && (!isLoading || isLoadMore)) {
            if (isLoadMore) {
                isLoading = true
            } else {
                isLoading = true
                results = emptyList()
                spotifyResults = null
                showSpotifyResults = false
                youtubeAllResults = null
                showYouTubeAllResults = false
            }
            error = null

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
                            // Limpiar resultados anteriores de YouTube
                            youtubeAllResults = null
                            showYouTubeAllResults = false

                            // Usar la nueva búsqueda completa de YouTube (videos + playlists)
                            val searchResults = youtubeSearchManager.searchYouTubeAll(finalQuery)

                            // Establecer los nuevos resultados
                            youtubeAllResults = searchResults
                            showYouTubeAllResults = true

                            // Mantener compatibilidad con el sistema legacy de videos
                            val newResults = searchResults.videos.map { videoInfo ->
                                AudioItem(
                                    title = videoInfo.title,
                                    url = "", // Use empty string for url, required by AudioItem
                                    videoId = videoInfo.videoId,
                                    channel = videoInfo.uploader,
                                    duration = videoInfo.getFormattedDuration()
                                )
                            }

                            results = newResults

                            isLoading = false
                        }

                        "spotify" -> {
                            // Search Spotify with pagination
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    SpotifyRepository.searchAllWithPagination(accessToken, finalQuery) { searchResults: SpotifySearchAllResponse?, searchError: String? ->
                                        // Asegurar que las actualizaciones se ejecuten en el hilo principal
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            if (searchError != null) {
                                                isLoading = false
                                                error = "Error searching Spotify: $searchError"
                                            } else if (searchResults != null) {
                                                spotifyResults = searchResults

                                                // Para esta implementación, como searchAllWithPagination ya obtiene todos los resultados,
                                                // no hay paginación manual adicional necesaria
                                                isLoading = false
                                                showSpotifyResults = true
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
                        }
                    }

                } catch (e: Exception) {
                    isLoading = false
                    error = "Error en búsqueda: ${e.message}"
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
                        SpotifyRepository.followPlaylist(accessToken, playlist.id) { success, errorMsg ->
                        }
                    }
                }
                selectedSpotifyAlbum?.let { album ->
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        SpotifyRepository.saveAlbum(accessToken, album.id) { success, errorMsg ->
                        }
                    }
                }
            } catch (_: Exception) {
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
                    SpotifyRepository.getPlaylistTracks(accessToken, playlist.id) { playlistTracks, errorMsg ->
                        isLoadingTracks = false
                        if (playlistTracks != null) {
                            // Convertir SpotifyPlaylistTrack a SpotifyTrack
                            val tracks = playlistTracks.mapNotNull { it.track }
                            selectedItemTracks = tracks
                        } else {
                            error = "Error cargando tracks de la playlist: $errorMsg"
                        }
                    }
                } else {
                    isLoadingTracks = false
                    error = "Token de Spotify no disponible"
                }
            } catch (e: Exception) {
                isLoadingTracks = false
                error = "Error cargando tracks de la playlist: ${e.message}"
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
                    SpotifyRepository.getAlbumTracks(accessToken, album.id) { tracks, errorMsg ->
                        isLoadingTracks = false
                        if (tracks != null) {
                            selectedItemTracks = tracks
                        } else {
                            error = "Error cargando tracks del álbum: $errorMsg"
                        }
                    }
                } else {
                    isLoadingTracks = false
                    error = "Token de Spotify no disponible"
                }
            } catch (e: Exception) {
                isLoadingTracks = false
                error = "Error cargando tracks del álbum: ${e.message}"
            }
        }
    }

    val loadArtistAlbums: (SpotifyArtistFull) -> Unit = { artist ->
        selectedSpotifyArtist = artist
        isLoadingArtistAlbums = true
        error = null
        selectedArtistAlbums = emptyList()

        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    SpotifyRepository.getArtistAlbums(accessToken, artist.id) { albums, errorMsg ->
                        isLoadingArtistAlbums = false
                        if (albums != null) {
                            selectedArtistAlbums = albums
                        } else {
                            error = "Error cargando álbumes del artista: $errorMsg"
                        }
                    }
                } else {
                    isLoadingArtistAlbums = false
                    error = "Token de Spotify no disponible"
                }
            } catch (e: Exception) {
                isLoadingArtistAlbums = false
                error = "Error cargando álbumes del artista: ${e.message}"
            }
        }
    }

    {
        isLoadingPlaylists = true
        userPlaylists = emptyList()

        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    SpotifyRepository.getUserPlaylists(accessToken) { playlists, errorMsg ->
                        isLoadingPlaylists = false
                        if (playlists != null) {
                            userPlaylists = playlists
                        } else {
                            error = "Error cargando playlists del usuario: $errorMsg"
                        }
                    }
                } else {
                    isLoadingPlaylists = false
                    error = "Token de Spotify no disponible"
                }
            } catch (e: Exception) {
                isLoadingPlaylists = false
                error = "Error cargando playlists del usuario: ${e.message}"
            }
        }
    }

    // Handle back button
    BackHandler {
        when {
            selectedYouTubePlaylist != null -> {
                // Volver de la vista detallada de playlist de YouTube
                selectedYouTubePlaylist = null
            }
            selectedSpotifyPlaylist != null || selectedSpotifyAlbum != null || selectedSpotifyArtist != null -> {
                // Volver de la vista detallada a los resultados de búsqueda
                selectedSpotifyPlaylist = null
                selectedSpotifyAlbum = null
                selectedSpotifyArtist = null
                selectedItemTracks = emptyList()
                selectedArtistAlbums = emptyList()
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
            selectedYouTubePlaylist != null -> {
                // Nueva vista detallada para playlists de YouTube
                YouTubePlaylistDetailView(
                    playlist = selectedYouTubePlaylist!!,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
            selectedSpotifyPlaylist != null -> {
                SpotifyPlaylistDetailView(
                    playlist = selectedSpotifyPlaylist!!,
                    tracks = selectedItemTracks,
                    isLoading = isLoadingTracks,
                    error = error,
                    onStart = {
                        // Reproducir playlist desde el primer track
                        if (selectedItemTracks.isNotEmpty()) {

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
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    },
                    onRandom = {
                        // Reproducir playlist en orden aleatorio
                        if (selectedItemTracks.isNotEmpty()) {

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
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    },
                    onSave = saveSpotifyPlaylistToLibrary,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
            selectedSpotifyAlbum != null -> {
                val album = selectedSpotifyAlbum!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    // Album header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = album.getImageUrl(),
                            contentDescription = "Album cover",
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                color = Color(0xFF4ECDC4)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "<start>",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFF4ECDC4)
                            ),
                            modifier = Modifier.clickable {
                                // Start playback from first track
                                if (selectedItemTracks.isNotEmpty()) {
                                    val trackEntities = selectedItemTracks.mapIndexed { trackIndex, spotifyTrack ->
                                        TrackEntity(
                                            id = "spotify_${album.id}_${spotifyTrack.id}",
                                            playlistId = album.id,
                                            spotifyTrackId = spotifyTrack.id,
                                            name = spotifyTrack.name,
                                            artists = spotifyTrack.getArtistNames(),
                                            youtubeVideoId = null,
                                            audioUrl = null,
                                            position = trackIndex,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }
                                    playerViewModel?.setCurrentPlaylist(trackEntities, 0)
                                    trackEntities.firstOrNull()?.let { track ->
                                        coroutineScope.launch {
                                            try {
                                                playerViewModel?.loadAudioFromTrack(track)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                            }.padding(8.dp)
                        )
                        Text(
                            text = "<rand>",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFFFFD93D)
                            ),
                            modifier = Modifier.clickable {
                                // Start playback with shuffled tracks
                                if (selectedItemTracks.isNotEmpty()) {
                                    val shuffledTracks = selectedItemTracks.shuffled()
                                    val trackEntities = shuffledTracks.mapIndexed { trackIndex, spotifyTrack ->
                                        TrackEntity(
                                            id = "spotify_${album.id}_${spotifyTrack.id}_shuffled",
                                            playlistId = album.id,
                                            spotifyTrackId = spotifyTrack.id,
                                            name = spotifyTrack.name,
                                            artists = spotifyTrack.getArtistNames(),
                                            youtubeVideoId = null,
                                            audioUrl = null,
                                            position = trackIndex,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }
                                    playerViewModel?.setCurrentPlaylist(trackEntities, 0)
                                    trackEntities.firstOrNull()?.let { track ->
                                        coroutineScope.launch {
                                            try {
                                                playerViewModel?.loadAudioFromTrack(track)
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                }
                            }.padding(8.dp)
                        )
                        Text(
                            text = "<back>",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFF95A5A6)
                            ),
                            modifier = Modifier.clickable {
                                selectedSpotifyAlbum = null
                                selectedItemTracks = emptyList()
                            }.padding(8.dp)
                        )
                    }
                    // Loading and error states
                    if (isLoadingTracks) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$ loading tracks...",
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
                    // Track list
                    if (selectedItemTracks.isNotEmpty()) {
                        val trackEntities = selectedItemTracks.mapIndexed { trackIndex, spotifyTrack ->
                            TrackEntity(
                                id = "spotify_${album.id}_${spotifyTrack.id}",
                                playlistId = album.id,
                                spotifyTrackId = spotifyTrack.id,
                                name = spotifyTrack.name,
                                artists = spotifyTrack.getArtistNames(),
                                youtubeVideoId = null,
                                audioUrl = null,
                                position = trackIndex,
                                lastSyncTime = System.currentTimeMillis()
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(selectedItemTracks.size) { index ->
                                val track = selectedItemTracks[index]
                                val song = Song(
                                    number = index + 1,
                                    title = track.name,
                                    artist = track.getArtistNames()
                                )
                                SongListItem(
                                    song = song,
                                    trackEntities = trackEntities,
                                    index = index,
                                    playerViewModel = playerViewModel,
                                    coroutineScope = coroutineScope,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            selectedSpotifyArtist != null -> {
                // Nueva vista detallada para el artista
                SpotifyArtistDetailView(
                    artist = selectedSpotifyArtist!!,
                    albums = selectedArtistAlbums,
                    isLoading = isLoadingArtistAlbums,
                    error = error,
                    onAlbumClick = { album ->
                        // Navegar al álbum seleccionado
                        loadSpotifyAlbumTracks(album)
                    }
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
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch,
                    onAlbumSelected = loadSpotifyAlbumTracks,
                    onPlaylistSelected = loadSpotifyPlaylistTracks,
                    onArtistSelected = loadArtistAlbums,
                    onSearchTriggered = performSearch,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope,
                    youtubeAllResults = youtubeAllResults,
                    showYouTubeAllResults = showYouTubeAllResults,
                    onYouTubePlaylistSelected = { playlist ->
                        selectedYouTubePlaylist = playlist
                    },
                    onShowQrScannerChange = { showQrScanner = it }
                )
                if (showQrScanner) {
                    QrScannerDialog(
                        onDismiss = { showQrScanner = false },
                        onQrScanned = { qrContent ->
                            searchQuery = qrContent
                            performSearch(qrContent, false)
                            showQrScanner = false
                        }
                    )
                }
            }
        }

        // Diálogo para selección de playlist
        if (showPlaylistSelectionDialog && selectedTrackToAdd != null) {
            AlertDialog(
                onDismissRequest = { showPlaylistSelectionDialog = false },
                title = { Text("Seleccionar playlist") },
                text = {
                    Column {
                        if (isLoadingPlaylists) {
                            // Indicador de carga
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            // Lista de playlists del usuario
                            LazyColumn {
                                items(userPlaylists) { playlist ->
                                    Text(
                                        text = playlist.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Añadir la canción a la playlist seleccionada
                                                coroutineScope.launch {
                                                    val accessToken = Config.getSpotifyAccessToken(context)
                                                    if (accessToken != null) {
                                                        SpotifyRepository.addTrackToPlaylist(accessToken, playlist.id, selectedTrackToAdd!!.id) { success, errorMsg ->
                                                            if (success) {
                                                                // Cerrar diálogo y mostrar mensaje de éxito
                                                                showPlaylistSelectionDialog = false
                                                            } else {
                                                                // Mostrar error
                                                                error = "Error añadiendo canción a la playlist: $errorMsg"
                                                            }
                                                        }
                                                    } else {
                                                        error = "Token de Spotify no disponible"
                                                    }
                                                }
                                            },
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
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // Cerrar diálogo sin acción
                            showPlaylistSelectionDialog = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
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
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit,
    onArtistSelected: (SpotifyArtistFull) -> Unit,
    onSearchTriggered: (String, Boolean) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    youtubeAllResults: YouTubeSearchManager.YouTubeSearchAllResult?,
    showYouTubeAllResults: Boolean,
    onYouTubePlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
    onShowQrScannerChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "$ plyr_search",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                        }) {
                            Text(
                                text = "x",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                    IconButton(onClick = { onShowQrScannerChange(true) }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert, // Cambia por un icono de QR si lo tienes
                            contentDescription = "Escanear QR"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank() && !isLoading) {
                        onSearchTriggered(searchQuery, false)
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
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace
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
                    style = MaterialTheme.typography.titleMedium.copy(
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
            CollapsibleSpotifySearchResultsView(
                results = spotifyResults,
                onAlbumSelected = onAlbumSelected,
                onPlaylistSelected = onPlaylistSelected,
                onArtistSelected = onArtistSelected,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        }

        // === RESULTADOS DE YOUTUBE CON PLAYLISTS (NUEVO) ===
        if (showYouTubeAllResults && youtubeAllResults != null) {
            YouTubeSearchResults(
                results = null, // Legacy results
                youtubeAllResults = youtubeAllResults,
                onVideoSelectedFromSearch = onVideoSelectedFromSearch,
                onPlaylistSelected = onYouTubePlaylistSelected,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        }

        // === RESULTADOS DE YOUTUBE LEGACY ===
        if (results.isNotEmpty() && !showYouTubeAllResults) {
            CollapsibleYouTubeSearchResultsView(
                results = results,
                onLoadMore = { onSearchTriggered(searchQuery, true) },
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        }

        // Mensaje cuando no hay resultados
        if (!isLoading && searchQuery.isNotBlank() &&
            !showSpotifyResults && !showYouTubeAllResults && results.isEmpty()) {
            Text(
                text = "> no results found",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF95A5A6)
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun CollapsibleSpotifySearchResultsView(
    results: SpotifySearchAllResponse,
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit,
    onArtistSelected: (SpotifyArtistFull) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var tracksExpanded by remember { mutableStateOf(false) }
    var albumsExpanded by remember { mutableStateOf(false) }
    var playlistsExpanded by remember { mutableStateOf(false) }
    var artistsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tracks section
        if (results.tracks.items.isNotEmpty()) {
            Text(
                text = if (tracksExpanded) "v tracks" else "> tracks",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { tracksExpanded = !tracksExpanded }
                    .padding(4.dp)
            )

            if (tracksExpanded) {
                val trackEntities = results.tracks.items.take(5).mapIndexed { trackIndex, track ->
                    TrackEntity(
                        id = "spotify_search_${track.id}_$trackIndex",
                        playlistId = "spotify_search_${System.currentTimeMillis()}",
                        spotifyTrackId = track.id,
                        name = track.name,
                        artists = track.getArtistNames(),
                        youtubeVideoId = null,
                        audioUrl = null,
                        position = trackIndex,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    results.tracks.items.take(5).forEachIndexed { index, track ->
                        val song = Song(
                            number = index + 1,
                            title = track.name,
                            artist = track.getArtistNames()
                        )
                        SongListItem(
                            song = song,
                            trackEntities = trackEntities,
                            index = index,
                            playerViewModel = playerViewModel,
                            coroutineScope = coroutineScope,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Albums section
        if (results.albums.items.isNotEmpty()) {
            Text(
                text = if (albumsExpanded) "v albums" else "> albums",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { albumsExpanded = !albumsExpanded }
                    .padding(4.dp)
            )

            if (albumsExpanded) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(results.albums.items.size) { index ->
                        val album = results.albums.items[index]
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { onAlbumSelected(album) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = album.getImageUrl(),
                                contentDescription = "Album cover",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = album.getArtistNames(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF95A5A6)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Playlists section
        if (results.playlists.items.isNotEmpty()) {
            Text(
                text = if (playlistsExpanded) "v playlists" else "> playlists",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { playlistsExpanded = !playlistsExpanded }
                    .padding(4.dp)
            )

            if (playlistsExpanded) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(results.playlists.items.size) { index ->
                        val playlist = results.playlists.items[index]
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { onPlaylistSelected(playlist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = playlist.getImageUrl(),
                                contentDescription = "Playlist cover",
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )

                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Artists section
        if (results.artists.items.isNotEmpty()) {
            Text(
                text = if (artistsExpanded) "v artists" else "> artists",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { artistsExpanded = !artistsExpanded }
                    .padding(4.dp)
            )

            if (artistsExpanded) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(results.artists.items.size) { index ->
                        val artist = results.artists.items[index]
                        Column(
                            modifier = Modifier
                                .width(100.dp)
                                .clickable { onArtistSelected(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = artist.getImageUrl(),
                                contentDescription = "Artist image",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(50.dp))
                            )

                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleYouTubeSearchResultsView(
    results: List<AudioItem>,
    onLoadMore: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var videosExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Videos section header
        Text(
            text = if (videosExpanded) "v youtube results [${results.size}]" else "> youtube results [${results.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier
                .clickable { videosExpanded = !videosExpanded }
                .padding(4.dp)
        )

        if (videosExpanded) {
            val trackEntities = results.mapIndexed { trackIndex, item ->
                TrackEntity(
                    id = "youtube_${item.videoId}",
                    playlistId = "youtube_search",
                    spotifyTrackId = item.videoId,
                    name = item.title,
                    artists = item.channel,
                    youtubeVideoId = item.videoId,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                results.forEachIndexed { index, item ->
                    val song = Song(
                        number = index + 1,
                        title = item.title,
                        artist = item.channel
                    )
                    SongListItem(
                        song = song,
                        trackEntities = trackEntities,
                        index = index,
                        playerViewModel = playerViewModel,
                        coroutineScope = coroutineScope,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Load more button if there are more results
                if (results.size >= 10) {
                    Text(
                        text = "> load more",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF4ECDC4)
                        ),
                        modifier = Modifier
                            .clickable { onLoadMore() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
