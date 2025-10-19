package com.plyr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.database.DownloadedTrackEntity
import com.plyr.database.PlaylistDatabase
import com.plyr.database.TrackEntity
import com.plyr.ui.components.*
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.DownloadManager
import kotlinx.coroutines.launch
import android.util.Log
import com.plyr.utils.Translations

@Composable
fun LocalScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var downloadedTracks by remember { mutableStateOf<List<DownloadedTrackEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<DownloadedTrackEntity?>(null) }

    // Handle back button
    BackHandler {
        onBack()
    }

    // Cargar tracks descargados
    LaunchedEffect(Unit) {
        try {
            val database = PlaylistDatabase.getDatabase(context)
            database.downloadedTrackDao().getAllDownloadedTracks().collect { tracks ->
                downloadedTracks = tracks
                isLoading = false
                Log.d("LocalScreen", "Loaded ${tracks.size} downloaded tracks")
            }
        } catch (e: Exception) {
            error = "Error loading downloaded tracks: ${e.message}"
            isLoading = false
            Log.e("LocalScreen", "Error loading tracks", e)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        
		Titulo(Translations.get(context, "plyr_local"))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF4ECDC4))
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error ?: Translations.get(context, "unknown_error"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }
            }
            downloadedTracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
				    Text(
                        text = Translations.get(context, "No tracks downloaded"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
            else -> {
                // Lista de tracks
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(downloadedTracks) { index, track ->
                        // Convertir DownloadedTrackEntity a TrackEntity para SongListItem
                        val song = Song(
                            number = index + 1,
                            title = track.name,
                            artist = track.artists,
                            spotifyId = track.spotifyTrackId,
                            youtubeId = track.youtubeVideoId
                        )

                        SongListItem(
                            song = song,
                            trackEntities = downloadedTracks.map { dt ->
                                val trackEntity = TrackEntity(
                                    id = dt.id,
                                    playlistId = "local",
                                    spotifyTrackId = dt.spotifyTrackId,
                                    name = dt.name,
                                    artists = dt.artists,
                                    youtubeVideoId = dt.youtubeVideoId,
                                    audioUrl = dt.localFilePath, // Esta ruta debe usarse para reproducción local
                                    position = downloadedTracks.indexOf(dt)
                                )
                                Log.d("LocalScreen", "TrackEntity creado - audioUrl: ${trackEntity.audioUrl}")
                                trackEntity
                            },
                            index = index,
                            playerViewModel = playerViewModel,
                            coroutineScope = coroutineScope,
                            customButtonIcon = "x",
                            customButtonAction = {
                                trackToDelete = track
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de confirmación de eliminación
    if (showDeleteDialog && trackToDelete != null) {
        PlyrConfirmDialog(
            title = Translations.get(context, "delete track"),
            message = Translations.get(context, "Song {{track_name}} will be removed permanently").replace("{{track_name}}", trackToDelete?.name ?: ""),
            confirmText = Translations.get(context, "delete"),
            cancelText = "cancel",
            onConfirm = {
                coroutineScope.launch {
                    trackToDelete?.let { track ->
                        val success = DownloadManager.deleteDownloadedTrack(context, track)
                        if (success) {
                            Log.d("LocalScreen", "Track deleted successfully")
                        } else {
                            error = "Failed to delete track"
                        }
                    }
                    showDeleteDialog = false
                    trackToDelete = null
                }
            },
            onDismiss = {
                showDeleteDialog = false
                trackToDelete = null
            }
        )
    }
}
