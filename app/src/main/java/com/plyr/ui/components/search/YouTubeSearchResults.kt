package com.plyr.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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

private const val SONG_PACK_SIZE = 5

/** Ancho de un paquete de canciones y separación común de los carruseles (M36). */
private val SONG_PACK_WIDTH = 280.dp
private val CAROUSEL_SPACING = 12.dp

private fun LazyListScope.youtubeVideosItems(
    deps: YouTubeSearchSectionDeps,
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    currentTrack: TrackEntity?,
    videoTrackEntities: List<TrackEntity>,
) {
    item(key = "yt_videos_carousel") {
        SongPacksCarousel(
            deps = deps,
            videos = videos,
            currentTrack = currentTrack,
            videoTrackEntities = videoTrackEntities
        )
    }
}

/**
 * Carrusel de canciones (M36): los resultados se agrupan en paquetes de
 * [SONG_PACK_SIZE] apilados uno encima del otro —solo número, título y artista—
 * y cada paquete es un paso del desplazamiento horizontal.
 *
 * Las filas van con `swipeEnabled = false`: el gesto horizontal del swipe se
 * comería el desplazamiento del carrusel. El resto de acciones (me gusta, cola,
 * lista, compartir) sigue disponible en el menú `*` de cada fila.
 */
@Composable
private fun SongPacksCarousel(
    deps: YouTubeSearchSectionDeps,
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    currentTrack: TrackEntity?,
    videoTrackEntities: List<TrackEntity>,
) {
    val packCount = (videos.size + SONG_PACK_SIZE - 1) / SONG_PACK_SIZE
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CAROUSEL_SPACING),
        contentPadding = PaddingValues(horizontal = PlyrSpacing.small)
    ) {
        items(packCount, key = { pack -> "yt_pack_$pack" }) { pack ->
            val start = pack * SONG_PACK_SIZE
            Column(
                modifier = Modifier.width(SONG_PACK_WIDTH),
                verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs)
            ) {
                videos.subList(start, minOf(start + SONG_PACK_SIZE, videos.size))
                    .forEachIndexed { offset, video ->
                        val index = start + offset
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
                            isCurrentlyPlaying = isPlaying,
                            swipeEnabled = false
                        )
                    }
            }
        }
    }
}

private fun LazyListScope.youtubePlaylistItems(
    deps: YouTubeSearchSectionDeps,
    playlists: List<YouTubeSearchManager.YouTubePlaylistInfo>,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
) {
    item(key = "yt_playlists_carousel") {
        PlaylistSearchCarousel(
            deps = deps,
            playlists = playlists,
            onPlaylistSelected = onPlaylistSelected
        )
    }
}

/**
 * Carrusel de listas (M36): las mismas tarjetas que el carrusel de la Home, con
 * el autor y el número de pistas como segunda línea.
 */
@Composable
private fun PlaylistSearchCarousel(
    deps: YouTubeSearchSectionDeps,
    playlists: List<YouTubeSearchManager.YouTubePlaylistInfo>,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CAROUSEL_SPACING),
        contentPadding = PaddingValues(horizontal = PlyrSpacing.small)
    ) {
        items(playlists.size, key = { playlists[it].playlistId }) { index ->
            val playlist = playlists[index]
            val coverUrl by rememberPlaylistCoverUrl(playlist, deps)
            PlaylistCoverCard(
                coverUrl = coverUrl,
                title = playlist.title,
                subtitle = "${playlist.uploader} • ${playlist.getFormattedVideoCount()}",
                onClick = { onPlaylistSelected(playlist) }
            )
        }
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
