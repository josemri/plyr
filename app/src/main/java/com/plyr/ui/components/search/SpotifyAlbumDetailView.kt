package com.plyr.ui.components.search

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
import coil.compose.AsyncImage
import com.plyr.database.TrackEntity
import com.plyr.network.SpotifyAlbum
import com.plyr.network.SpotifyTrack
import com.plyr.ui.components.MarqueeText
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SpotifyAlbumDetailView(
    album: SpotifyAlbum,
    tracks: List<SpotifyTrack>,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header with album info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album image
            album.images?.firstOrNull()?.url?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$ album_detail",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        color = Color(0xFF4ECDC4)
                    )
                )

                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = album.getArtistNames(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${album.total_tracks} tracks • ${album.release_date}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    )
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) {
                Text(
                    "< back",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    )
                )
            }

            TextButton(onClick = onStart) {
                Text(
                    "play",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF4ECDC4)
                    )
                )
            }

            TextButton(onClick = onRandom) {
                Text(
                    "shuffle",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD93D)
                    )
                )
            }

            TextButton(onClick = onSave) {
                Text(
                    "save",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF1DB954)
                    )
                )
            }
        }

        // Content
        when {
            isLoading -> {
                Text(
                    text = "> loading tracks...",
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

            tracks.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(tracks.size) { index ->
                        val track = tracks[index]

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    // Play individual track from album
                                    playerViewModel?.let { vm ->
                                        coroutineScope.launch {
                                            val trackEntities = tracks.mapIndexed { idx, spotifyTrack ->
                                                TrackEntity(
                                                    id = "spotify_${album.id}_${spotifyTrack.id}",
                                                    playlistId = album.id,
                                                    spotifyTrackId = spotifyTrack.id,
                                                    name = spotifyTrack.name,
                                                    artists = spotifyTrack.getArtistNames(),
                                                    youtubeVideoId = null,
                                                    audioUrl = null,
                                                    position = idx,
                                                    lastSyncTime = System.currentTimeMillis()
                                                )
                                            }
                                            vm.setCurrentPlaylist(trackEntities, index)
                                            vm.loadAudioFromTrack(trackEntities[index])
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1DB954).copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Track number
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFF95A5A6)
                                    ),
                                    modifier = Modifier.width(32.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    MarqueeText(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    )

                                    Text(
                                        text = track.getArtistNames(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = Color(0xFF95A5A6)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = track.getDurationText(),
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

            else -> {
                Text(
                    text = "> no tracks found",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
