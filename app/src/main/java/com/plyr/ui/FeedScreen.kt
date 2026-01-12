package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.plyr.database.TrackEntity
import com.plyr.model.Recommendation
import com.plyr.network.SupabaseClient
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.MediaMetadata
import com.plyr.utils.MediaMetadataExtractor
import com.plyr.utils.MediaType
import com.plyr.utils.NfcScanEvent
import com.plyr.utils.NfcScanResult
import com.plyr.utils.Translations
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(
    context: Context,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    playerViewModel: PlayerViewModel? = null
) {
    var recommendations by remember { mutableStateOf<List<Recommendation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var metadataCache by remember { mutableStateOf<Map<String, MediaMetadata>>(emptyMap()) }

    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()
    val scope = rememberCoroutineScope()

    // Load general group recommendations on start
    LaunchedEffect(Unit) {
        isLoading = true
        val groups = SupabaseClient.getGroups()
        val generalGroup = groups.find { it.groupType == "general" }
        if (generalGroup != null) {
            recommendations = SupabaseClient.getRecommendations(generalGroup.id)

            // Extract metadata for all recommendations
            recommendations.forEach { recommendation ->
                scope.launch {
                    val metadata = MediaMetadataExtractor.extractMetadata(recommendation.url, context)
                    metadataCache = metadataCache + (recommendation.id to metadata)
                }
            }
        }
        isLoading = false
    }

    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimensions.screenPadding)
    ) {
        Titulo(Translations.get(context, "feed_title"))
        Spacer(modifier = Modifier.height(dimensions.sectionSpacing))

        when {
            isLoading -> {
                Text(
                    text = Translations.get(context, "loading"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = dimensions.captionSize,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            recommendations.isEmpty() -> {
                Text(
                    text = Translations.get(context, "no_recommendations"),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = dimensions.captionSize,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            else -> {
                recommendations.forEach { recommendation ->
                    RecommendationItem(
                        recommendation = recommendation,
                        metadata = metadataCache[recommendation.id],
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                handleRecommendationClick(
                                    recommendation = recommendation,
                                    metadata = metadataCache[recommendation.id],
                                    playerViewModel = playerViewModel,
                                    onNavigateToSearch = onNavigateToSearch
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(dimensions.itemSpacing))
                }
            }
        }
    }
}

/**
 * Maneja el click en una recomendación según su tipo de contenido.
 * - YouTube Videos: Reproduce directamente
 * - Spotify/YouTube Playlists, Albums, Artists: Navega a SearchScreen reutilizando NfcScanEvent
 */
private suspend fun handleRecommendationClick(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    playerViewModel: PlayerViewModel?,
    onNavigateToSearch: () -> Unit
) {
    val url = recommendation.url
    val mediaType = metadata?.type ?: MediaType.UNKNOWN

    when (mediaType) {
        MediaType.YOUTUBE_VIDEO -> {
            // Reproducir directamente videos de YouTube
            playYoutubeVideo(recommendation, metadata, playerViewModel)
        }
        MediaType.YOUTUBE_PLAYLIST -> {
            // Navegar a SearchScreen para playlists de YouTube
            val playlistId = extractYoutubePlaylistId(url)
            if (playlistId != null) {
                NfcScanEvent.onNfcScanned(NfcScanResult("youtube", "playlist", playlistId))
                onNavigateToSearch()
            }
        }
        MediaType.SPOTIFY_TRACK -> {
            // Reproducir track de Spotify buscando en YouTube
            playSpotifyTrack(recommendation, metadata, playerViewModel)
        }
        MediaType.SPOTIFY_PLAYLIST -> {
            val spotifyId = extractSpotifyId(url)
            if (spotifyId != null) {
                NfcScanEvent.onNfcScanned(NfcScanResult("spotify", "playlist", spotifyId))
                onNavigateToSearch()
            }
        }
        MediaType.SPOTIFY_ALBUM -> {
            val spotifyId = extractSpotifyId(url)
            if (spotifyId != null) {
                NfcScanEvent.onNfcScanned(NfcScanResult("spotify", "album", spotifyId))
                onNavigateToSearch()
            }
        }
        MediaType.SPOTIFY_ARTIST -> {
            val spotifyId = extractSpotifyId(url)
            if (spotifyId != null) {
                NfcScanEvent.onNfcScanned(NfcScanResult("spotify", "artist", spotifyId))
                onNavigateToSearch()
            }
        }
        MediaType.UNKNOWN -> {
            // Intentar como video de YouTube
            val videoId = extractYoutubeVideoId(url)
            if (videoId != null) {
                playYoutubeVideo(recommendation, metadata, playerViewModel)
            }
        }
    }
}

private suspend fun playYoutubeVideo(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    playerViewModel: PlayerViewModel?
) {
    if (playerViewModel == null) return
    val videoId = extractYoutubeVideoId(recommendation.url) ?: return

    val track = TrackEntity(
        id = "feed_${recommendation.id}",
        playlistId = "feed_recommendations",
        spotifyTrackId = "",
        name = metadata?.title ?: recommendation.url,
        artists = metadata?.author ?: "",
        youtubeVideoId = videoId,
        audioUrl = null,
        position = 0,
        lastSyncTime = System.currentTimeMillis()
    )
    playerViewModel.setCurrentPlaylist(listOf(track), 0)
    playerViewModel.loadAudioFromTrack(track)
}

private suspend fun playSpotifyTrack(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    playerViewModel: PlayerViewModel?
) {
    if (playerViewModel == null) return

    val track = TrackEntity(
        id = "feed_${recommendation.id}",
        playlistId = "feed_recommendations",
        spotifyTrackId = extractSpotifyId(recommendation.url) ?: "",
        name = metadata?.title ?: recommendation.url,
        artists = metadata?.author ?: "",
        youtubeVideoId = null, // Se buscará dinámicamente
        audioUrl = null,
        position = 0,
        lastSyncTime = System.currentTimeMillis()
    )
    playerViewModel.setCurrentPlaylist(listOf(track), 0)
    playerViewModel.loadAudioFromTrack(track)
}

private fun extractYoutubeVideoId(url: String): String? = runCatching {
    when {
        url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
        url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
        url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
        else -> null
    }
}.getOrNull()

private fun extractYoutubePlaylistId(url: String): String? = runCatching {
    when {
        url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
        url.contains("/playlist/") -> url.substringAfterLast("/playlist/").substringBefore("?")
        else -> null
    }
}.getOrNull()

private fun extractSpotifyId(url: String): String? = runCatching {
    url.substringAfterLast("/").substringBefore("?")
}.getOrNull()

@Composable
private fun RecommendationItem(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            if (metadata?.thumbnailUrl != null) {
                AsyncImage(
                    model = metadata.thumbnailUrl,
                    contentDescription = "Thumbnail",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = metadata?.title ?: recommendation.url,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        maxLines = if (metadata?.author != null) 1 else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (metadata?.author != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = metadata.author,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatTimestamp(recommendation.createdAt)} - ${recommendation.nickname}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}
