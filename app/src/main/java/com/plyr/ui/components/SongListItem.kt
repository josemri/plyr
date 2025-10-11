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
import com.plyr.ui.PlaylistsScreen
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
    isSelected: Boolean = false,
    onLikedStatusChanged: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showPopup by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<com.plyr.network.SpotifyPlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var addToPlaylistError by remember { mutableStateOf<String?>(null) }
    var addToPlaylistSuccess by remember { mutableStateOf(false) }
    var isLoadingTrackInfo by remember { mutableStateOf(false) }
    var fetchedTrackInfo by remember { mutableStateOf<com.plyr.network.SpotifyTrack?>(null) }
    var fetchInfoError by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingLiked by remember { mutableStateOf(false) }

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
        // Cargar información de la canción cuando se abre el popup
        LaunchedEffect(true) {
            if (showPopup && song.spotifyId != null) {
                isLoadingTrackInfo = true
                isCheckingLiked = true
                fetchInfoError = null
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    // Obtener info del track
                    SpotifyRepository.getTrackInfo(accessToken, song.spotifyId) { trackInfo, error ->
                        isLoadingTrackInfo = false
                        if (trackInfo != null) {
                            fetchedTrackInfo = trackInfo
                        } else {
                            fetchInfoError = error ?: "Error fetching track info"
                        }
                    }

                    // Verificar si está en Liked Songs
                    SpotifyRepository.checkSavedTrack(accessToken, song.spotifyId) { liked, error ->
                        isCheckingLiked = false
                        if (error == null) {
                            isLiked = liked
                        }
                    }
                } else {
                    isLoadingTrackInfo = false
                    isCheckingLiked = false
                    fetchInfoError = "Token de Spotify no disponible"
                }
            }
        }

        Dialog(onDismissRequest = {
            showPopup = false
            fetchedTrackInfo = null
            fetchInfoError = null
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
                    // Sección de información del track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
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
                                        text = "loading...",
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
                                        style = MaterialTheme.typography.displayMedium.copy(
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
                                val trackInfo = fetchedTrackInfo
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Text(
                                            text = trackInfo?.name ?: song.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    item {
                                        Text(
                                            text = trackInfo?.artists?.joinToString(", ") { it.name } ?: song.artist,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(0xFFAAAAAA)
                                            )
                                        )
                                    }
                                    trackInfo?.album?.let { album ->
                                        item {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Album: ${album.name}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFF888888)
                                                )
                                            )
                                        }
                                        album.releaseDate?.let { date ->
                                            item {
                                                Text(
                                                    text = "Release: $date",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color(0xFF888888)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Text(
                                            text = "Duration: ${trackInfo?.durationMs?.let { ms -> 
                                                val minutes = ms / 60000
                                                val seconds = "%02d".format((ms % 60000) / 1000)
                                                "$minutes:$seconds"
                                            } ?: "N/A"}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF888888)
                                            )
                                        )
                                    }
                                }
                            }
                            else -> {
                                // Mostrar info básica mientras carga
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFFAAAAAA)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Botones de acción
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Add to Playlist
                        Text(
                            text = "add to playlist",
                            color = Color(0xFF3FFFEF),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (song.spotifyId != null && Config.isSpotifyConnected(context)) {
                                        showPopup = false
                                        showPlaylistDialog = true
                                        isLoadingPlaylists = true
                                        addToPlaylistError = null

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
                                .padding(vertical = 4.dp)
                        )

                        // Add to Queue
                        Text(
                            text = "add to queue",
                            color = Color(0xFF3FFFEF),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPopup = false
                                    playerViewModel?.let { viewModel ->
                                        if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                            val trackToAdd = trackEntities[index]
                                            viewModel.addToQueue(trackToAdd)
                                            Log.d("SongListItem", "✓ Track added to queue: ${trackToAdd.name}")
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Share
                        Text(
                            text = "share",
                            color = Color(0xFF3FFFEF),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareDialog = true
                                    showPopup = false
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Like / Unlike
                        Text(
                            text = if (isLiked == true) "remove from liked songs" else "add to liked songs",
                            color = Color(0xFF3FFFEF),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Acción de agregar/quitar de Liked Songs
                                    showPopup = false
                                    isLiked?.let { currentlyLiked ->
                                        val accessToken = Config.getSpotifyAccessToken(context)
                                        if (accessToken != null && song.spotifyId != null) {
                                            isLoadingTrackInfo = true
                                            if (currentlyLiked) {
                                                // Quitar de Liked Songs
                                                SpotifyRepository.removeTrack(accessToken, song.spotifyId) { success, error ->
                                                    isLoadingTrackInfo = false
                                                    if (success) {
                                                        isLiked = false
                                                        Log.d("SongListItem", "✓ Canción quitada de Liked Songs")
                                                        onLikedStatusChanged?.invoke()
                                                    } else {
                                                        Log.e("SongListItem", "Error quitando canción de Liked Songs: $error")
                                                    }
                                                }
                                            } else {
                                                // Añadir a Liked Songs
                                                SpotifyRepository.saveTrack(accessToken, song.spotifyId) { success, error ->
                                                    isLoadingTrackInfo = false
                                                    if (success) {
                                                        isLiked = true
                                                        Log.d("SongListItem", "✓ Canción añadida a Liked Songs")
                                                        onLikedStatusChanged?.invoke()
                                                    } else {
                                                        Log.e("SongListItem", "Error añadiendo canción a Liked Songs: $error")
                                                    }
                                                }
                                            }
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
}
