package com.plyr.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.plyr.database.TrackEntity
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.PlyrErrorText
import com.plyr.ui.components.PlyrInfoText
import com.plyr.ui.components.PlyrLoadingIndicator
import com.plyr.ui.components.PlyrMediumSpacer
import com.plyr.ui.components.PlyrSmallSpacer
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import com.plyr.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun YouTubePlaylistDetailView(
    playlist: YouTubeSearchManager.YouTubePlaylistInfo,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var videos by remember { mutableStateOf<List<YouTubeSearchManager.YouTubeVideoInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var trackEntities by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }

    val context = LocalContext.current

    LaunchedEffect(playlist.playlistId) {
        try {
            isLoading = true
            errorMessage = null
            val youtubeSearchManager = YouTubeSearchManager(context)
            val playlistVideos = youtubeSearchManager.getYouTubePlaylistVideos(playlist.playlistId)
            videos = playlistVideos
            // Crear TrackEntities para reproducción
            trackEntities = playlistVideos.mapIndexed { index, video ->
                TrackEntity(
                    id = "ytpl_${playlist.playlistId}_${video.videoId}_$index",
                    playlistId = playlist.playlistId,
                    spotifyTrackId = video.videoId,
                    name = video.title,
                    artists = video.uploader,
                    youtubeVideoId = video.videoId,
                    audioUrl = null,
                    position = index,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error loading playlist: ${e.message}"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
    ) {
        // Header estilo Spotify playlist detail
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ${playlist.title}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        PlyrSmallSpacer()
        // Action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.large)
        ) {
            Text(
                text = "<start>",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.clickable(enabled = videos.isNotEmpty()) {
                    if (trackEntities.isNotEmpty() && playerViewModel != null) {
                        playerViewModel.setCurrentPlaylist(trackEntities, 0)
                        coroutineScope.launch { playerViewModel.loadAudioFromTrack(trackEntities.first()) }
                    }
                }
            )
            Text(
                text = "<rand>",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFD93D)
                ),
                modifier = Modifier.clickable(enabled = videos.isNotEmpty()) {
                    if (trackEntities.isNotEmpty() && playerViewModel != null) {
                        val shuffled = trackEntities.shuffled()
                        playerViewModel.setCurrentPlaylist(shuffled, 0)
                        coroutineScope.launch { playerViewModel.loadAudioFromTrack(shuffled.first()) }
                    }
                }
            )
            Text(
                text = "<save>",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF7FB069)
                ),
                modifier = Modifier.clickable { /* futuro guardado */ }
            )
        }
        PlyrMediumSpacer()

        when {
            isLoading -> PlyrLoadingIndicator("loading playlist")
            errorMessage != null -> PlyrErrorText(errorMessage!!)
            videos.isEmpty() -> PlyrInfoText("No videos found in this playlist")
            else -> {
                // Listado SongListItem con duración
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = PlyrSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(videos.size) { idx ->
                        val v = videos[idx]
                        val song = Song(
                            number = idx + 1,
                            title = v.title,
                            artist = v.uploader
                        )
                        val isSelected = playerViewModel?.currentTrack?.value?.id == trackEntities.getOrNull(idx)?.id
                        SongListItem(
                            song = song,
                            trackEntities = trackEntities,
                            index = idx,
                            playerViewModel = playerViewModel,
                            coroutineScope = coroutineScope,
                            isSelected = isSelected // duration removed to show action button
                        )
                    }
                }
            }
        }
    }
}
