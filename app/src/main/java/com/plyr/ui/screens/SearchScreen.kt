package com.plyr.ui.screens

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plyr.database.TrackEntity
import com.plyr.model.AudioItem
import com.plyr.network.*
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.search.*
import com.plyr.utils.Config
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

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

    // Spotify search results
    var spotifyResults by remember { mutableStateOf<SpotifySearchAllResponse?>(null) }
    var showSpotifyResults by remember { mutableStateOf(false) }

    // Pagination state
    var currentOffset by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMoreResults by remember { mutableStateOf(true) }
    val itemsPerPage = 10

    // Detailed view state for playlists/albums
    var selectedSpotifyPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var selectedSpotifyAlbum by remember { mutableStateOf<SpotifyAlbum?>(null) }
    var selectedItemTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }

    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Search function with pagination support
    val performSearch: (String, Boolean) -> Unit = { query, isLoadMore ->
        if (query.isNotBlank() && (!isLoading || isLoadMore)) {
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

                    // Allow temporary override with prefixes
                    val (finalSearchEngine, finalQuery) = when {
                        query.startsWith("yt:", ignoreCase = true) -> {
                            "youtube" to query.substring(3).trim()
                        }
                        query.startsWith("sp:", ignoreCase = true) -> {
                            "spotify" to query.substring(3).trim()
                        }
                        else -> searchEngine to query
                    }

                    if (finalQuery.isEmpty()) {
                        isLoading = false
                        isLoadingMore = false
                        error = "Empty query after processing prefix"
                        return@launch
                    }

                    when (finalSearchEngine) {
                        "youtube" -> {
                            val youtubeResults = youtubeSearchManager.searchYouTubeVideosDetailed(finalQuery)
                            val newResults = youtubeResults.map { videoInfo ->
                                AudioItem(
                                    title = videoInfo.title,
                                    url = "",
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
                            if (Config.isSpotifyConnected(context)) {
                                val accessToken = Config.getSpotifyAccessToken(context)
                                if (accessToken != null) {
                                    Log.d("SearchScreen", "🔍 Starting Spotify search: '$finalQuery'")
                                    SpotifyRepository.searchAllWithPagination(accessToken, finalQuery) { searchResults: SpotifySearchAllResponse?, searchError: String? ->
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            if (searchError != null) {
                                                isLoading = false
                                                isLoadingMore = false
                                                error = "Error searching Spotify: $searchError"
                                                Log.e("SearchScreen", "Error searching Spotify: $searchError")
                                            } else if (searchResults != null) {
                                                Log.d("SearchScreen", "✅ Results updated: ${searchResults.tracks.items.size} tracks, ${searchResults.albums.items.size} albums, ${searchResults.artists.items.size} artists, ${searchResults.playlists.items.size} playlists")

                                                if (isLoadMore && spotifyResults != null) {
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

                                                hasMoreResults = false
                                                isLoading = false
                                                isLoadingMore = false
                                                showSpotifyResults = true
                                                Log.d("SearchScreen", "🔄 State updated - showSpotifyResults=$showSpotifyResults")
                                            }
                                        }
                                    }
                                } else {
                                    isLoading = false
                                    isLoadingMore = false
                                    error = "Spotify token not available"
                                }
                            } else {
                                isLoading = false
                                isLoadingMore = false
                                error = "Spotify not connected"
                            }
                        }

                        else -> {
                            isLoading = false
                            isLoadingMore = false
                            error = "Unrecognized search engine: $finalSearchEngine"
                            Log.w("SearchScreen", "Unrecognized search engine: $finalSearchEngine")
                        }
                    }

                } catch (e: Exception) {
                    isLoading = false
                    isLoadingMore = false
                    error = "Search error: ${e.message}"
                    Log.e("SearchScreen", "Search error", e)
                }
            }
        }
    }

    // Helper functions for Spotify operations
    val saveSpotifyPlaylistToLibrary: () -> Unit = {
        coroutineScope.launch {
            try {
                selectedSpotifyPlaylist?.let { playlist ->
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        Log.d("SearchScreen", "💾 Saving playlist to Spotify library: ${playlist.name}")
                        SpotifyRepository.followPlaylist(accessToken, playlist.id) { success, errorMsg ->
                            if (success) {
                                Log.d("SearchScreen", "✅ Playlist followed successfully: ${playlist.name}")
                            } else {
                                Log.e("SearchScreen", "❌ Error following playlist: $errorMsg")
                            }
                        }
                    } else {
                        Log.e("SearchScreen", "❌ Spotify token not available")
                    }
                }
                selectedSpotifyAlbum?.let { album ->
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        Log.d("SearchScreen", "💾 Saving album to Spotify library: ${album.name}")
                        SpotifyRepository.saveAlbum(accessToken, album.id) { success, errorMsg ->
                            if (success) {
                                Log.d("SearchScreen", "✅ Album saved successfully: ${album.name}")
                            } else {
                                Log.e("SearchScreen", "❌ Error saving album: $errorMsg")
                            }
                        }
                    } else {
                        Log.e("SearchScreen", "❌ Spotify token not available")
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchScreen", "Error saving to Spotify library", e)
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
                    Log.d("SearchScreen", "🎵 Loading playlist tracks: ${playlist.name}")
                    SpotifyRepository.getPlaylistTracks(accessToken, playlist.id) { playlistTracks, errorMsg ->
                        isLoadingTracks = false
                        if (playlistTracks != null) {
                            val tracks = playlistTracks.mapNotNull { it.track }
                            selectedItemTracks = tracks
                            Log.d("SearchScreen", "✅ ${tracks.size} tracks loaded for playlist: ${playlist.name}")
                        } else {
                            error = "Error loading playlist tracks: $errorMsg"
                            Log.e("SearchScreen", "❌ Error loading playlist tracks: $errorMsg")
                        }
                    }
                } else {
                    isLoadingTracks = false
                    error = "Spotify token not available"
                    Log.e("SearchScreen", "❌ Spotify token not available")
                }
            } catch (e: Exception) {
                isLoadingTracks = false
                error = "Error loading playlist tracks: ${e.message}"
                Log.e("SearchScreen", "Error loading playlist tracks", e)
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
                    Log.d("SearchScreen", "🎵 Loading album tracks: ${album.name}")
                    SpotifyRepository.getAlbumTracks(accessToken, album.id) { tracks, errorMsg ->
                        isLoadingTracks = false
                        if (tracks != null) {
                            selectedItemTracks = tracks
                            Log.d("SearchScreen", "✅ ${tracks.size} tracks loaded for album: ${album.name}")
                        } else {
                            error = "Error loading album tracks: $errorMsg"
                            Log.e("SearchScreen", "❌ Error loading album tracks: $errorMsg")
                        }
                    }
                } else {
                    isLoadingTracks = false
                    error = "Spotify token not available"
                    Log.e("SearchScreen", "❌ Spotify token not available")
                }
            } catch (e: Exception) {
                isLoadingTracks = false
                error = "Error loading album tracks: ${e.message}"
                Log.e("SearchScreen", "Error loading album tracks", e)
            }
        }
    }

    // Handle back button
    BackHandler {
        when {
            selectedSpotifyPlaylist != null || selectedSpotifyAlbum != null -> {
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
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🎵 Starting playlist playback: ${selectedSpotifyPlaylist!!.name}")

                            val trackEntities = selectedItemTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyPlaylist!!.id}_${spotifyTrack.id}",
                                    playlistId = selectedSpotifyPlaylist!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null,
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }

                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)

                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error playing playlist", e)
                                    }
                                }
                            }
                        }
                    },
                    onRandom = {
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🔀 Starting random playlist playback: ${selectedSpotifyPlaylist!!.name}")

                            val shuffledTracks = selectedItemTracks.shuffled()
                            val trackEntities = shuffledTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyPlaylist!!.id}_${spotifyTrack.id}_shuffled",
                                    playlistId = selectedSpotifyPlaylist!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null,
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }

                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)

                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error playing shuffled playlist", e)
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
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🎵 Starting album playback: ${selectedSpotifyAlbum!!.name}")

                            val trackEntities = selectedItemTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyAlbum!!.id}_${spotifyTrack.id}",
                                    playlistId = selectedSpotifyAlbum!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null,
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }

                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)

                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error playing album", e)
                                    }
                                }
                            }
                        }
                    },
                    onRandom = {
                        if (selectedItemTracks.isNotEmpty()) {
                            Log.d("SearchScreen", "🔀 Starting random album playback: ${selectedSpotifyAlbum!!.name}")

                            val shuffledTracks = selectedItemTracks.shuffled()
                            val trackEntities = shuffledTracks.mapIndexed { index, spotifyTrack ->
                                TrackEntity(
                                    id = "spotify_${selectedSpotifyAlbum!!.id}_${spotifyTrack.id}_shuffled",
                                    playlistId = selectedSpotifyAlbum!!.id,
                                    spotifyTrackId = spotifyTrack.id,
                                    name = spotifyTrack.name,
                                    artists = spotifyTrack.getArtistNames(),
                                    youtubeVideoId = null,
                                    audioUrl = null,
                                    position = index,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                            }

                            playerViewModel?.setCurrentPlaylist(trackEntities, 0)

                            trackEntities.firstOrNull()?.let { track ->
                                coroutineScope.launch {
                                    try {
                                        playerViewModel?.loadAudioFromTrack(track)
                                    } catch (e: Exception) {
                                        Log.e("SearchScreen", "Error playing shuffled album", e)
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
            else -> {
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
                    onSearchTriggered = performSearch,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}
