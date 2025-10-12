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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.plyr.network.SpotifyArtistFull
import com.plyr.network.SpotifyAlbum
import com.plyr.network.SpotifyRepository
import com.plyr.ui.components.ShareDialog
import com.plyr.ui.components.ShareableItem
import com.plyr.ui.components.ShareType
import com.plyr.utils.Config
import kotlinx.coroutines.launch

/**
 * Vista detallada de un artista de Spotify
 * Muestra información del artista y sus álbumes principales
 */
@Composable
fun SpotifyArtistDetailView(
    artist: SpotifyArtistFull,
    albums: List<SpotifyAlbum>,
    isLoading: Boolean,
    error: String?,
    onAlbumClick: (SpotifyAlbum) -> Unit
) {
    var showShareDialog by remember { mutableStateOf(false) }
    var isFollowing by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingFollow by remember { mutableStateOf(true) }
    var isFollowActionLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Verificar si ya se sigue al artista
    LaunchedEffect(artist.id) {
        val accessToken = Config.getSpotifyAccessToken(context)
        if (accessToken != null) {
            SpotifyRepository.checkIfFollowingArtist(accessToken, artist.id) { following, errorMsg ->
                isCheckingFollow = false
                if (following != null) {
                    isFollowing = following
                } else {
                    android.util.Log.e("SpotifyArtistDetailView", "Error checking follow status: $errorMsg")
                }
            }
        } else {
            isCheckingFollow = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header con información del artista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Imagen del artista
            artist.images?.firstOrNull()?.url?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Imagen de ${artist.name}",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(60.dp))
                )
            } ?: run {
                // Placeholder si no hay imagen
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(60.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♫",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = Color(0xFF1DB954),
                            fontSize = 48.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Información del artista
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Géneros del artista
                if (!artist.genres.isNullOrEmpty()) {
                    Text(
                        text = artist.genres.take(3).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Botones de acción para el artista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Botón de share
            Text(
                text = "<share>",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = Color(0xFFFF6B9D)
                ),
                modifier = Modifier
                    .clickable { showShareDialog = true }
                    .padding(8.dp)
            )

            // Botón de follow/unfollow
            if (isCheckingFollow) {
                Text(
                    text = "<...>",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                val followText = when {
                    isFollowActionLoading -> "<loading...>"
                    isFollowing == true -> "<unfollow>"
                    else -> "<follow>"
                }
                val followColor = when {
                    isFollowActionLoading -> Color(0xFFFFD93D)
                    isFollowing == true -> Color(0xFFFF6B6B)
                    else -> Color(0xFF7FB069)
                }

                Text(
                    text = followText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = followColor
                    ),
                    modifier = Modifier
                        .clickable(enabled = !isFollowActionLoading) {
                            isFollowActionLoading = true
                            val accessToken = Config.getSpotifyAccessToken(context)
                            if (accessToken != null) {
                                if (isFollowing == true) {
                                    // Dejar de seguir
                                    SpotifyRepository.unfollowArtist(accessToken, artist.id) { success, errorMsg ->
                                        isFollowActionLoading = false
                                        if (success) {
                                            isFollowing = false
                                            android.util.Log.d("SpotifyArtistDetailView", "Artist unfollowed: ${artist.name}")
                                        } else {
                                            android.util.Log.e("SpotifyArtistDetailView", "Error unfollowing artist: $errorMsg")
                                        }
                                    }
                                } else {
                                    // Seguir
                                    SpotifyRepository.followArtist(accessToken, artist.id) { success, errorMsg ->
                                        isFollowActionLoading = false
                                        if (success) {
                                            isFollowing = true
                                            android.util.Log.d("SpotifyArtistDetailView", "Artist followed: ${artist.name}")
                                        } else {
                                            android.util.Log.e("SpotifyArtistDetailView", "Error following artist: $errorMsg")
                                        }
                                    }
                                }
                            } else {
                                isFollowActionLoading = false
                                android.util.Log.e("SpotifyArtistDetailView", "No access token available")
                            }
                        }
                        .padding(8.dp)
                )
            }
        }

        // Lista de álbumes
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF1DB954)
                        )
                        Text(
                            text = "Cargando álbumes...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
            albums.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron álbumes para este artista",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }
            else -> {
                LazyColumn {

                    items(albums.size) { index ->
                        val album = albums[index]
                        AlbumItem(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        ShareDialog(
            item = ShareableItem(
                spotifyId = artist.id,
                spotifyUrl = "https://open.spotify.com/artist/${artist.id}",
                youtubeId = null,
                title = artist.name,
                artist = "Artist",
                type = ShareType.ARTIST
            ),
            onDismiss = { showShareDialog = false }
        )
    }
}

/**
 * Item individual de álbum en la lista
 */
@Composable
private fun AlbumItem(
    album: SpotifyAlbum,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Imagen del álbum
        album.images?.firstOrNull()?.url?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "Imagen de ${album.name}",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color(0xFF1DB954)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Información del álbum
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Año de lanzamiento - Usar release_date en lugar de releaseDate
                album.releasedate?.take(4)?.let { year ->
                    Text(
                        text = " $year",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }

                // Número de tracks - Usar total_tracks en lugar de totalTracks
                album.totaltracks?.let { tracks ->
                    Text(
                        text = " • $tracks tracks",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }
}
