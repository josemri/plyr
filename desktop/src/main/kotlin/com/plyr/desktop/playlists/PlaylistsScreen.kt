package com.plyr.desktop.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.database.TrackRecord
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing

@Composable
fun PlaylistsScreen(
    controller: PlaylistsController,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    Column(
        modifier = modifier.fillMaxSize().padding(PlyrSpacing.large).testTag("screen_${Screen.PLAYLISTS.name}")
    ) {
        Titulo(com.plyr.desktop.i18n.Translations.get("plyr_lists"))
        Spacer(modifier = Modifier.height(PlyrSpacing.small))
        when {
            state.showCreate -> CreatePlaylistView(controller, onBack)
            state.selectedPlaylistId != null -> PlaylistDetailView(controller, state, onPlayTracks, onBack)
            else -> PlaylistGridView(controller, state, onBack)
        }
    }
}

@Composable
private fun PlaylistGridView(
    controller: PlaylistsController,
    state: PlaylistsState,
    onBack: () -> Unit,
) {
    ActionButton(
        data = ActionButtonData(
            text = "+ ${com.plyr.desktop.i18n.Translations.get("create_playlist")}",
            onClick = { controller.showCreate() },
        ),
        modifier = Modifier.testTag("playlists_create")
    )
    Spacer(modifier = Modifier.height(PlyrSpacing.medium))

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.playlists) { _, playlist ->
            PlaylistItem(playlist = playlist, onClick = { controller.selectPlaylist(playlist.remoteId) })
        }
    }

    ActionButton(
        data = ActionButtonData(
            text = "\u2190 ${com.plyr.desktop.i18n.Translations.get("close")}",
            onClick = onBack,
        ),
        modifier = Modifier.testTag("back_from_${Screen.PLAYLISTS.name}")
    )
}

@Composable
private fun PlaylistItem(playlist: PlaylistRecord, onClick: () -> Unit) {
    val isLiked = playlist.remoteId == LikedSongs.ID
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("playlist_item_${playlist.remoteId}"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (isLiked) {
                    Text(
                        text = "♥",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(weight = 1f, fill = true)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                    ),
                )
            }
        }
    }
}
