package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.database.TrackRecord
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadStatusBar
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import kotlinx.coroutines.delay

/** Cuánto se deja a la vista el resultado de una descarga antes de descartarlo. */
private const val DOWNLOAD_RESULT_MS = 3000L

@Composable
fun PlaylistsScreen(
    controller: PlaylistsController,
    downloadController: DownloadController,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val downloadState by downloadController.state.collectAsState()

    // El resultado ("descargado 3/3") se muestra unos segundos y se olvida, como
    // en Android; mientras tanto la barra lo pinta desde el estado.
    LaunchedEffect(downloadState.outcome) {
        if (downloadState.outcome != null) {
            delay(DOWNLOAD_RESULT_MS)
            downloadController.dismissResult()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(PlyrSpacing.large).testTag("screen_${Screen.PLAYLISTS.name}")
    ) {
        Titulo(Translations.get("plyr_lists"))
        Spacer(modifier = Modifier.height(PlyrSpacing.small))
        when {
            state.showCreate -> CreatePlaylistView(controller)
            state.selectedPlaylistId != null ->
                PlaylistDetailView(controller, downloadController, state, onPlayTracks, onBack)
            else -> PlaylistGridView(controller, downloadController, state, onBack)
        }
    }
}

@Composable
private fun PlaylistGridView(
    controller: PlaylistsController,
    downloadController: DownloadController,
    state: PlaylistsState,
    onBack: () -> Unit,
) {
    // Barra de descarga a nivel global (M17): aunque estés en el listado, se ve
    // qué lista se está bajando y se puede cancelar.
    val downloadState by downloadController.state.collectAsState()

    ActionButton(
        data = ActionButtonData(
            text = "+ ${Translations.get("create_playlist")}",
            onClick = { controller.showCreate() },
        ),
        modifier = Modifier.testTag("playlists_create")
    )
    Spacer(modifier = Modifier.height(PlyrSpacing.medium))

    DownloadStatusBar(state = downloadState, controller = downloadController)

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(state.playlists) { _, playlist ->
            PlaylistItem(playlist = playlist, onClick = { controller.selectPlaylist(playlist.remoteId) })
        }
    }

    ActionButton(
        data = ActionButtonData(
            text = "\u2190 ${Translations.get("close")}",
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
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                PlaylistCover(url = playlist.imageUrl, size = 60.dp)
                if (isLiked) {
                    Text(
                        text = "♥",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = String.format(Translations.get("tracks_count"), playlist.trackCount),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                    ),
                )
            }
        }
    }
}
