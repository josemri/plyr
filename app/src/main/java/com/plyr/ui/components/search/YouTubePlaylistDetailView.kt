package com.plyr.ui.components.search

import android.content.Context
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
import com.plyr.ui.components.PlaylistOrigin
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
import com.plyr.ui.utils.stableKeys
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
    val context = LocalContext.current
    val state = remember { YouTubePlaylistDetailState(context) }
    // Observar el track actual para actualización reactiva
    val currentTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }
    // Verificar si la playlist ya está guardada
    LaunchedEffect(playlist.playlistId) {
        state.refreshSaved(playlist.playlistId)
    }
    LaunchedEffect(playlist.playlistId) {
        state.load(playlist.playlistId)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
    ) {
        // Header del detalle de playlist
        PlaylistHeader(title = playlist.title)
        PlyrSmallSpacer()
        // Action bar
        PlaylistActionBar(
            isSaved = state.isSaved,
            isSaving = state.isSaving,
            hasVideos = state.videos.isNotEmpty(),
            onShuffle = { state.shuffleAndPlay(playerViewModel) },
            onToggleSave = { state.toggleSave(coroutineScope, playlist) },
            onShare = { state.showShareDialog = true }
        )
        PlyrMediumSpacer()

        // Mensaje de estado del guardado
        PlaylistSaveMessage(message = state.saveMessage)

        PlaylistContent(
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            videos = state.videos,
            trackEntities = state.trackEntities,
            playerViewModel = playerViewModel,
            currentTrack = currentTrack,
            coroutineScope = coroutineScope
        )
    }

    if (state.showShareDialog) {
        PlaylistShareDialog(
            playlist = playlist,
            onDismiss = { state.showShareDialog = false }
        )
    }
}

// Estado compartido del detalle de playlist de YouTube.
private class YouTubePlaylistDetailState(private val context: Context) {

    private val localRepository = PlaylistLocalRepository(context)

    var videos by mutableStateOf<List<YouTubeSearchManager.YouTubeVideoInfo>>(emptyList())
    var isLoading by mutableStateOf(true)
    var errorMessage by mutableStateOf<String?>(null)
    var trackEntities by mutableStateOf<List<TrackEntity>>(emptyList())
    var showShareDialog by mutableStateOf(false)

    // Estado para guardado de playlist
    var isSaved by mutableStateOf(false)
    var isSaving by mutableStateOf(false)
    var saveMessage by mutableStateOf<String?>(null)

    suspend fun refreshSaved(playlistId: String) {
        isSaved = localRepository.playlists.isYouTubePlaylistSaved(playlistId)
    }

    suspend fun load(playlistId: String) {
        try {
            isLoading = true
            errorMessage = null
            val playlistVideos = loadPlaylistVideos(context, playlistId)
            videos = playlistVideos
            // Crear TrackEntities para reproducción
            trackEntities = buildTrackEntities(playlistId, playlistVideos)
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error loading playlist: ${e.message}"
            isLoading = false
        }
    }

    fun shuffleAndPlay(playerViewModel: PlayerViewModel?) {
        if (trackEntities.isNotEmpty() && playerViewModel != null) {
            val shuffled = trackEntities.shuffled()
            // Sustituir también la lista local para que la UI (orden,
            // highlight de reproducción e índices) siga a la cola (B9)
            trackEntities = shuffled
            videos = shuffled.mapNotNull { shuffledTrack ->
                videos.firstOrNull { it.videoId == shuffledTrack.remoteTrackId }
            }
            playerViewModel.setCurrentPlaylist(shuffled, 0)
            playerViewModel.playback.play(shuffled.first())
        }
    }

    fun toggleSave(
        coroutineScope: CoroutineScope,
        playlist: YouTubeSearchManager.YouTubePlaylistInfo
    ) {
        if (isSaved) {
            // Eliminar playlist guardada
            coroutineScope.launch {
                localRepository.playlists.deleteYouTubePlaylist(playlist.playlistId)
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
                val success = localRepository.playlists.saveYouTubePlaylist(
                    playlistId = playlist.playlistId,
                    title = playlist.title,
                    uploader = playlist.uploader,
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
}

@Composable
private fun PlaylistHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$ $title",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4ECDC4)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlaylistActionBar(
    isSaved: Boolean,
    isSaving: Boolean,
    hasVideos: Boolean,
    onShuffle: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.large)
    ) {
        Text(
            text = "<rnd>",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.clickable(enabled = hasVideos, onClick = onShuffle)
        )
        Text(
            text = if (isSaved) "<saved>" else "<save>",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = if (isSaved) Color(0xFFFFD93D) else Color(0xFF7FB069)
            ),
            modifier = Modifier.clickable(enabled = !isSaving && hasVideos, onClick = onToggleSave)
        )
        Text(
            text = "<shr>",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFF6B9D)
            ),
            modifier = Modifier.clickable(onClick = onShare)
        )
    }
}

@Composable
private fun PlaylistSaveMessage(message: String?) {
    message?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error
            )
        )
        PlyrSmallSpacer()
    }
}

@Composable
private fun PlaylistContent(
    isLoading: Boolean,
    errorMessage: String?,
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    trackEntities: List<TrackEntity>,
    playerViewModel: PlayerViewModel?,
    currentTrack: TrackEntity?,
    coroutineScope: CoroutineScope
) {
    when {
        isLoading -> PlyrLoadingIndicator(text = "loading playlist")
        errorMessage != null -> PlyrErrorText(errorMessage)
        videos.isEmpty() -> PlyrInfoText("No videos found in this playlist")
        else -> PlaylistTracksList(
            videos = videos,
            trackEntities = trackEntities,
            playerViewModel = playerViewModel,
            currentTrack = currentTrack,
            coroutineScope = coroutineScope
        )
    }
}

@Composable
private fun PlaylistTracksList(
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    trackEntities: List<TrackEntity>,
    playerViewModel: PlayerViewModel?,
    currentTrack: TrackEntity?,
    coroutineScope: CoroutineScope
) {
    // Listado SongListItem con duración
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PlyrSpacing.large),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val itemKeys = stableKeys(videos.map { it.videoId })
        items(videos.size, key = { idx -> itemKeys[idx] }) { idx ->
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

@Composable
private fun PlaylistShareDialog(
    playlist: YouTubeSearchManager.YouTubePlaylistInfo,
    onDismiss: () -> Unit
) {
    ShareDialog(
        item = ShareableItem(
            remoteId = null,
            shareUrl = null,
            youtubeId = playlist.playlistId,
            title = playlist.title,
            artist = "YouTube Playlist",
            type = ShareType.PLAYLIST,
            // Viene de buscar en YouTube, así que el origen está claro.
            playlistOrigin = PlaylistOrigin.YOUTUBE,
        ),
        onDismiss = onDismiss
    )
}

private suspend fun loadPlaylistVideos(
    context: Context,
    playlistId: String
): List<YouTubeSearchManager.YouTubeVideoInfo> {
    val youtubeSearchManager = YouTubeSearchManager(context)
    return youtubeSearchManager.getYouTubePlaylistVideos(playlistId)
}

private fun buildTrackEntities(
    playlistId: String,
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>
): List<TrackEntity> = videos.mapIndexed { index, video ->
    TrackEntity(
        id = "ytpl_${playlistId}_${video.videoId}_$index",
        playlistId = "youtube_$playlistId",
        remoteTrackId = video.videoId,
        name = video.title,
        artists = video.uploader,
        youtubeVideoId = video.videoId,
        audioUrl = null,
        position = index,
        lastSyncTime = System.currentTimeMillis()
    )
}
