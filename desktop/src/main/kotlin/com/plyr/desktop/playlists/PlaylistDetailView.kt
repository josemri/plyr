package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.plyr.database.TrackRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.FileDialogs
import com.plyr.desktop.qr.QrShareDialog
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.ReorderState
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.utils.CoverStore
import com.plyr.ui.components.PlaylistShare
import com.plyr.ui.components.ShareType
import com.plyr.ui.components.ShareableItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage


@Composable
fun PlaylistDetailView(
    controller: PlaylistsController,
    state: PlaylistsState,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val playlist = state.selectedPlaylist
    val description = playlist?.description
    val reorderState = remember { ReorderState() }
    val scope = rememberCoroutineScope()
    var shareItem by remember { mutableStateOf<ShareableItem?>(null) }
    var cropSource by remember { mutableStateOf<BufferedImage?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        PlaylistCover(url = playlist?.imageUrl, size = 64.dp, modifier = Modifier.testTag("playlist_cover"))
        Spacer(modifier = Modifier.width(12.dp))
        Titulo(playlist?.name ?: Translations.get("plyr_lists"))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            data = ActionButtonData(text = Translations.get("play_all"), onClick = { if (state.tracks.isNotEmpty()) onPlayTracks(state.tracks, 0) }),
            modifier = Modifier.testTag("playlist_play_all")
        )
        if (playlist != null && PlaylistShare.isShareable(playlist.remoteId, playlist.description, playlist.source, playlist.sourceId)) {
            ActionButton(
                data = ActionButtonData(text = Translations.get("share"), onClick = { shareItem = playlist.toShareableItem() }),
                modifier = Modifier.testTag("playlist_share")
            )
        }
        if (!state.isEditing && playlist?.remoteId != "liked_songs") {
            ActionButton(
                data = ActionButtonData(text = Translations.get("edit"), onClick = { controller.enterEditMode() }),
                modifier = Modifier.testTag("playlist_edit")
            )
        }
        if (state.isEditing) {
            ActionButton(
                data = ActionButtonData(text = Translations.get("save"), onClick = { controller.saveEdits() }),
                modifier = Modifier.testTag("playlist_save")
            )
            ActionButton(
                data = ActionButtonData(text = Translations.get("cancel"), onClick = { controller.cancelEdit() }),
                modifier = Modifier.testTag("playlist_cancel_edit")
            )
            ActionButton(
                data = ActionButtonData(text = Translations.get("change_cover"), onClick = {
                    val file = FileDialogs.chooseImageToOpen(Translations.get("change_cover"))
                    if (file != null) {
                        scope.launch {
                            cropSource = withContext(Dispatchers.IO) { CoverStore.load(file) }
                        }
                    }
                }),
                modifier = Modifier.testTag("playlist_change_cover")
            )
        }
        if (state.isEditing && playlist?.remoteId != "liked_songs") {
            ActionButton(
                data = ActionButtonData(text = Translations.get("delete"), onClick = { controller.showDeleteDialog() }),
                modifier = Modifier.testTag("playlist_delete")
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (state.isEditing) {
        OutlinedTextField(
            value = state.newTitle,
            onValueChange = { controller.updateTitle(it) },
            label = { Text(Translations.get("playlist_name")) },
            modifier = Modifier.fillMaxWidth().testTag("playlist_edit_title")
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.newDescription,
            onValueChange = { controller.updateDescription(it) },
            label = { Text(Translations.get("description")) },
            modifier = Modifier.fillMaxWidth().testTag("playlist_edit_desc")
        )
        Spacer(modifier = Modifier.height(8.dp))
    } else if (!description.isNullOrBlank()) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(state.tracks, key = { _, track -> track.remoteTrackId }) { index, track ->
            PlaylistTrackItem(
                track = track,
                index = index,
                itemCount = state.tracks.size,
                isEditing = state.isEditing,
                reorderState = reorderState,
                onClick = { onPlayTracks(state.tracks, index) },
                onRemove = { controller.removeTrack(track.remoteTrackId) },
                onMove = { from, to -> controller.reorderTracks(from, to) },
                onShare = track.youtubeVideoId?.let { { shareItem = track.toShareableItem() } },
            )
        }
    }

    ActionButton(
        data = ActionButtonData(
            text = "\u2190 ${Translations.get("close")}",
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
            title = { Text(Translations.get("delete_playlist_title")) },
            text = { Text(String.format(Translations.get("delete_playlist_message"), playlist?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { controller.deletePlaylist(onBack) }) { Text(Translations.get("delete")) }
            },
            dismissButton = {
                TextButton(onClick = { controller.hideDeleteDialog() }) { Text(Translations.get("cancel")) }
            }
        )
    }

    if (state.showExitEditDialog) {
        AlertDialog(
            onDismissRequest = { controller.exitEdit() },
            title = { Text(Translations.get("unsaved_changes_title")) },
            text = { Text(Translations.get("unsaved_changes_message")) },
            confirmButton = {
                TextButton(onClick = { controller.confirmExitEdit() }) { Text(Translations.get("exit")) }
            },
            dismissButton = {
                TextButton(onClick = { controller.exitEdit() }) { Text(Translations.get("cancel")) }
            }
        )
    }

    cropSource?.let { source ->
        CoverCropDialog(
            source = source,
            onDismiss = { cropSource = null },
            onConfirm = { cropped ->
                val playlistId = playlist?.remoteId
                scope.launch {
                    if (playlistId != null) {
                        val url = withContext(Dispatchers.IO) { CoverStore.save(playlistId, cropped) }
                        if (url != null) controller.updateCover(url)
                    }
                    cropSource = null
                }
            },
        )
    }

    shareItem?.let { item ->
        QrShareDialog(item = item, onDismiss = { shareItem = null })
    }
}

/** Convierte la lista seleccionada en algo compartible por QR. */
private fun com.plyr.database.PlaylistRecord.toShareableItem(): ShareableItem = ShareableItem(
    remoteId = remoteId,
    shareUrl = null,
    youtubeId = PlaylistShare.sharedId(remoteId, sourceId),
    title = name,
    artist = "",
    type = ShareType.PLAYLIST,
    playlistOrigin = PlaylistShare.classify(remoteId, description, source, sourceId),
)

/** Convierte una pista en algo compartible por QR (solo si tiene vídeo). */
private fun TrackRecord.toShareableItem(): ShareableItem = ShareableItem(
    remoteId = remoteTrackId,
    shareUrl = null,
    youtubeId = youtubeVideoId,
    title = name,
    artist = artists,
    type = ShareType.TRACK,
)
