package com.plyr.ui.components

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plyr.database.TrackEntity
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyTrack
import com.plyr.utils.Config
import com.plyr.utils.Translations
import com.plyr.viewmodel.PlayerViewModel

/**
 * Data class para pasar información de la canción al menú
 */
data class SongMenuData(
    val title: String,
    val artist: String,
    val spotifyId: String? = null,
    val youtubeId: String? = null,
    val trackEntity: TrackEntity? = null
)

/**
 * Helper function para verificar si es un ID de Spotify real
 */
private fun isValidSpotifyId(id: String?): Boolean {
    if (id == null || id.isBlank()) return false
    if (id.startsWith("recommended_") || id.startsWith("temp_")) return false
    return true
}

/**
 * Diálogo de menú de canción reutilizable
 */
@Composable
fun SongMenuDialog(
    context: Context,
    songData: SongMenuData,
    playerViewModel: PlayerViewModel?,
    onDismiss: () -> Unit,
    onLikedStatusChanged: (() -> Unit)? = null
) {
    var isLoadingTrackInfo by remember { mutableStateOf(false) }
    var fetchedTrackInfo by remember { mutableStateOf<SpotifyTrack?>(null) }
    var fetchInfoError by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf<Boolean?>(null) }
    
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var addToPlaylistError by remember { mutableStateOf<String?>(null) }
    var addToPlaylistSuccess by remember { mutableStateOf(false) }

    // Cargar información de la canción cuando se abre el popup
    LaunchedEffect(Unit) {
        val sId = songData.spotifyId
        if (!isValidSpotifyId(sId)) {
            isLoadingTrackInfo = false
            fetchInfoError = null
            isLiked = null
        } else {
            isLoadingTrackInfo = true
            fetchInfoError = null
            val accessToken = Config.getSpotifyAccessToken(context)
            if (accessToken != null) {
                SpotifyRepository.getTrackInfo(accessToken, sId!!) { trackInfo, error ->
                    isLoadingTrackInfo = false
                    if (trackInfo != null) {
                        fetchedTrackInfo = trackInfo
                    } else {
                        fetchInfoError = error ?: "Error fetching track info"
                    }
                }
                SpotifyRepository.checkSavedTrack(accessToken, sId) { liked, error ->
                    if (error == null) {
                        isLiked = liked
                    }
                }
            } else {
                isLoadingTrackInfo = false
                fetchInfoError = "Token de Spotify no disponible"
            }
        }
    }

    Dialog(onDismissRequest = {
        onDismiss()
    }) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
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
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = Translations.get(context, "loading"),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                        fetchInfoError != null -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "✗",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = MaterialTheme.colorScheme.error
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = fetchInfoError ?: "Error",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.error
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        fetchedTrackInfo != null -> {
                            val trackInfo = fetchedTrackInfo
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text(
                                        text = trackInfo?.name ?: songData.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                item {
                                    Text(
                                        text = trackInfo?.artists?.joinToString(", ") { it.name } ?: songData.artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                                trackMetadataSection(
                                    albumName = trackInfo?.album?.name,
                                    releaseDate = trackInfo?.album?.releaseDate,
                                    durationMs = trackInfo?.durationMs
                                )
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = songData.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = songData.artist,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
                        text = Translations.get(context, "add_to_playlist"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isValidSpotifyId(songData.spotifyId) && Config.isSpotifyConnected(context)) {
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
                                    Log.d("SongMenuDialog", "No se puede añadir a playlist: spotifyId inválido o no conectado")
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 4.dp)
                    )

                    // Add to Queue
                    Text(
                        text = Translations.get(context, "add_to_queue"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                songData.trackEntity?.let { track ->
                                    playerViewModel?.addToQueue(track)
                                    Log.d("SongMenuDialog", "✓ Track added to queue: ${track.name}")
                                }
                            }
                            .padding(vertical = 4.dp)
                    )

                    // Share
                    Text(
                        text = Translations.get(context, "share"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                val shareText = "${songData.title} - ${songData.artist}"
                                val shareUrl = songData.spotifyId?.let { "https://open.spotify.com/track/$it" }
                                val fullShareText = if (isValidSpotifyId(songData.spotifyId) && shareUrl != null) 
                                    "$shareText\n$shareUrl" else shareText
                                
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, fullShareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                            .padding(vertical = 4.dp)
                    )

                    // Like / Unlike
                    Text(
                        text = if (isLiked == true) Translations.get(context, "remove_from_liked_songs") 
                               else Translations.get(context, "add_to_liked_songs"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                isLiked?.let { currentlyLiked ->
                                    val accessToken = Config.getSpotifyAccessToken(context)
                                    if (accessToken != null && isValidSpotifyId(songData.spotifyId)) {
                                        if (currentlyLiked) {
                                            SpotifyRepository.removeTrack(accessToken, songData.spotifyId!!) { success, error ->
                                                if (success) {
                                                    isLiked = false
                                                    Log.d("SongMenuDialog", "✓ Canción quitada de Liked Songs")
                                                    onLikedStatusChanged?.invoke()
                                                } else {
                                                    Log.e("SongMenuDialog", "Error quitando canción de Liked Songs: $error")
                                                }
                                            }
                                        } else {
                                            SpotifyRepository.saveTrack(accessToken, songData.spotifyId!!) { success, error ->
                                                if (success) {
                                                    isLiked = true
                                                    Log.d("SongMenuDialog", "✓ Canción añadida a Liked Songs")
                                                    onLikedStatusChanged?.invoke()
                                                } else {
                                                    Log.e("SongMenuDialog", "Error añadiendo canción a Liked Songs: $error")
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
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Translations.get(context, "select_playlist"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    when {
                        isLoadingPlaylists -> {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                        addToPlaylistError != null -> {
                            Text(
                                text = addToPlaylistError ?: "Error",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        addToPlaylistSuccess -> {
                            Text(
                                text = "✓ ${Translations.get(context, "added_to_playlist")}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(userPlaylists) { playlist ->
                                    Text(
                                        text = playlist.name,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val accessToken = Config.getSpotifyAccessToken(context)
                                                if (accessToken != null && songData.spotifyId != null) {
                                                    SpotifyRepository.addTrackToPlaylist(
                                                        accessToken,
                                                        playlist.id,
                                                        songData.spotifyId
                                                    ) { success, error ->
                                                        if (success) {
                                                            addToPlaylistSuccess = true
                                                        } else {
                                                            addToPlaylistError = error
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(vertical = 8.dp)
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
