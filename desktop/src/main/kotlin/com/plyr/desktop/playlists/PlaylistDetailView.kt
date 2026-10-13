package com.plyr.desktop.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.database.TrackRecord
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.ReorderState
import com.plyr.desktop.ui.components.Titulo

@Composable
fun PlaylistDetailView(
    controller: PlaylistsController,
    state: PlaylistsState,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val playlist = state.selectedPlaylist
    val reorderState = remember { ReorderState() }

    Titulo(playlist?.name ?: "Playlist")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            data = ActionButtonData(text = "PLAY ALL", onClick = { if (state.tracks.isNotEmpty()) onPlayTracks(state.tracks, 0) }),
            modifier = Modifier.testTag("playlist_play_all")
        )
        if (!state.isEditing && playlist?.remoteId != "liked_songs") {
            ActionButton(
                data = ActionButtonData(text = "EDIT", onClick = { controller.enterEditMode() }),
                modifier = Modifier.testTag("playlist_edit")
            )
        }
        if (state.isEditing) {
            ActionButton(
                data = ActionButtonData(text = "SAVE", onClick = { controller.saveEdits() }),
                modifier = Modifier.testTag("playlist_save")
            )
            ActionButton(
                data = ActionButtonData(text = "CANCEL", onClick = { controller.cancelEdit() }),
                modifier = Modifier.testTag("playlist_cancel_edit")
            )
        }
        if (state.isEditing && playlist?.remoteId != "liked_songs") {
            ActionButton(
                data = ActionButtonData(text = "DELETE", onClick = { controller.showDeleteDialog() }),
                modifier = Modifier.testTag("playlist_delete")
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (state.isEditing) {
        OutlinedTextField(
            value = state.newTitle,
            onValueChange = { controller.updateTitle(it) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth().testTag("playlist_edit_title")
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newDescription,
            onValueChange = { controller.updateDescription(it) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().testTag("playlist_edit_desc")
        )
        Spacer(modifier = Modifier.height(8.dp))
    } else if (!playlist?.description.isNullOrBlank()) {
        Text(
            text = playlist!!.description!!,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(state.tracks, key = { index, track -> track.remoteTrackId }) { index, track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (state.isEditing) reorderState.itemModifier(track.remoteTrackId, index, state.tracks.size) { from, to -> controller.reorderTracks(from, to) } else Modifier)
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !state.isEditing) { onPlayTracks(state.tracks, index) }
                    .testTag("track_item_$index")
            ) {
                Text("${track.position + 1}. ", fontFamily = FontFamily.Monospace)
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.name, fontFamily = FontFamily.Monospace)
                    Text(track.artists, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                if (state.isEditing) {
                    Text(
                        text = "≡",
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(4.dp).testTag("track_drag_$index")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "×",
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .padding(4.dp)
                            .clickable { controller.removeTrack(track.remoteTrackId) }
                            .testTag("track_remove_$index")
                    )
                }
            }
        }
    }

    ActionButton(
        data = ActionButtonData(
            text = "\u2190 Back",
            onClick = {
                if (state.isEditing && state.hasUnsavedChanges) {
                    controller.cancelEdit()
                } else {
                    onBack()
                }
            }
        ),
        modifier = Modifier.testTag("back_from_playlist_detail")
    )

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { controller.hideDeleteDialog() },
            title = { Text("Delete playlist") },
            text = { Text("Delete ${playlist?.name}?") },
            confirmButton = {
                TextButton(onClick = { controller.deletePlaylist(onBack) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { controller.hideDeleteDialog() }) { Text("Cancel") }
            }
        )
    }

    if (state.showExitEditDialog) {
        AlertDialog(
            onDismissRequest = { controller.exitEdit() },
            title = { Text("Unsaved changes") },
            text = { Text("Discard changes?") },
            confirmButton = {
                TextButton(onClick = { controller.confirmExitEdit() }) { Text("Exit") }
            },
            dismissButton = {
                TextButton(onClick = { controller.exitEdit() }) { Text("Cancel") }
            }
        )
    }
}
