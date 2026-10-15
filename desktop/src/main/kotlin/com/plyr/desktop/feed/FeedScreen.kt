package com.plyr.desktop.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Subtitulo
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.model.Recommendation
import com.plyr.utils.MediaMetadata
import com.plyr.utils.MediaType
import com.plyr.utils.UrlParser
import com.plyr.utils.formatTimestamp

/**
 * Feed de recomendaciones del escritorio (M12).
 *
 * Es el port de `FeedScreen` de Android sobre el [FeedController]: lista las
 * recomendaciones del grupo seleccionado (el general por defecto), enriquece cada
 * una con los metadatos de YouTube (título, autor y miniatura, M23) y permite
 * fijar el apodo y publicar una nueva. Al pulsar una entrada se reproduce el
 * vídeo o se abre la lista, según su tipo (igual que Android); además se conserva
 * lo propio del escritorio: el comentario de cada recomendación y el selector de
 * grupo.
 */
@Composable
fun FeedScreen(
    controller: FeedController,
    onBack: () -> Unit,
    onPlayVideo: (videoId: String, title: String, artist: String) -> Unit = { _, _, _ -> },
    onOpenPlaylist: (playlistId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    LaunchedEffect(controller) { controller.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
            .testTag("screen_FEED"),
    ) {
        Titulo(Translations.get("feed_title"))

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isLoading && state.recommendations.isEmpty() -> {
                    item { FeedMessage(Translations.get("loading")) }
                }
                state.recommendations.isEmpty() -> {
                    item { FeedMessage(Translations.get("no_recommendations")) }
                }
                else -> {
                    items(state.recommendations, key = { it.id }) { recommendation ->
                        val metadata = state.metadata[recommendation.id]
                        RecommendationRow(
                            recommendation = recommendation,
                            metadata = metadata,
                            onClick = {
                                handleRecommendationClick(recommendation, metadata, onPlayVideo, onOpenPlaylist)
                            },
                        )
                        Spacer(Modifier.height(PlyrSpacing.small))
                    }
                }
            }

            item { Spacer(Modifier.height(PlyrSpacing.large)) }
            item { Subtitulo(Translations.get("add_recommendation")) }
            item { NewRecommendationForm(state, controller) }
            item {
                FeedStatusMessage(
                    message = state.statusMessage,
                    isError = state.statusIsError,
                    tag = "feed_status",
                )
            }
        }

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_FEED"),
        )
    }
}

/** Mensaje de lista vacía o cargando. */
@Composable
private fun FeedMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        ),
        modifier = Modifier.padding(vertical = PlyrSpacing.large),
    )
}

/**
 * Una recomendación pulsable: miniatura (si se conoce) y, junto a ella, el
 * comentario (si lo hay), el título/autor del vídeo (o su URL sin metadatos) y la
 * antigüedad con el apodo. Al pulsarla se reproduce o se abre la lista (M23).
 */
@Composable
private fun RecommendationRow(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recommendation_${recommendation.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedThumbnail(metadata?.thumbnailUrl)
        Column(modifier = Modifier.weight(1f)) {
            if (!recommendation.comment.isNullOrBlank()) {
                Text(
                    text = recommendation.comment.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = metadata?.title ?: recommendation.url,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metadata?.author?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${formatTimestamp(recommendation.createdAt)} - ${recommendation.nickname}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Miniatura del vídeo, si el extractor la devolvió. */
@Composable
private fun FeedThumbnail(url: String?) {
    if (url.isNullOrBlank()) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
    )
    Spacer(Modifier.width(PlyrSpacing.small))
}

/**
 * Maneja el click en una recomendación según su tipo de contenido (M23), igual
 * que Android: los vídeos de YouTube se reproducen y las listas se abren en la
 * pantalla de búsqueda; si no hay metadatos pero la URL lleva un id de vídeo, se
 * reproduce igualmente.
 */
internal fun handleRecommendationClick(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    onPlayVideo: (videoId: String, title: String, artist: String) -> Unit,
    onOpenPlaylist: (playlistId: String) -> Unit,
) {
    val url = recommendation.url
    when (metadata?.type ?: MediaType.UNKNOWN) {
        MediaType.YOUTUBE_VIDEO -> playYoutubeVideo(recommendation, metadata, onPlayVideo)
        MediaType.YOUTUBE_PLAYLIST -> UrlParser.extractYoutubePlaylistId(url)?.let(onOpenPlaylist)
        MediaType.UNKNOWN -> {
            val videoId = UrlParser.extractYoutubeVideoId(url)
            if (videoId != null) onPlayVideo(videoId, metadata?.title ?: url, metadata?.author.orEmpty())
        }
    }
}

/** Reproduce un vídeo de YouTube usando el título/autor extraídos como metadatos. */
private fun playYoutubeVideo(
    recommendation: Recommendation,
    metadata: MediaMetadata?,
    onPlayVideo: (videoId: String, title: String, artist: String) -> Unit,
) {
    val videoId = UrlParser.extractYoutubeVideoId(recommendation.url) ?: return
    onPlayVideo(videoId, metadata?.title ?: recommendation.url, metadata?.author.orEmpty())
}

/** Formulario para fijar el apodo y publicar una recomendación en un grupo. */
@Composable
private fun NewRecommendationForm(state: FeedState, controller: FeedController) {
    OutlinedTextField(
        value = state.nickname,
        onValueChange = controller::onNicknameChange,
        label = { Text(Translations.get("your_nickname")) },
        supportingText = { Text(Translations.get("nickname_description")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("feed_nickname"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            data = ActionButtonData(
                text = Translations.get("save_nickname"),
                onClick = controller::saveNickname,
            ),
            modifier = Modifier.testTag("feed_save_nickname"),
        )
        state.nicknameMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("feed_nickname_status"),
            )
        }
    }

    Spacer(Modifier.height(PlyrSpacing.small))
    GroupSelector(state, controller)

    OutlinedTextField(
        value = state.url,
        onValueChange = controller::onUrlChange,
        label = { Text(Translations.get("recommendation_url")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("feed_url"),
    )
    OutlinedTextField(
        value = state.comment,
        onValueChange = controller::onCommentChange,
        label = { Text(Translations.get("comment_optional")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("feed_comment"),
    )
    ActionButton(
        data = ActionButtonData(
            text = if (state.isPosting) {
                Translations.get("sending")
            } else {
                Translations.get("add_recommendation")
            },
            onClick = controller::addRecommendation,
            enabled = state.canPost,
        ),
        modifier = Modifier.testTag("feed_post"),
    )
}

/** Selector del grupo destino: una tira horizontal de botones. */
@Composable
private fun GroupSelector(state: FeedState, controller: FeedController) {
    if (state.groups.isEmpty()) {
        Text(
            text = Translations.get("no_groups"),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            ),
            modifier = Modifier.testTag("feed_no_groups"),
        )
        return
    }

    Text(
        text = Translations.get("recommend_to_group"),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.large),
    ) {
        state.groups.forEach { group ->
            val selected = group.id == state.selectedGroupId
            ActionButton(
                data = ActionButtonData(
                    text = (if (selected) "* " else "") + group.name,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    onClick = { controller.selectGroup(group.id) },
                ),
                modifier = Modifier.testTag("feed_group_${group.id}"),
            )
        }
    }
}

/** Mensaje del último intento de publicación, si lo hay. */
@Composable
private fun FeedStatusMessage(message: String?, isError: Boolean, tag: String) {
    if (message == null) return
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
