package com.plyr.desktop.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.database.TrackRecord
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadStatusBar
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import kotlinx.coroutines.delay

/** Cuánto se deja a la vista el resultado de una descarga antes de descartarlo. */
private const val DOWNLOAD_RESULT_MS = 3000L

/** Lado de la portada de cada lista en la rejilla adaptativa (M30), como Android. */
private val PLAYLIST_GRID_COVER = 150.dp

@Composable
fun PlaylistsScreen(
    controller: PlaylistsController,
    downloadController: DownloadController,
    onBack: () -> Unit,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit = { _, _ -> },
    isRandomizing: Boolean = false,
    onPlayRandom: (List<TrackRecord>) -> Unit = {},
    onStopRandomize: () -> Unit = {},
    searchTracks: suspend (String) -> List<PlayerTrack> = { emptyList() },
    onAddToQueue: (TrackRecord) -> Unit = {},
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
        // Cada rama pinta su propia cabecera: la rejilla con su `+`, el detalle
        // con el nombre de la lista y el alta con su título (M30).
        when {
            state.showCreate -> CreatePlaylistView(controller, searchTracks)
            state.selectedPlaylistId != null ->
                PlaylistDetailView(
                    controller = controller,
                    downloadController = downloadController,
                    state = state,
                    onPlayTracks = onPlayTracks,
                    onBack = onBack,
                    isRandomizing = isRandomizing,
                    onPlayRandom = onPlayRandom,
                    onStopRandomize = onStopRandomize,
                    playlists = state.playlists,
                    searchTracks = searchTracks,
                    onAddToQueue = onAddToQueue,
                )
            else -> PlaylistGridView(controller, downloadController, state, onBack)
        }
    }
}

@Composable
private fun ColumnScope.PlaylistGridView(
    controller: PlaylistsController,
    downloadController: DownloadController,
    state: PlaylistsState,
    onBack: () -> Unit,
) {
    // Barra de descarga a nivel global (M17): aunque estés en el listado, se ve
    // qué lista se está bajando y se puede cancelar.
    val downloadState by downloadController.state.collectAsState()

    Titulo(
        titulo = Translations.get("plyr_lists"),
        trailing = {
            ActionButton(
                data = ActionButtonData(text = "+", onClick = { controller.showCreate() }),
                fontSize = 24.sp,
                modifier = Modifier.testTag("playlists_create"),
            )
        },
    )

    DownloadStatusBar(state = downloadState, controller = downloadController)

    if (state.playlists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = Translations.get("no_playlists"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    } else {
        // Rejilla adaptativa (M30): las mismas piezas que Android
        // (`GridCells.Adaptive` de 150 dp) en vez del listado anterior.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PLAYLIST_GRID_COVER),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = PlyrSpacing.large),
            verticalArrangement = Arrangement.spacedBy(PlyrSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.large),
        ) {
            items(state.playlists, key = { it.remoteId }) { playlist ->
                PlaylistGridItem(
                    playlist = playlist,
                    onClick = { controller.selectPlaylist(playlist.remoteId) },
                )
            }
        }
    }

    // Volver a la Home (el escritorio no tiene back del sistema): `< back`.
    ActionButton(
        data = ActionButtonData(
            text = "< ${Translations.get("back")}",
            onClick = onBack,
        ),
        modifier = Modifier.testTag("back_from_${Screen.PLAYLISTS.name}"),
    )
}

/** Portada y nombre de una lista en la rejilla (M30), como el grid de Android. */
@Composable
private fun PlaylistGridItem(playlist: PlaylistRecord, onClick: () -> Unit) {
    val isLiked = playlist.remoteId == LikedSongs.ID
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("playlist_item_${playlist.remoteId}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLiked) {
            Box(
                modifier = Modifier
                    .size(PLAYLIST_GRID_COVER)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "♥", fontSize = 48.sp, color = MaterialTheme.colorScheme.error)
            }
        } else {
            PlaylistCover(
                url = playlist.imageUrl,
                size = PLAYLIST_GRID_COVER,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)),
            )
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier.padding(top = PlyrSpacing.small),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = String.format(Translations.get("tracks_count"), playlist.trackCount),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.padding(top = PlyrSpacing.xxs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
