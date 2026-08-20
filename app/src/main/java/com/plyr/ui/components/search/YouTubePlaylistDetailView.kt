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
import androidx.compose.runtime.livedata.observeAsState
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.service.YouTubeSearchManager
import com.plyr.utils.UrlParser
import com.plyr.ui.components.PlyrErrorText
import com.plyr.ui.components.PlyrInfoText
import com.plyr.ui.components.PlyrLoadingIndicator
import com.plyr.ui.components.PlyrMediumSpacer
import com.plyr.ui.components.PlyrSmallSpacer
import com.plyr.ui.components.ShareDialog
import com.plyr.ui.components.ShareableItem
import com.plyr.ui.components.ShareType
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
    var showShareDialog by remember { mutableStateOf(false) }

    // Estado para guardado de playlist
    val context = LocalContext.current
    val localRepository = remember { PlaylistLocalRepository(context) }
    var isSaved by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Observar el track actual para actualización reactiva
    val currentTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }

    // Verificar si la playlist ya está guardada
    LaunchedEffect(playlist.playlistId) {
        isSaved = localRepository.isYouTubePlaylistSaved(playlist.playlistId)
    }

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
                    playlistId = "youtube_${playlist.playlistId}",
                    remoteTrackId = video.videoId,
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
        // Header del detalle de playlist
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
                text = ">",
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
                text = "<rnd>",
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
                text = if (isSaved) "<saved>" else "<save>",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (isSaved) Color(0xFFFFD93D) else Color(0xFF7FB069)
                ),
                modifier = Modifier.clickable(enabled = !isSaving && videos.isNotEmpty()) {
                    if (isSaved) {
                        // Eliminar playlist guardada
                        coroutineScope.launch {
                            localRepository.deleteYouTubePlaylist(playlist.playlistId)
                            isSaved = false
                            saveMessage = null
                        }
                    } else {
                        // Guardar playlist
                        isSaving = true
                        saveMessage = null
                        coroutineScope.launch {
                            val savedTracks = trackEntities.map { it.copy(playlistId = "youtube_${playlist.playlistId}") }
                            val coverUrl = videos.firstOrNull()?.thumbnailUrl
                                ?: trackEntities.firstOrNull()?.youtubeVideoId?.let { UrlParser.youtubeThumbnailUrl(it) }
                            val success = localRepository.saveYouTubePlaylist(
                                playlistId = playlist.playlistId,
                                title = playlist.title,
                                description = playlist.description,
                                uploader = playlist.uploader,
                                videoCount = playlist.videoCount,
                                imageUrl = coverUrl,
                                tracks = savedTracks
                            )
                            isSaving = false
                            if (success) {
                                isSaved = true
                                saveMessage = null
                            } else {
                                saveMessage = "Error saving playlist"
                            }
                        }
                    }
                }
            )
            Text(
                text = "<share>",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF6B9D)
                ),
                modifier = Modifier.clickable { showShareDialog = true }
            )
        }
        PlyrMediumSpacer()

        // Mensaje de estado del guardado
        saveMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )
            )
            PlyrSmallSpacer()
        }

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
                            artist = v.uploader,
                            youtubeId = v.videoId,
                            shareUrl = "https://www.youtube.com/watch?v=${v.videoId}"
                        )
                        val isPlaying = currentTrack?.youtubeVideoId == v.videoId ||
                                       currentTrack?.id == trackEntities.getOrNull(idx)?.id
                        SongListItem(
                            song = song,
                            trackEntities = trackEntities,
                            index = idx,
                            playerViewModel = playerViewModel,
                            coroutineScope = coroutineScope,
                            isCurrentlyPlaying = isPlaying
                        )
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        ShareDialog(
            item = ShareableItem(
                remoteId = null,
                shareUrl = null,
                youtubeId = playlist.playlistId,
                title = playlist.title,
                artist = "YouTube Playlist",
                type = ShareType.PLAYLIST
            ),
            onDismiss = { showShareDialog = false }
        )
    }
}
