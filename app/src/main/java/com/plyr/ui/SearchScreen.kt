package com.plyr.ui

import android.content.Context
import android.util.Log
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.plyr.ui.components.search.SpotifyArtistDetailView
import com.plyr.ui.components.search.YouTubePlaylistDetailView
import com.plyr.ui.components.search.YouTubeSearchResults
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun SearchScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
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

    // Estados para paginación
    var currentOffset by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreResults by remember { mutableStateOf(true) }
    val itemsPerPage = 10

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

    val haptic = LocalHapticFeedback.current

    // Search function with pagination support
    val performSearch: (String, Boolean) -> Unit = { searchQuery, isLoadMore ->
        if (searchQuery.isNotBlank() && (!isLoading || isLoadMore)) {
            if (isLoadMore) {
                isLoadingMore = true
            } else {
                isLoading = true
                currentOffset = 0
                results = emptyList()
                spotifyResults = null
                showSpotifyResults = false
                hasMoreResults = true
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
                        isLoadingMore = false
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

                            if (isLoadMore) {
                                results = results + newResults
                            } else {
                                results = newResults
                            }

                            hasMoreResults = newResults.size >= itemsPerPage
                            isLoading = false
                            isLoadingMore = false
                        }

                        "spotify" -> {
                            // Search Spotify with pagination
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    Log.d("SearchScreen", "🔍 Iniciando búsqueda en Spotify: '$finalQuery'")
                                    SpotifyRepository.searchAllWithPagination(accessToken, finalQuery) { searchResults: SpotifySearchAllResponse?, searchError: String? ->
                                        // Asegurar que las actualizaciones se ejecuten en el hilo principal
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            if (searchError != null) {
                                                isLoading = false
                                                isLoadingMore = false
                                                error = "Error searching Spotify: $searchError"
                                                Log.e("SearchScreen", "Error searching Spotify: $searchError")
                                            } else if (searchResults != null) {
                                                Log.d("SearchScreen", "✅ Resultados actualizados: ${searchResults.tracks.items.size} tracks, ${searchResults.albums.items.size} albums, ${searchResults.artists.items.size} artists, ${searchResults.playlists.items.size} playlists")

                                                if (isLoadMore && spotifyResults != null) {
                                                    // Combinar resultados existentes con nuevos
                                                    val combinedResults = SpotifySearchAllResponse(
                                                        tracks = SpotifyTracksSearchResult(
                                                            items = spotifyResults!!.tracks.items + searchResults.tracks.items,
                                                            total = searchResults.tracks.total,
                                                            limit = searchResults.tracks.limit,
                                                            offset = searchResults.tracks.offset,
                                                            next = searchResults.tracks.next
                                                        ),
                                                        albums = SpotifyAlbumsSearchResult(
                                                            items = spotifyResults!!.albums.items + searchResults.albums.items,
                                                            total = searchResults.albums.total,
                                                            limit = searchResults.albums.limit,
                                                            offset = searchResults.albums.offset,
                                                            next = searchResults.albums.next
                                                        ),
                                                        artists = SpotifyArtistsSearchResult(
                                                            items = spotifyResults!!.artists.items + searchResults.artists.items,
                                                            total = searchResults.artists.total,
                                                            limit = searchResults.artists.limit,
                                                            offset = searchResults.artists.offset,
                                                            next = searchResults.artists.next
                                                        ),
                                                        playlists = SpotifyPlaylistsSearchResult(
                                                            items = spotifyResults!!.playlists.items + searchResults.playlists.items,
                                                            total = searchResults.playlists.total,
                                                            limit = searchResults.playlists.limit,
                                                            offset = searchResults.playlists.offset,
                                                            next = searchResults.playlists.next
                                                        )
                                                    )
                                                    spotifyResults = combinedResults
                                                } else {
                                                    spotifyResults = searchResults
                                                }

                                                // Para esta implementación, como searchAllWithPagination ya obtiene todos los resultados,
                                                // no hay paginación manual adicional necesaria
                                                hasMoreResults = false

                                                isLoading = false
                                                isLoadingMore = false
                                                showSpotifyResults = true
                                                Log.d("SearchScreen", "🔄 Estado actualizado - showSpotifyResults=$showSpotifyResults")
                                            }
                                        }
                                    }
                                } else {
                                    isLoading = false
                                    isLoadingMore = false
                                    error = "Token de Spotify no disponible"
                                }
                            } else {
                                isLoading = false
                                isLoadingMore = false
                                error = "Spotify no está conectado"
                            }
                        }

                        else -> {
                            isLoading = false
                            isLoadingMore = false
                            error = "Motor de búsqueda no reconocido: $finalSearchEngine"
                            Log.w("SearchScreen", "Motor de búsqueda no reconocido: $finalSearchEngine")
                        }
                    }

                } catch (e: Exception) {
                    isLoading = false
                    isLoadingMore = false
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

    // Nueva función para cargar álbumes de un artista
    val loadArtistAlbums: (SpotifyArtistFull) -> Unit = { artist ->
        selectedSpotifyArtist = artist
        isLoadingArtistAlbums = true
        error = null
        selectedArtistAlbums = emptyList()

        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    Log.d("SearchScreen", "🎵 Cargando álbumes del artista: ${artist.name}")
                    SpotifyRepository.getArtistAlbums(accessToken, artist.id) { albums, errorMsg ->
                        isLoadingArtistAlbums = false
                        if (albums != null) {
                            selectedArtistAlbums = albums
                            Log.d("SearchScreen", "✅ ${albums.size} álbumes cargados para el artista: ${artist.name}")
                        } else {
                            error = "Error cargando álbumes del artista: $errorMsg"
                            Log.e("SearchScreen", "❌ Error cargando álbumes de artista: $errorMsg")
                        }
                    }
                } else {
                    isLoadingArtistAlbums = false
                    error = "Token de Spotify no disponible"
                    Log.e("SearchScreen", "❌ Token de Spotify no disponible")
                }
            } catch (e: Exception) {
                isLoadingArtistAlbums = false
                error = "Error cargando álbumes del artista: ${e.message}"
                Log.e("SearchScreen", "Error cargando artist albums", e)
            }
        }
    }

    // Función para cargar playlists del usuario
    val loadUserPlaylists: () -> Unit = {
        isLoadingPlaylists = true
        userPlaylists = emptyList()

        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    Log.d("SearchScreen", "🎵 Cargando playlists del usuario")
                    SpotifyRepository.getUserPlaylists(accessToken) { playlists, errorMsg ->
                        isLoadingPlaylists = false
                        if (playlists != null) {
                            userPlaylists = playlists
                            Log.d("SearchScreen", "✅ ${playlists.size} playlists cargadas del usuario")
                        } else {
                            error = "Error cargando playlists del usuario: $errorMsg"
                            Log.e("SearchScreen", "❌ Error cargando playlists del usuario: $errorMsg")
                        }
                    }
                } else {
                    isLoadingPlaylists = false
                    error = "Token de Spotify no disponible"
                    Log.e("SearchScreen", "❌ Token de Spotify no disponible")
                }
            } catch (e: Exception) {
                isLoadingPlaylists = false
                error = "Error cargando playlists del usuario: ${e.message}"
                Log.e("SearchScreen", "Error cargando user playlists", e)
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
                    onBack = {
                        selectedYouTubePlaylist = null
                    },
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
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
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
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
            selectedSpotifyArtist != null -> {
                // Nueva vista detallada para el artista
                SpotifyArtistDetailView(
                    artist = selectedSpotifyArtist!!,
                    albums = selectedArtistAlbums,
                    isLoading = isLoadingArtistAlbums,
                    error = error,
                    onBack = {
                        selectedSpotifyArtist = null
                        selectedArtistAlbums = emptyList()
                    },
                    onAlbumClick = { album ->
                        // Navegar al álbum seleccionado
                        loadSpotifyAlbumTracks(album)
                    },
                    onShuffleAll = {
                        // Reproducir todos los álbumes del artista en orden aleatorio
                        if (selectedArtistAlbums.isNotEmpty()) {
                            val firstAlbum = selectedArtistAlbums.first()
                            Log.d("SearchScreen", "🔀 Iniciando reproducción aleatoria del primer álbum del artista: ${firstAlbum.name}")

                            // Cargar los tracks del primer álbum
                            val accessToken = Config.getSpotifyAccessToken(context)
                            if (accessToken != null) {
                                SpotifyRepository.getAlbumTracks(accessToken, firstAlbum.id) { tracks, errorMsg ->
                                    if (tracks != null) {
                                        // Convertir SpotifyTrack a TrackEntity y mezclar
                                        val shuffledTracks = tracks.shuffled()
                                        val trackEntities = shuffledTracks.mapIndexed { index, spotifyTrack ->
                                            TrackEntity(
                                                id = "spotify_${firstAlbum.id}_${spotifyTrack.id}_shuffled",
                                                playlistId = firstAlbum.id,
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
                                                    Log.e("SearchScreen", "Error al reproducir álbum del artista aleatorio", e)
                                                }
                                            }
                                        }
                                    } else {
                                        Log.e("SearchScreen", "❌ Error cargando tracks para shuffle: $errorMsg")
                                    }
                                }
                            }
                        }
                    },
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
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
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch,
                    onAlbumSelected = loadSpotifyAlbumTracks,
                    onPlaylistSelected = loadSpotifyPlaylistTracks,
                    onArtistSelected = loadArtistAlbums,
                    onSearchTriggered = performSearch,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope,
                    // Pass the missing parameters
                    youtubeAllResults = youtubeAllResults,
                    showYouTubeAllResults = showYouTubeAllResults,
                    onYouTubePlaylistSelected = { playlist ->
                        selectedYouTubePlaylist = playlist
                    },
                    onAddTrackToPlaylist = { track ->
                        selectedTrackToAdd = track
                        loadUserPlaylists()
                        showPlaylistSelectionDialog = true
                    }
                )
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
                                                                Log.d("SearchScreen", "✅ Canción añadida a la playlist: ${playlist.name}")
                                                                // Cerrar diálogo y mostrar mensaje de éxito
                                                                showPlaylistSelectionDialog = false
                                                            } else {
                                                                Log.e("SearchScreen", "❌ Error añadiendo canción a la playlist: $errorMsg")
                                                                // Mostrar error
                                                                error = "Error añadiendo canción a la playlist: $errorMsg"
                                                            }
                                                        }
                                                    } else {
                                                        Log.e("SearchScreen", "❌ Token de Spotify no disponible")
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
    onVideoSelected: (String, String) -> Unit,
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
    onAddTrackToPlaylist: (SpotifyTrack) -> Unit = {}
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
                onTrackSelectedFromSearch = { track: SpotifyTrack, allTracks: List<SpotifyTrack>, selectedIndex: Int ->
                    // Convertir tracks de Spotify a TrackEntity y crear playlist temporal
                    val trackEntities = allTracks.mapIndexed { index: Int, spotifyTrack: SpotifyTrack ->
                        TrackEntity(
                            id = "spotify_search_${spotifyTrack.id}_$index",
                            playlistId = "spotify_search_${System.currentTimeMillis()}",
                            spotifyTrackId = spotifyTrack.id,
                            name = spotifyTrack.name,
                            artists = spotifyTrack.getArtistNames(),
                            youtubeVideoId = null, // Se buscará dinámicamente
                            audioUrl = null,
                            position = index,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }

                    // Establecer playlist en el PlayerViewModel
                    playerViewModel?.setCurrentPlaylist(trackEntities, selectedIndex)

                    // Cargar el track seleccionado
                    val selectedTrackEntity = trackEntities[selectedIndex]
                    coroutineScope.launch {
                        try {
                            playerViewModel?.loadAudioFromTrack(selectedTrackEntity)
                            Log.d(
                                "SpotifySearch",
                                "🎵 Track Spotify como playlist: ${track.name} (${selectedIndex + 1}/${allTracks.size})"
                            )
                        } catch (e: Exception) {
                            Log.e("SpotifySearch", "Error al reproducir track de Spotify", e)
                        }
                    }
                },
                onLoadMore = { onSearchTriggered(searchQuery, true) },
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope,
                onAddTrackToPlaylist = onAddTrackToPlaylist
            )
        }

        // === RESULTADOS DE YOUTUBE CON PLAYLISTS (NUEVO) ===
        if (showYouTubeAllResults && youtubeAllResults != null) {
            YouTubeSearchResults(
                results = null, // Legacy results
                youtubeAllResults = youtubeAllResults,
                onVideoSelected = onVideoSelected,
                onVideoSelectedFromSearch = onVideoSelectedFromSearch,
                onPlaylistSelected = onYouTubePlaylistSelected
            )
        }

        // === RESULTADOS DE YOUTUBE LEGACY ===
        if (results.isNotEmpty() && !showYouTubeAllResults) {
            CollapsibleYouTubeSearchResultsView(
                results = results,
                onVideoSelected = onVideoSelected,
                onVideoSelectedFromSearch = onVideoSelectedFromSearch,
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
    onTrackSelectedFromSearch: (SpotifyTrack, List<SpotifyTrack>, Int) -> Unit,
    onLoadMore: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    onAddTrackToPlaylist: (SpotifyTrack) -> Unit = {}
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    results.tracks.items.take(5).forEachIndexed { index, track ->
                        TrackRowWithPlaylistButton(
                            track = track,
                            index = index,
                            onTrackClick = {
                                onTrackSelectedFromSearch(track, results.tracks.items, index)
                            },
                            onAddToPlaylist = { selectedTrack ->
                                onAddTrackToPlaylist(selectedTrack)
                            }
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
fun TrackRowWithPlaylistButton(
    track: SpotifyTrack,
    index: Int,
    onTrackClick: () -> Unit,
    onAddToPlaylist: (SpotifyTrack) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
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

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onTrackClick() }
        ) {
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

        // Botón para añadir a playlist
        IconButton(
            onClick = { onAddToPlaylist(track) },
            modifier = Modifier.size(32.dp)
        ) {
            Text(
                text = "*",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                )
            )
        }
    }
}

@Composable
fun CollapsibleYouTubeSearchResultsView(
    results: List<AudioItem>,
    onVideoSelected: (String, String) -> Unit,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit,
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                results.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onVideoSelectedFromSearch(
                                    item.videoId,
                                    item.title,
                                    results,
                                    index
                                )
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFE0E0E0)
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = item.channel,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF95A5A6)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        item.duration?.let { duration ->
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF95A5A6)
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
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
