package com.plyr.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.plyr.database.TrackEntity
import com.plyr.network.*
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

@Composable
fun SpotifySearchResults(
    searchResults: SpotifySearchAllResponse,
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit,
    onArtistSelected: (SpotifyArtistFull) -> Unit, // Nuevo callback para artistas
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tracks section
        if (searchResults.tracks.items.isNotEmpty()) {
            item {
                SpotifyTracksSection(
                    tracks = searchResults.tracks.items,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
        }

        // Albums section
        if (searchResults.albums.items.isNotEmpty()) {
            item {
                SpotifyAlbumsSection(
                    albums = searchResults.albums.items,
                    onAlbumSelected = onAlbumSelected
                )
            }
        }

        // Playlists section
        if (searchResults.playlists.items.isNotEmpty()) {
            item {
                SpotifyPlaylistsSection(
                    playlists = searchResults.playlists.items,
                    onPlaylistSelected = onPlaylistSelected
                )
            }
        }

        // Artists section
        if (searchResults.artists.items.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SpotifyArtistsSection(
                    artists = searchResults.artists.items, // Ahora son SpotifyArtistFull
                    onArtistSelected = onArtistSelected
                )
            }
        }
    }
}

@Composable
private fun SpotifyTracksSection(
    tracks: List<SpotifyTrack>,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column {
        Text(
            text = "> spotify tracks [${tracks.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        tracks.take(5).forEach { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        // Play individual track
                        playerViewModel?.let { vm ->
                            coroutineScope.launch {
                                val trackEntity = TrackEntity(
                                    id = "spotify_single_${track.id}",
                                    playlistId = "single_track",
                                    spotifyTrackId = track.id,
                                    name = track.name,
                                    artists = track.getArtistNames(),
                                    youtubeVideoId = null,
                                    audioUrl = null,
                                    position = 0,
                                    lastSyncTime = System.currentTimeMillis()
                                )
                                vm.setCurrentPlaylist(listOf(trackEntity), 0)
                                vm.loadAudioFromTrack(trackEntity)
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track icon
                Text(
                    text = "♫",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color(0xFF1DB954),
                        fontSize = 16.sp
                    ),
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            }
        }

        if (tracks.size > 5) {
            Text(
                text = "... and ${tracks.size - 5} more tracks",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF95A5A6)
                ),
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun SpotifyAlbumsSection(
    albums: List<SpotifyAlbum>,
    onAlbumSelected: (SpotifyAlbum) -> Unit
) {
    Column {
        Text(
            text = "> spotify albums [${albums.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(albums.size) { index ->
                val album = albums[index]
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .clickable { onAlbumSelected(album) }
                        .padding(8.dp)
                ) {
                    album.images?.firstOrNull()?.url?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = album.getArtistNames(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
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

@Composable
private fun SpotifyPlaylistsSection(
    playlists: List<SpotifyPlaylist>,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit
) {
    Column {
        Text(
            text = "> spotify playlists [${playlists.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(playlists.size) { index ->
                val playlist = playlists[index]
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .clickable { onPlaylistSelected(playlist) }
                        .padding(8.dp)
                ) {
                    playlist.images?.firstOrNull()?.url?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${playlist.tracks?.total ?: 0} tracks",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotifyArtistsSection(
    artists: List<SpotifyArtistFull>, // Cambiar de SpotifyArtist a SpotifyArtistFull
    onArtistSelected: (SpotifyArtistFull) -> Unit
) {
    Column {
        Text(
            text = "> spotify artists [${artists.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = Color(0xFFFFD93D)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(artists.size) { index ->
                val artist = artists[index]
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onArtistSelected(artist) } // Hacer clickeable
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Usar imagen del artista si está disponible
                    artist.images?.firstOrNull()?.url?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Imagen de ${artist.name}",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        )
                    } ?: run {
                        // Placeholder si no hay imagen
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "♫",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color(0xFF1DB954),
                                    fontSize = 24.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
