package com.plyr.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerState
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.AddToPlaylistDialog
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.components.TrackMenuActions
import com.plyr.desktop.ui.components.TrackMenuButton
import com.plyr.desktop.ui.components.TrackMenuItem
import com.plyr.desktop.ui.components.itemsFor
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols

/**
 * Pantalla de cola de escritorio (M7).
 *
 * Equivalente a `com.plyr.ui.AudioListScreen` de Android, pero en vez de pagers
 * (que no encajan con ratón/teclado) usa un `Screen` explícito que se cambia
 * desde el menú de inicio. Desde M22 cada fila ofrece el menú `⋯` de acciones
 * (me gusta, añadir a cola/lista, compartir).
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
    playlists: List<PlaylistRecord> = emptyList(),
    onAddToPlaylist: ((PlayerTrack, String) -> Unit)? = null,
    menuActions: TrackMenuActions = TrackMenuActions(),
    modifier: Modifier = Modifier,
) {
    // Pista para la que se está eligiendo lista destino (M22); null = sin diálogo.
    var pendingAdd by remember { mutableStateOf<PlayerTrack?>(null) }
    val requestAddToPlaylist: ((PlayerTrack) -> Unit)? =
        if (onAddToPlaylist != null) { track -> pendingAdd = track } else null

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
            QueueList(state, onJump, onMove, menuActions, requestAddToPlaylist)
        }

        ActionButton(
            data = ActionButtonData(text = "\u2190", onClick = onBack),
            modifier = Modifier.testTag("back_from_${Screen.QUEUE.name}"),
        )
    }

    pendingAdd?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { playlistId ->
                onAddToPlaylist?.invoke(track, playlistId)
                pendingAdd = null
            },
            onDismiss = { pendingAdd = null },
        )
    }
}

/** Listado de la cola con, por fila, el menú de acciones y la reordenación. */
@Composable
private fun ColumnScope.QueueList(
    state: PlayerState,
    onJump: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    menuActions: TrackMenuActions,
    requestAddToPlaylist: ((PlayerTrack) -> Unit)?,
) {
    // `weight(1f)`: sin él la lista se lleva todo el alto que quede y el botón
    // `←` del final de la pantalla queda fuera de pantalla (M36). Por eso esta
    // función es extensión de `ColumnScope`.
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
                menuItems = menuActions.itemsFor(track, requestAddToPlaylist),
            )
        }
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
    menuItems: List<TrackMenuItem>,
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
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "${index + 1}. ${track.title}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(track.artist, track.duration).filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TrackMenuButton(items = menuItems, menuTag = "queue_menu_$index")

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
