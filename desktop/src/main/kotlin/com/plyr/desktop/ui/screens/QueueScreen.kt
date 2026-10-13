package com.plyr.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerState
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols

/**
 * Pantalla de cola de escritorio (M7).
 *
 * Lista la cola del reproductor con la pista actual marcada; pulsar una fila
 * salta a ella y los símbolos `^`/`v` reordenan. Es el equivalente de
 * `com.plyr.ui.QueueScreen`, pero con reordenación por botones en vez de arrastre
 * (el drag & drop "de verdad" llega con las listas en M8).
 *
 * Como [SearchScreen], recibe estado y callbacks de fuera para poder testearse
 * sin motor de audio.
 */
@Composable
fun QueueScreen(
    state: PlayerState,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
            .testTag("screen_${Screen.QUEUE.name}"),
    ) {
        Titulo(Translations.get("plyr_queue"))

        if (state.queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = Translations.get("no_tracks_loaded"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.testTag("queue_empty"),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(state.queue, key = { index, track -> "${track.id}#$index" }) { index, track ->
                    QueueRow(
                        index = index,
                        track = track,
                        isCurrent = index == state.currentIndex,
                        onJump = onJump,
                        onMove = onMove,
                        isFirst = index == 0,
                        isLast = index == state.queue.lastIndex,
                    )
                }
            }
        }

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_${Screen.QUEUE.name}"),
        )
    }
}

@Composable
private fun QueueRow(
    index: Int,
    track: PlayerTrack,
    isCurrent: Boolean,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    isFirst: Boolean,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("queue_item_$index")
            .clickable { onJump(index) }
            .padding(vertical = PlyrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isCurrent) PlyrSymbols.BULLET else " ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            ),
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = PlyrSpacing.small)) {
            Text(
                text = "${index + 1}. ${track.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        MoveButton("^", enabled = !isFirst, tag = "queue_up_$index", onClick = { onMove(index, index - 1) })
        MoveButton("v", enabled = !isLast, tag = "queue_down_$index", onClick = { onMove(index, index + 1) })
    }
}

@Composable
private fun MoveButton(symbol: String, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text = symbol,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        ),
        modifier = Modifier
            .testTag(tag)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = PlyrSpacing.small),
    )
}
