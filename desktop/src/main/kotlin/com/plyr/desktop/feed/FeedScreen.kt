package com.plyr.desktop.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Subtitulo
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.model.Recommendation
import com.plyr.utils.formatTimestamp

/**
 * Feed de recomendaciones del escritorio (M12).
 *
 * Es el port de `FeedScreen` de Android sobre el [FeedController]: lista las
 * recomendaciones del grupo seleccionado (el general por defecto) y permite fijar
 * el apodo y publicar una nueva. A diferencia de Android, aquí no se extrae
 * metadatos del vídeo (eso vive en `MediaMetadataExtractor`, que todavía no se ha
 * portado): cada entrada se pinta con su comentario, su URL y su antigüedad.
 */
@Composable
fun FeedScreen(
    controller: FeedController,
    onBack: () -> Unit,
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
                        RecommendationRow(recommendation)
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

/** Una recomendación: comentario (si lo hay), URL y antigüedad + apodo. */
@Composable
private fun RecommendationRow(recommendation: Recommendation) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recommendation_${recommendation.id}"),
    ) {
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
            text = recommendation.url,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
