package com.plyr.ui.components.search

import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.database.TrackEntity
import com.plyr.model.AudioItem
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.*
import com.plyr.ui.theme.*
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun YouTubeSearchResults(
    results: List<AudioItem>?,
    youtubeAllResults: YouTubeSearchManager.YouTubeSearchAllResult?,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        youtubeAllResults?.let { allResults ->
            YouTubeCollapsibleResults(
                videos = allResults.videos,
                playlists = allResults.playlists,
                onPlaylistSelected = onPlaylistSelected,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
            PlyrMediumSpacer()
        }
        results?.let { legacy ->
            if (legacy.isNotEmpty()) {
                YouTubeLegacyResultsSection(
                    results = legacy,
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch
                )
            }
        }
    }
}

@Composable
private fun YouTubeCollapsibleResults(
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    playlists: List<YouTubeSearchManager.YouTubePlaylistInfo>,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var videosExpanded by remember { mutableStateOf(false) }
    var playlistsExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coverCache = remember { mutableStateMapOf<String, String>() }
    val youtubeManager = remember { YouTubeSearchManager(context) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PlyrSpacing.medium)) {
        if (videos.isNotEmpty()) {
            Text(
                text = if (videosExpanded) "v videos" else "> videos",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { videosExpanded = !videosExpanded }
                    .padding(bottom = PlyrSpacing.xs)
            )
            if (videosExpanded) {
                YouTubeVideosList(
                    videos = videos,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
        }
        if (playlists.isNotEmpty()) {
            Text(
                text = if (playlistsExpanded) "v playlists" else "> playlists",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .clickable { playlistsExpanded = !playlistsExpanded }
                    .padding(bottom = PlyrSpacing.xs)
            )
            if (playlistsExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PlyrSpacing.small)
                ) {
                    playlists.forEach { playlist ->
                        val coverUrl by produceState(
                            initialValue = coverCache[playlist.playlistId] ?: playlist.getImageUrl(),
                            key1 = playlist.playlistId
                        ) {
                            if (!coverCache.containsKey(playlist.playlistId)) {
                                try {
                                    val vids = youtubeManager.getYouTubePlaylistVideos(playlist.playlistId)
                                    val firstThumb = vids.firstOrNull()?.thumbnailUrl
                                    if (!firstThumb.isNullOrBlank()) {
                                        coverCache[playlist.playlistId] = firstThumb
                                        value = firstThumb
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelected(playlist) }
                                .padding(horizontal = PlyrSpacing.small, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!coverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = "playlist thumb",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
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
                }
            }
        }
    }
}

@Composable
private fun YouTubeVideosList(
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    val playlistId = remember(videos) { "youtube_search_${System.currentTimeMillis()}" }
    // Crear TrackEntity list reutilizable
    val trackEntities = remember(videos) {
        videos.mapIndexed { index, video ->
            TrackEntity(
                id = "yt_${video.videoId}",
                playlistId = playlistId,
                spotifyTrackId = "yt_${video.videoId}", // placeholder obligatorio
                name = video.title,
                artists = video.uploader,
                youtubeVideoId = video.videoId,
                audioUrl = null,
                position = index,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs)) {
        videos.forEachIndexed { index, video ->
            val isSelected = playerViewModel?.currentTrack?.value?.id == trackEntities[index].id
            SongListItem(
                song = Song(
                    number = index + 1,
                    title = video.title,
                    artist = video.uploader,
                    youtubeId = video.videoId,
                    spotifyUrl = "https://www.youtube.com/watch?v=${video.videoId}"
                ),
                trackEntities = trackEntities,
                index = index,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope,
                modifier = Modifier.fillMaxWidth(),
                isSelected = isSelected
            )
        }
    }
}

@Composable
private fun YouTubeLegacyResultsSection(
    results: List<AudioItem>,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit
) {
    Column {
        Text(
            text = "${PlyrSymbols.PROMPT} youtube results [${results.size}]",
            style = PlyrTextStyles.menuOption().copy(color = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.padding(bottom = PlyrSpacing.small)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = PlyrSpacing.large),
            verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs)
        ) {
            items(
                count = results.size,
                key = { index -> results[index].videoId }
            ) { index ->
                val item = results[index]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onVideoSelectedFromSearch(
                                item.videoId,
                                item.title,
                                results,
                                index
                            )
                        }
                        .padding(vertical = PlyrSpacing.xs, horizontal = PlyrSpacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = PlyrTextStyles.trackTitle(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = item.channel,
                            style = PlyrTextStyles.trackArtist(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = PlyrSpacing.xxs)
                        )
                    }

                    Text(
                        text = item.duration,
                        style = PlyrTextStyles.trackArtist(),
                        modifier = Modifier.padding(start = PlyrSpacing.small)
                    )
                }
            }
        }
    }
}
