package com.plyr.ui.components.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plyr.database.TrackEntity
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.*
import com.plyr.ui.theme.*
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Consultas de portada simultáneas: acota la concurrencia de red. */
const val PLAYLIST_COVER_CONCURRENCY = 3

/**
 * Dependencias compartidas de la resolución de portadas de playlist y de la
 * reproducción de los resultados (el reproductor y su scope se hoistean desde
 * la pantalla para que sobrevivan a la recomposición).
 */
data class YouTubeSearchSectionDeps(
    val youtubeManager: YouTubeSearchManager,
    val coverCache: MutableMap<String, String>,
    val coverSemaphore: Semaphore,
    val playerViewModel: PlayerViewModel?,
    val coroutineScope: CoroutineScope,
)

/**
 * Estado completo de la sección "completa" de YouTube (vídeos + playlists) como
 * items de un [LazyListScope]: un solo parámetro mantiene la firma corta para
 * detekt (LongParameterList) y agrupa lo que la pantalla hoistea.
 */
data class YouTubeSearchResultsState(
    val deps: YouTubeSearchSectionDeps,
    val allResults: YouTubeSearchManager.YouTubeSearchAllResult,
    val videosExpanded: Boolean,
    val onToggleVideos: () -> Unit,
    val playlistsExpanded: Boolean,
    val onTogglePlaylists: () -> Unit,
    val currentTrack: TrackEntity?,
    val videoTrackEntities: List<TrackEntity>,
    val onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
)

/**
 * Resultados "completos" de YouTube (vídeos + playlists) como items de un
 * [LazyListScope]: la pantalla de búsqueda compone solo lo visible en lugar de
 * toda la lista de golpe.
 */
fun LazyListScope.youtubeSearchResultsSection(section: YouTubeSearchResultsState) {
    val videos = section.allResults.videos
    val playlists = section.allResults.playlists

    if (videos.isNotEmpty()) {
        item(key = "yt_videos_header") {
            Text(
                text = if (section.videosExpanded) "v videos" else "> videos",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { section.onToggleVideos() }
                    .padding(bottom = PlyrSpacing.xs)
            )
        }
        if (section.videosExpanded) {
            youtubeVideosItems(
                deps = section.deps,
                videos = videos,
                currentTrack = section.currentTrack,
                videoTrackEntities = section.videoTrackEntities
            )
        }
    }

    if (playlists.isNotEmpty()) {
        item(key = "yt_playlists_header") {
            Text(
                text = if (section.playlistsExpanded) "v playlists" else "> playlists",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { section.onTogglePlaylists() }
                    .padding(bottom = PlyrSpacing.xs)
            )
        }
        if (section.playlistsExpanded) {
            youtubePlaylistItems(
                deps = section.deps,
                playlists = playlists,
                onPlaylistSelected = section.onPlaylistSelected
            )
        }
    }
}

private fun LazyListScope.youtubeVideosItems(
    deps: YouTubeSearchSectionDeps,
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    currentTrack: TrackEntity?,
    videoTrackEntities: List<TrackEntity>,
) {
    items(
        count = videos.size,
        key = { index -> videos[index].videoId }
    ) { index ->
        val video = videos[index]
        val isPlaying = currentTrack?.youtubeVideoId == video.videoId ||
            currentTrack?.id == videoTrackEntities.getOrNull(index)?.id
        SongListItem(
            song = Song(
                number = index + 1,
                title = video.title,
                artist = video.uploader,
                youtubeId = video.videoId,
                shareUrl = "https://www.youtube.com/watch?v=${video.videoId}"
            ),
            trackEntities = videoTrackEntities,
            index = index,
            playerViewModel = deps.playerViewModel,
            coroutineScope = deps.coroutineScope,
            modifier = Modifier.fillMaxWidth(),
            isCurrentlyPlaying = isPlaying
        )
    }
}

private fun LazyListScope.youtubePlaylistItems(
    deps: YouTubeSearchSectionDeps,
    playlists: List<YouTubeSearchManager.YouTubePlaylistInfo>,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
) {
    items(
        count = playlists.size,
        key = { index -> playlists[index].playlistId }
    ) { index ->
        val playlist = playlists[index]
        PlaylistCoverRow(
            playlist = playlist,
            deps = deps,
            onClick = { onPlaylistSelected(playlist) }
        )
    }
}

/**
 * Resuelve la portada de una playlist de YouTube por una vez, cacheándola en
 * [YouTubeSearchSectionDeps.coverCache] con el semáforo como tope de
 * concurrencia. Fallos y ausencia de miniatura caen en el placeholder.
 */
@Composable
private fun rememberPlaylistCoverUrl(
    playlist: YouTubeSearchManager.YouTubePlaylistInfo,
    deps: YouTubeSearchSectionDeps,
): State<String?> = produceState(
    initialValue = deps.coverCache[playlist.playlistId] ?: playlist.getImageUrl(),
    key1 = playlist.playlistId
) {
    if (!deps.coverCache.containsKey(playlist.playlistId)) {
        try {
            val vids = deps.coverSemaphore.withPermit {
                deps.youtubeManager.getYouTubePlaylistVideos(playlist.playlistId)
            }
            val firstThumb = vids.firstOrNull()?.thumbnailUrl
            if (!firstThumb.isNullOrBlank()) {
                deps.coverCache[playlist.playlistId] = firstThumb
                value = firstThumb
            }
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun PlaylistCoverRow(
    playlist: YouTubeSearchManager.YouTubePlaylistInfo,
    deps: YouTubeSearchSectionDeps,
    onClick: () -> Unit
) {
    val coverUrl by rememberPlaylistCoverUrl(playlist, deps)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = PlyrSpacing.small, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "playlist thumb",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            PlaylistCoverPlaceholder(playlist)
        }
        Spacer(Modifier.width(PlyrSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = PlyrTextStyles.trackTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.uploader} • ${playlist.getFormattedVideoCount()}",
                style = PlyrTextStyles.trackArtist(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistCoverPlaceholder(playlist: YouTubeSearchManager.YouTubePlaylistInfo) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF1F2A2A),
                        Color(0xFF3B4F4E)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = playlist.title.take(1).uppercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4ECDC4)
            )
        )
    }
}