package com.plyr.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plyr.database.TrackEntity
import com.plyr.service.YouTubeSearchManager
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.ui.components.*
import com.plyr.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun YouTubePlaylistDetailView(
    playlist: YouTubeSearchManager.YouTubePlaylistInfo,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var videos by remember { mutableStateOf<List<YouTubeSearchManager.YouTubeVideoInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Cargar videos de la playlist
    LaunchedEffect(playlist.playlistId) {
        try {
            isLoading = true
            errorMessage = null

            // Usar el YouTubeSearchManager para obtener los videos
            val youtubeSearchManager = YouTubeSearchManager(context)
            val playlistVideos = youtubeSearchManager.getYouTubePlaylistVideos(playlist.playlistId)

            videos = playlistVideos
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error loading playlist: ${e.message}"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PlyrSpacing.large)
    ) {
        // Header con info de la playlist
        YouTubePlaylistHeader(
            playlist = playlist,
            onBack = onBack,
            onPlayAll = {
                if (videos.isNotEmpty()) {
                    playYouTubePlaylist(videos, playerViewModel, coroutineScope)
                }
            }
        )

        PlyrLargeSpacer()

        // Estado de carga o error
        when {
            isLoading -> {
                PlyrLoadingIndicator("loading playlist")
            }
            errorMessage != null -> {
                PlyrErrorText(errorMessage!!)
            }
            videos.isEmpty() -> {
                PlyrInfoText("No videos found in this playlist")
            }
            else -> {
                YouTubePlaylistTracksList(
                    videos = videos,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

@Composable
private fun YouTubePlaylistHeader(
    playlist: YouTubeSearchManager.YouTubePlaylistInfo,
    onBack: () -> Unit,
    onPlayAll: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column {
        // Botón de regreso
        PlyrMenuOption(
            text = "back",
            onClick = onBack
        )

        PlyrMediumSpacer()

        // Información de la playlist
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.large)
        ) {
            // Thumbnail de la playlist
            playlist.thumbnailUrl?.let { thumbnailUrl ->
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = "Playlist thumbnail",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(PlyrSpacing.small)
                )
            }

            // Info de la playlist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs)
            ) {
                Text(
                    text = playlist.title,
                    style = PlyrTextStyles.trackTitle(),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "by ${playlist.uploader}",
                    style = PlyrTextStyles.trackArtist()
                )

                Text(
                    text = playlist.getFormattedVideoCount(),
                    style = PlyrTextStyles.trackArtist()
                )

                playlist.description?.let { description ->
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = PlyrTextStyles.infoText(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        PlyrMediumSpacer()

        // Botón de reproducir todo
        Row(
            horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.medium)
        ) {
            PlyrPrimaryButton(
                text = "play all",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayAll()
                }
            )
        }
    }
}

@Composable
private fun YouTubePlaylistTracksList(
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column {
        PlyrMenuOption(
            text = "tracks [${videos.size}]",
            onClick = { },
            enabled = false
        )

        PlyrSmallSpacer()

        videos.forEachIndexed { index, video ->
            YouTubeVideoItem(
                video = video,
                index = index + 1,
                onClick = {
                    playYouTubeVideo(video, videos, index, playerViewModel, coroutineScope)
                }
            )
        }
    }
}

@Composable
private fun YouTubeVideoItem(
    video: YouTubeSearchManager.YouTubeVideoInfo,
    index: Int,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = PlyrSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Número de track
        Text(
            text = "${index}.",
            style = PlyrTextStyles.trackArtist(),
            modifier = Modifier.width(30.dp)
        )

        // Info del video
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = video.title,
                style = PlyrTextStyles.trackTitle(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = video.uploader,
                style = PlyrTextStyles.trackArtist(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duración
        Text(
            text = video.getFormattedDuration(),
            style = PlyrTextStyles.trackArtist()
        )
    }
}

// === FUNCIONES DE REPRODUCCIÓN ===

private fun playYouTubeVideo(
    video: YouTubeSearchManager.YouTubeVideoInfo,
    allVideos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    selectedIndex: Int,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    playerViewModel?.let { viewModel ->
        coroutineScope.launch {
            try {
                // Convertir videos de YouTube a TrackEntity para crear playlist temporal
                val youtubePlaylist = allVideos.mapIndexed { index, youtubeVideo ->
                    TrackEntity(
                        id = "youtube_${youtubeVideo.videoId}_$index",
                        playlistId = "youtube_playlist_${System.currentTimeMillis()}",
                        spotifyTrackId = "", // Empty for YouTube tracks
                        name = youtubeVideo.title,
                        artists = youtubeVideo.uploader,
                        youtubeVideoId = youtubeVideo.videoId,
                        audioUrl = null,
                        position = index,
                        lastSyncTime = System.currentTimeMillis()
                    )
                }

                // Establecer playlist en el PlayerViewModel
                viewModel.setCurrentPlaylist(youtubePlaylist, selectedIndex)

                // Inicializar player y cargar el video seleccionado
                viewModel.initializePlayer()
                viewModel.loadAudio(video.videoId, video.title)

            } catch (e: Exception) {
                println("Error playing YouTube video: ${e.message}")
            }
        }
    }
}

private fun playYouTubePlaylist(
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    if (videos.isNotEmpty()) {
        playYouTubeVideo(videos.first(), videos, 0, playerViewModel, coroutineScope)
    }
}
