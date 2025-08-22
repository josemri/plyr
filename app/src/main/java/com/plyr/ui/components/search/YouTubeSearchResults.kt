package com.plyr.ui.components.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.model.AudioItem
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.*
import com.plyr.ui.theme.*

@Composable
fun YouTubeSearchResults(
    results: List<AudioItem>?,
    youtubeAllResults: YouTubeSearchManager.YouTubeSearchAllResult?,
    onVideoSelected: (String, String) -> Unit,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit
) {
    Column {
        // Mostrar playlists de YouTube si están disponibles
        youtubeAllResults?.let { allResults ->
            if (allResults.playlists.isNotEmpty()) {
                YouTubePlaylistsSection(
                    playlists = allResults.playlists,
                    onPlaylistSelected = onPlaylistSelected
                )

                PlyrMediumSpacer()
            }

            if (allResults.videos.isNotEmpty()) {
                YouTubeVideosFromSearchSection(
                    videos = allResults.videos,
                    onVideoSelected = onVideoSelected,
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch
                )
            }
        }

        // Mostrar resultados legacy si están disponibles
        results?.let { audioItems ->
            if (audioItems.isNotEmpty()) {
                YouTubeLegacyResultsSection(
                    results = audioItems,
                    onVideoSelected = onVideoSelected,
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch
                )
            }
        }
    }
}

@Composable
private fun YouTubePlaylistsSection(
    playlists: List<YouTubeSearchManager.YouTubePlaylistInfo>,
    onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit
) {
    Column {
        Text(
            text = "${PlyrSymbols.PROMPT} youtube playlists [${playlists.size}]",
            style = PlyrTextStyles.menuOption().copy(color = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.padding(bottom = PlyrSpacing.small)
        )

        playlists.forEach { playlist ->
            PlyrListItem(
                title = playlist.title,
                subtitle = "${playlist.uploader} ${PlyrSymbols.BULLET} ${playlist.getFormattedVideoCount()}",
                onClick = { onPlaylistSelected(playlist) },
                modifier = Modifier.padding(vertical = PlyrSpacing.xxs)
            )
        }
    }
}

@Composable
private fun YouTubeVideosFromSearchSection(
    videos: List<YouTubeSearchManager.YouTubeVideoInfo>,
    onVideoSelected: (String, String) -> Unit,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit
) {
    Column {
        Text(
            text = "${PlyrSymbols.PROMPT} youtube videos [${videos.size}]",
            style = PlyrTextStyles.menuOption().copy(color = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.padding(bottom = PlyrSpacing.small)
        )

        // Convertir YouTubeVideoInfo a AudioItem para compatibilidad
        val audioItems = videos.map { video ->
            AudioItem(
                videoId = video.videoId,
                title = video.title,
                url = "", // Empty URL as it's not needed for YouTube videos
                channel = video.uploader,
                duration = video.getFormattedDuration()
            )
        }

        audioItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onVideoSelectedFromSearch(
                            item.videoId,
                            item.title,
                            audioItems,
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

                item.duration?.let { duration ->
                    Text(
                        text = duration,
                        style = PlyrTextStyles.trackArtist(),
                        modifier = Modifier.padding(start = PlyrSpacing.small)
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeLegacyResultsSection(
    results: List<AudioItem>,
    onVideoSelected: (String, String) -> Unit,
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

                    item.duration?.let { duration ->
                        Text(
                            text = duration,
                            style = PlyrTextStyles.trackArtist(),
                            modifier = Modifier.padding(start = PlyrSpacing.small)
                        )
                    }
                }
            }
        }
    }
}
