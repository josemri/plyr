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
import com.plyr.model.ScanResult
import com.plyr.network.SupabaseClient
import com.plyr.ui.components.Titulo
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.MediaMetadata
import com.plyr.utils.MediaMetadataExtractor
import com.plyr.utils.MediaType
import com.plyr.utils.NfcScanEvent
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.utils.formatTimestamp
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
 * - YouTube Playlists: Navega a SearchScreen reutilizando NfcScanEvent
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
            playYoutubeVideo(recommendation, metadata, playerViewModel)
        }
        MediaType.YOUTUBE_PLAYLIST -> {
            val playlistId = UrlParser.extractYoutubePlaylistId(url)
            if (playlistId != null) {
                NfcScanEvent.onNfcScanned(ScanResult("youtube", "playlist", playlistId))
                onNavigateToSearch()
            }
        }
        MediaType.UNKNOWN -> {
            val videoId = UrlParser.extractYoutubeVideoId(url)
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
    val videoId = UrlParser.extractYoutubeVideoId(recommendation.url) ?: return

    val track = TrackEntity(
        id = "feed_${recommendation.id}",
        playlistId = "feed_recommendations",
        remoteTrackId = "",
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
