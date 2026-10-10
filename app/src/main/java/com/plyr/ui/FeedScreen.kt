package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import com.plyr.ui.utils.ResponsiveDimensions
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.MediaMetadata
import com.plyr.utils.MediaMetadataExtractor
import com.plyr.utils.MediaType
import com.plyr.utils.NfcScanEvent
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.utils.formatTimestamp
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Extracciones de metadatos simultáneas: acota la concurrencia de red. */
private const val METADATA_CONCURRENCY = 4

@Composable
fun FeedScreen(
    context: Context,
    onBack: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    playerViewModel: PlayerViewModel? = null
) {
    var recommendations by remember { mutableStateOf<List<Recommendation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    // `mutableStateMapOf` en vez de reasignar un `Map` inmutable: el insert pasa a
    // ser O(1) y atómico, y no reconstruye el mapa entero en cada extracción.
    val metadataCache = remember { mutableStateMapOf<String, MediaMetadata>() }

    val haptic = LocalHapticFeedback.current
    val dimensions = calculateResponsiveDimensionsFallback()

    // Load general group recommendations on start
    LaunchedEffect(Unit) {
        isLoading = true
        recommendations = fetchFeedRecommendations()
        isLoading = false

        // Extracción de metadatos acotada: como máximo METADATA_CONCURRENCY a la vez,
        // y todas hijas de este efecto (se cancelan al salir de composición).
        extractRecommendationMetadata(recommendations, metadataCache)
    }

    BackHandler { onBack() }

    FeedList(
        context = context,
        recommendations = recommendations,
        isLoading = isLoading,
        metadataCache = metadataCache,
        dimensions = dimensions,
        onRecommendationClick = { recommendation ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            handleRecommendationClick(
                recommendation = recommendation,
                metadata = metadataCache[recommendation.id],
                playerViewModel = playerViewModel,
                onNavigateToSearch = onNavigateToSearch
            )
        }
    )
}

@Composable
private fun FeedList(
    context: Context,
    recommendations: List<Recommendation>,
    isLoading: Boolean,
    metadataCache: Map<String, MediaMetadata>,
    dimensions: ResponsiveDimensions,
    onRecommendationClick: (Recommendation) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.screenPadding)
    ) {
        item {
            Titulo(Translations.get(context, "feed_title"))
            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
        }

        when {
            isLoading -> {
                item { FeedMessage(Translations.get(context, "loading"), dimensions) }
            }
            recommendations.isEmpty() -> {
                item { FeedMessage(Translations.get(context, "no_recommendations"), dimensions) }
            }
            else -> {
                items(recommendations, key = { it.id }) { recommendation ->
                    RecommendationItem(
                        recommendation = recommendation,
                        metadata = metadataCache[recommendation.id],
                        onClick = { onRecommendationClick(recommendation) }
                    )
                    Spacer(modifier = Modifier.height(dimensions.itemSpacing))
                }
            }
        }
    }
}

@Composable
private fun FeedMessage(text: String, dimensions: ResponsiveDimensions) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = dimensions.captionSize,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

private suspend fun fetchFeedRecommendations(): List<Recommendation> {
    val groups = SupabaseClient.getGroups()
    val generalGroup = groups.find { it.groupType == "general" }
    return if (generalGroup != null) {
        SupabaseClient.getRecommendations(generalGroup.id)
    } else {
        emptyList()
    }
}

private suspend fun extractRecommendationMetadata(
    recommendations: List<Recommendation>,
    metadataCache: MutableMap<String, MediaMetadata>
) {
    val semaphore = Semaphore(METADATA_CONCURRENCY)
    coroutineScope {
        recommendations.forEach { recommendation ->
            launch {
                semaphore.withPermit {
                    metadataCache[recommendation.id] =
                        MediaMetadataExtractor.extractMetadata(recommendation.url)
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
private fun handleRecommendationClick(
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

private fun playYoutubeVideo(
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
    playerViewModel.playback.play(track)
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
            RecommendationThumbnail(metadata)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                RecommendationTexts(recommendation, metadata)
                RecommendationFooter(recommendation)
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun RecommendationThumbnail(metadata: MediaMetadata?) {
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
}

@Composable
private fun ColumnScope.RecommendationTexts(
    recommendation: Recommendation,
    metadata: MediaMetadata?
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
}

@Composable
private fun RecommendationFooter(recommendation: Recommendation) {
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
