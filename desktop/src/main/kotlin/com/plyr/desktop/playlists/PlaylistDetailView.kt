package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.database.PlaylistRecord
import com.plyr.database.TrackRecord
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadStatusBar
import com.plyr.desktop.download.OfflineStorageDialog
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.FileDialogs
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.qr.QrShareDialog
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.AddToPlaylistDialog
import com.plyr.desktop.ui.components.ReorderState
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.components.TrackMenuItem
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
    downloadController: DownloadController,
    state: PlaylistsState,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit,
    onBack: () -> Unit,
    isRandomizing: Boolean = false,
    onPlayRandom: (List<TrackRecord>) -> Unit = {},
    onStopRandomize: () -> Unit = {},
    playlists: List<PlaylistRecord> = emptyList(),
    searchTracks: suspend (String) -> List<PlayerTrack> = { emptyList() },
    onAddToQueue: (TrackRecord) -> Unit = {},
) {
    val playlist = state.selectedPlaylist
    val description = playlist?.description
    val reorderState = remember { ReorderState() }
    val scope = rememberCoroutineScope()
    var shareItem by remember { mutableStateOf<ShareableItem?>(null) }
    var cropSource by remember { mutableStateOf<BufferedImage?>(null) }
    var showStorageDialog by remember { mutableStateOf(false) }
    // Pista para la que se elige lista destino y buscador de pistas (M22).
    var pendingAdd by remember { mutableStateOf<TrackRecord?>(null) }
    val trackSearch = remember(searchTracks) { TrackSearchController(scope, searchTracks) }
    val trackSearchState by trackSearch.state.collectAsState()

    // Estado de descarga offline de la lista abierta (M17): qué pistas ya están
    // en el almacén, si se está bajando esta lista y el progreso de la pista
    // actual. `revision` sube al bajar/borrar y fuerza a recalcular el conjunto.
    val downloadState by downloadController.state.collectAsState()
    val selectedPlaylistId = state.selectedPlaylistId
    val videoIds = remember(state.tracks) {
        state.tracks.mapNotNull { it.youtubeVideoId }.filter { it.isNotBlank() }.toSet()
    }
    val downloadedIds = remember(videoIds, downloadState.revision) {
        downloadController.downloadedIds(videoIds)
    }
    val allDownloaded = videoIds.isNotEmpty() && downloadedIds.containsAll(videoIds)
    val downloadingThis = downloadState.isDownloading && downloadState.playlistId == selectedPlaylistId

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
        // Reproducción aleatoria (M20): `<rnd>` baraja y arranca; mientras dura,
        // el mismo botón pasa a `<stop>` (como el `playbackRandomButton` de
        // Android). Solo en modo normal, no editando la lista.
        if (!state.isEditing) {
            ActionButton(
                data = ActionButtonData(
                    text = if (isRandomizing) "<stop>" else "<rnd>",
                    color = if (isRandomizing) MaterialTheme.colorScheme.error else Color.Unspecified,
                    onClick = { if (isRandomizing) onStopRandomize() else onPlayRandom(state.tracks) },
                ),
                modifier = Modifier.testTag("playlist_random")
            )
        }
        if (playlist != null && PlaylistShare.isShareable(playlist.remoteId, playlist.description, playlist.source, playlist.sourceId)) {
            ActionButton(
                data = ActionButtonData(text = Translations.get("share"), onClick = { shareItem = playlist.toShareableItem() }),
                modifier = Modifier.testTag("playlist_share")
            )
        }
        // Botón de descarga offline (M17): baja lo que falte, cancela si está en
        // curso, o abre el diálogo de almacén cuando ya está todo en disco. No
        // aparece si la lista no tiene ninguna pista con vídeo descargable.
        if (!state.isEditing && selectedPlaylistId != null && videoIds.isNotEmpty()) {
            ActionButton(
                data = ActionButtonData(
                    text = when {
                        downloadingThis -> Translations.get("cancel")
                        allDownloaded -> Translations.get("download_clean")
                        else -> Translations.get("download")
                    },
                    color = if (downloadingThis) MaterialTheme.colorScheme.error else Color.Unspecified,
                    onClick = {
                        when {
                            downloadingThis -> downloadController.cancel()
                            allDownloaded -> showStorageDialog = true
                            else -> downloadController.startDownload(
                                playlistId = selectedPlaylistId,
                                playlistTitle = playlist?.name.orEmpty(),
                                tracks = state.tracks,
                            )
                        }
                    },
                ),
                modifier = Modifier.testTag("playlist_download")
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

    // Barra de descarga de la lista (M17): progreso/resultado de esta playlist.
    DownloadStatusBar(
        state = downloadState,
        controller = downloadController,
        playlistId = selectedPlaylistId,
    )

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

        // Buscador de YouTube para añadir pistas al vuelo (M22, F26).
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = trackSearchState.query,
                onValueChange = trackSearch::onQueryChange,
                label = { Text(Translations.get("search_tracks_label")) },
                modifier = Modifier.weight(1f).testTag("playlist_add_search"),
            )
            Spacer(modifier = Modifier.width(8.dp))
            ActionButton(
                data = ActionButtonData(
                    text = Translations.get("search_title"),
                    enabled = trackSearchState.query.isNotBlank() && !trackSearchState.isSearching,
                    onClick = { trackSearch.performSearch() },
                ),
                modifier = Modifier.testTag("playlist_add_search_button"),
            )
        }
        if (trackSearchState.isSearching) {
            Text(
                text = Translations.get("search_loading"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("playlist_add_searching"),
            )
        }
        if (trackSearchState.error != null) {
            Text(
                text = trackSearchState.error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error,
            )
        }
        trackSearchState.results.forEachIndexed { index, result ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = listOf(result.title, result.artist).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { controller.addTrack(result.toTrackRecord(selectedPlaylistId.orEmpty())) },
                    modifier = Modifier.testTag("playlist_add_result_$index"),
                ) {
                    Text("+", fontFamily = FontFamily.Monospace)
                }
            }
        }
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
                menuItems = buildList {
                    add(
                        TrackMenuItem(
                            label = Translations.get(
                                if (controller.isLiked(track.youtubeVideoId, track.name, track.artists)) "liked" else "like"
                            ),
                            tag = "track_like",
                            onClick = { controller.toggleLike(track.youtubeVideoId, track.name, track.artists, track.remoteTrackId) },
                        )
                    )
                    add(TrackMenuItem(Translations.get("add_to_queue"), "track_add_queue") { onAddToQueue(track) })
                    add(TrackMenuItem(Translations.get("add_to_playlist"), "track_add_playlist") { pendingAdd = track })
                    if (track.youtubeVideoId != null) {
                        add(TrackMenuItem(Translations.get("share"), "track_share") { shareItem = track.toShareableItem() })
                    }
                },
                isDownloaded = track.youtubeVideoId != null && track.youtubeVideoId in downloadedIds,
                downloadProgress = if (downloadingThis && track.youtubeVideoId == downloadState.currentVideoId) {
                    downloadState.currentFraction
                } else {
                    null
                },
                onDeleteDownload = track.youtubeVideoId?.let { videoId ->
                    { downloadController.removeDownload(videoId) }
                },
            )
        }
    }

    ActionButton(
        data = ActionButtonData(
            text = "< ${Translations.get("back")}",
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

    if (showStorageDialog) {
        OfflineStorageDialog(
            controller = downloadController,
            onDismiss = { showStorageDialog = false },
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

    pendingAdd?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { playlistId ->
                controller.addTrackToPlaylist(playlistId, track)
                pendingAdd = null
            },
            onDismiss = { pendingAdd = null },
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
