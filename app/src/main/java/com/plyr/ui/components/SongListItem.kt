package com.plyr.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.plyr.database.TrackEntity
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyTrack
import com.plyr.utils.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.plyr.ui.theme.PlyrSpacing
import com.plyr.ui.theme.PlyrTextStyles

// Data class para unificar los datos de la canción
data class Song(
    val number: Int,
    val title: String,
    val artist: String,
    val spotifyId: String? = null,
    val youtubeId: String? = null,
    val spotifyUrl: String? = null
)

@Composable
fun SongListItem(
    song: Song,
    trackEntities: List<TrackEntity>,
    index: Int,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showPopup by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showFetchInfoDialog by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var addToPlaylistError by remember { mutableStateOf<String?>(null) }
    var addToPlaylistSuccess by remember { mutableStateOf(false) }
    var isLoadingTrackInfo by remember { mutableStateOf(false) }
    var fetchedTrackInfo by remember { mutableStateOf<SpotifyTrack?>(null) }
    var fetchInfoError by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                playerViewModel?.let { viewModel ->
                    if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                        viewModel.setCurrentPlaylist(trackEntities, index)
                        val selectedTrackEntity = trackEntities[index]
                        coroutineScope.launch {
                            try {
                                viewModel.loadAudioFromTrack(selectedTrackEntity)
                                Log.d("SongListItem", "🎵 Reproduciendo track ${index + 1}/${trackEntities.size}: ${selectedTrackEntity.name}")
                            } catch (e: Exception) {
                                Log.e("SongListItem", "Error al reproducir track", e)
                            }
                        }
                    }
                }
            }
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Text(
            text = song.number.toString(),
            style = PlyrTextStyles.trackArtist(),
            modifier = Modifier.padding(end = PlyrSpacing.small)
        )
        // Song title and artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = if (isSelected)
                    PlyrTextStyles.selectableOption(true)
                else
                    PlyrTextStyles.trackTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = PlyrTextStyles.trackArtist(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.dp)
            )
        }
        IconButton(onClick = {
            showPopup = true
        }, modifier = Modifier.size(32.dp)) {
            Text(
                text = "*", style = PlyrTextStyles.menuOption(),
                color = Color(0xFF3FFFEF)
            )
        }
    }

    if (showPopup) {
        Dialog(onDismissRequest = { showPopup = false }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF181818))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val options = listOf(
                        "add to queue",
                        "add to playlist",
                        "share",
                        "fetch info",
                        "download",
                        "delete"
                    )
                    options.forEach { option ->
                        Text(
                            text = option,
                            color = Color(0xFF3FFFEF),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (option) {
                                        "share" -> {
                                            showShareDialog = true
                                            showPopup = false
                                        }
                                        "add to queue" -> {
                                            showPopup = false
                                            playerViewModel?.let { viewModel ->
                                                if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                                    val trackToAdd = trackEntities[index]
                                                    viewModel.addToQueue(trackToAdd)
                                                    Log.d("SongListItem", "✓ Track added to queue: ${trackToAdd.name}")
                                                }
                                            }
                                        }
                                        "add to playlist" -> {
                                            // Verificar que la canción tenga Spotify ID
                                            if (song.spotifyId != null && Config.isSpotifyConnected(context)) {
                                                showPopup = false
                                                showPlaylistDialog = true
                                                isLoadingPlaylists = true
                                                addToPlaylistError = null

                                                // Cargar playlists del usuario
                                                val accessToken = Config.getSpotifyAccessToken(context)
                                                if (accessToken != null) {
                                                    SpotifyRepository.getUserPlaylists(accessToken) { playlists, error ->
                                                        isLoadingPlaylists = false
                                                        if (playlists != null) {
                                                            userPlaylists = playlists
                                                        } else {
                                                            addToPlaylistError = error ?: "Error cargando playlists"
                                                        }
                                                    }
                                                } else {
                                                    isLoadingPlaylists = false
                                                    addToPlaylistError = "Token de Spotify no disponible"
                                                }
                                            } else {
                                                Log.d("SongListItem", "No se puede añadir a playlist: sin Spotify ID o no conectado")
                                                showPopup = false
                                            }
                                        }
                                        "fetch info" -> {
                                            showPopup = false
                                            showFetchInfoDialog = true
                                            isLoadingTrackInfo = true
                                            fetchInfoError = null

                                            // Obtener información de la canción
                                            val accessToken = Config.getSpotifyAccessToken(context)
                                            if (accessToken != null && song.spotifyId != null) {
                                                SpotifyRepository.getTrackInfo(accessToken, song.spotifyId) { trackInfo, error ->
                                                    isLoadingTrackInfo = false
                                                    if (trackInfo != null) {
                                                        fetchedTrackInfo = trackInfo
                                                    } else {
                                                        fetchInfoError = error ?: "Error fetching track info"
                                                    }
                                                }
                                            } else {
                                                isLoadingTrackInfo = false
                                                fetchInfoError = "Token de Spotify no disponible"
                                            }
                                        }
                                        else -> {
                                            Log.d("SongListItemPopup", option)
                                            showPopup = false
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Diálogo de selección de playlist
    if (showPlaylistDialog) {
        Dialog(onDismissRequest = {
            showPlaylistDialog = false
            addToPlaylistSuccess = false
            addToPlaylistError = null
        }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF181818))
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Título
                    Text(
                        text = "$ add to playlist",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ECDC4)
                        )
                    )

                    // Info de la canción
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF888888)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Divider(color = Color(0xFF333333))

                    // Contenido del diálogo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        when {
                            isLoadingPlaylists -> {
                                // Estado de carga
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF4ECDC4))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "loading playlists...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF888888)
                                        )
                                    )
                                }
                            }
                            addToPlaylistSuccess -> {
                                // Éxito
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = Color(0xFF4ECDC4)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Track added successfully!",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                            addToPlaylistError != null -> {
                                // Error
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "✗",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = Color(0xFFFF6B6B)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = addToPlaylistError ?: "Error",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFFF6B6B)
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            userPlaylists.isEmpty() -> {
                                // Sin playlists
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "No playlists found",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF888888)
                                        )
                                    )
                                }
                            }
                            else -> {
                                // Lista de playlists
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(userPlaylists) { playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    // Añadir la canción a la playlist
                                                    val accessToken = Config.getSpotifyAccessToken(context)
                                                    if (accessToken != null && song.spotifyId != null) {
                                                        isLoadingPlaylists = true
                                                        SpotifyRepository.addTrackToPlaylist(
                                                            accessToken,
                                                            playlist.id,
                                                            song.spotifyId
                                                        ) { success, error ->
                                                            isLoadingPlaylists = false
                                                            if (success) {
                                                                addToPlaylistSuccess = true
                                                                Log.d("SongListItem", "✓ Canción añadida a '${playlist.name}'")
                                                                // Cerrar el diálogo después de 1.5 segundos
                                                                coroutineScope.launch {
                                                                    kotlinx.coroutines.delay(1500)
                                                                    showPlaylistDialog = false
                                                                    addToPlaylistSuccess = false
                                                                }
                                                            } else {
                                                                addToPlaylistError = error
                                                                Log.e("SongListItem", "Error añadiendo canción: $error")
                                                            }
                                                        }
                                                    }
                                                }
                                                .background(Color(0xFF252525))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = Color.White
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                playlist.description?.let { desc ->
                                                    if (desc.isNotBlank()) {
                                                        Text(
                                                            text = desc,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                color = Color(0xFF888888)
                                                            ),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = ">",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = Color(0xFF4ECDC4)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botón cerrar
                    if (!isLoadingPlaylists) {
                        TextButton(
                            onClick = {
                                showPlaylistDialog = false
                                addToPlaylistSuccess = false
                                addToPlaylistError = null
                            }
                        ) {
                            Text(
                                text = "close",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF888888)
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        ShareDialog(
            item = ShareableItem(
                spotifyId = song.spotifyId,
                spotifyUrl = song.spotifyUrl,
                youtubeId = song.youtubeId,
                title = song.title,
                artist = song.artist,
                type = ShareType.TRACK
            ),
            onDismiss = { showShareDialog = false }
        )
    }

    // Diálogo de información de la canción
    if (showFetchInfoDialog) {
        Dialog(onDismissRequest = { showFetchInfoDialog = false }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF181818))
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Título
                    Text(
                        text = "Track info",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ECDC4)
                        )
                    )

                    // Info de la canción
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF888888)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Divider(color = Color(0xFF333333))

                    // Contenido del diálogo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        when {
                            isLoadingTrackInfo -> {
                                // Estado de carga
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF4ECDC4))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "loading track info...",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF888888)
                                        )
                                    )
                                }
                            }
                            fetchInfoError != null -> {
                                // Error
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "✗",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = Color(0xFFFF6B6B)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = fetchInfoError ?: "Error",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFFF6B6B)
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            fetchedTrackInfo != null -> {
                                // Mostrar información de la canción
                                val trackInfo = fetchedTrackInfo // Local variable for smart cast
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Track ID: ${trackInfo?.id}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF888888)
                                        )
                                    )
                                    Text(
                                        text = "Name: ${trackInfo?.name}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = "Artist(s): ${trackInfo?.artists?.joinToString(", ") { it.name }}",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White
                                        )
                                    )
                                    trackInfo?.album?.let { album ->
                                        Text(
                                            text = "Album: ${album.name}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White
                                            )
                                        )
                                        album.releaseDate?.let { date ->
                                            Text(
                                                text = "Release date: $date",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFF888888)
                                                )
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Duration: ${
                                            trackInfo?.durationMs?.let { ms -> 
                                            val minutes = ms / 60000
                                            val seconds = String.format("%02d", (ms % 60000) / 1000)
                                            "$minutes:$seconds"
                                        } ?: "N/A"}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF888888)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Botón cerrar
                    if (!isLoadingTrackInfo) {
                        TextButton(
                            onClick = {
                                showFetchInfoDialog = false
                                isLoadingTrackInfo = false
                                fetchInfoError = null
                            }
                        ) {
                            Text(
                                text = "close",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF888888)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
