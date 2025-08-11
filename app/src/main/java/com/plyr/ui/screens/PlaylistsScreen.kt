package com.plyr.ui.screens

import CreateSpotifyPlaylistScreen
import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plyr.database.*
import com.plyr.network.SpotifyRepository
import com.plyr.utils.Config
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val playlistRepository = remember { PlaylistLocalRepository(context) }

    // Load playlists
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            playlists = playlistRepository.getPlaylistsWithAutoSync()

            // Also sync Spotify playlists if connected
            if (Config.isSpotifyConnected(context)) {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    // Trigger a sync instead of manually inserting playlists
                    val syncSuccess = playlistRepository.syncPlaylistsFromSpotify()
                    if (syncSuccess) {
                        playlists = playlistRepository.getPlaylistsWithAutoSync()
                    } else {
                        Log.e("PlaylistsScreen", "Failed to sync playlists from Spotify")
                        error = "Failed to sync playlists from Spotify"
                    }
                }
            }
        } catch (e: Exception) {
            error = "Error loading playlists: ${e.message}"
            Log.e("PlaylistsScreen", "Error loading playlists", e)
        } finally {
            isLoading = false
        }
    }

    BackHandler {
        if (showCreatePlaylist) {
            showCreatePlaylist = false
        } else {
            onBack()
        }
    }

    if (showCreatePlaylist) {
        CreateSpotifyPlaylistScreen(
            onBack = { showCreatePlaylist = false },
            onPlaylistCreated = {
                showCreatePlaylist = false
                // Reload playlists
                coroutineScope.launch {
                    playlists = playlistRepository.getPlaylistsWithAutoSync()
                }
            }
        )
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ plyr_playlists",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        color = Color(0xFF4ECDC4)
                    )
                )

                if (Config.isSpotifyConnected(context)) {
                    Text(
                        text = "+ create",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF1DB954)
                        ),
                        modifier = Modifier
                            .clickable { showCreatePlaylist = true }
                            .padding(8.dp)
                    )
                }
            }

            when {
                isLoading -> {
                    Text(
                        text = "> loading playlists...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFFD93D)
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                error != null -> {
                    Text(
                        text = "> error: $error",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE74C3C)
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                playlists.isNotEmpty() -> {
                    Text(
                        text = "> your playlists [${playlists.size}]",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = Color(0xFFFFD93D)
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists.size) { index ->
                            val playlist = playlists[index]

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Load and play playlist
                                        coroutineScope.launch {
                                            try {
                                                val tracks = playlistRepository.getTracksWithAutoSync(playlist.spotifyId)
                                                if (tracks.isNotEmpty()) {
                                                    playerViewModel?.setCurrentPlaylist(tracks, 0)
                                                    playerViewModel?.loadAudioFromTrack(tracks.first())
                                                    Log.d("PlaylistsScreen", "Playing playlist: ${playlist.name}")
                                                }
                                            } catch (e: Exception) {
                                                Log.e("PlaylistsScreen", "Error playing playlist", e)
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    // All playlists are from Spotify since that's the only source
                                    containerColor = Color(0xFF1DB954).copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Playlist image or icon
                                    if (playlist.imageUrl != null) {
                                        AsyncImage(
                                            model = playlist.imageUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "♫", // All playlists are from Spotify
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    color = Color(0xFF1DB954)
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 16.sp,
                                                color = Color.White
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${playlist.trackCount} tracks",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF95A5A6)
                                                )
                                            )
                                        }

                                        playlist.description?.let { description ->
                                            if (description.isNotBlank()) {
                                                Text(
                                                    text = description,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
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
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "> no playlists found",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )

                        if (!Config.isSpotifyConnected(context)) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connect to Spotify to see your playlists",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
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
