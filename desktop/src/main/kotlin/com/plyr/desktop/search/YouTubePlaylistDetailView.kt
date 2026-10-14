package com.plyr.desktop.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import com.plyr.database.TrackRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.playlists.PlaylistCover
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing

/**
 * Detalle de una lista de YouTube buscada (M19).
 *
 * Lista sus pistas, permite reproducirlas todas y guardarlas como lista local
 * (o quitarla si ya lo estaba). Es el port de `YouTubePlaylistDetailView` de
 * Android, pero recibe el estado ya resuelto ([YouTubePlaylistDetailState]) para
 * poder testearlo sin red.
 */
@Composable
fun YouTubePlaylistDetailView(
    state: YouTubePlaylistDetailState,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit,
    onToggleSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
            .testTag("youtube_playlist_detail"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaylistCover(
                url = state.thumbnailUrl,
                size = 64.dp,
                modifier = Modifier.testTag("youtube_playlist_cover"),
            )
            Spacer(Modifier.width(PlyrSpacing.small))
            Titulo(state.title.ifBlank { Translations.get("youtube_playlists") })
        }

        DetailActions(state = state, onPlayTracks = onPlayTracks, onToggleSave = onToggleSave)

        if (state.uploader.isNotBlank()) {
            Text(
                text = state.uploader,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }

        Spacer(Modifier.height(PlyrSpacing.small))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> DetailStatus(Translations.get("loading"), "youtube_playlist_loading")
                state.error != null -> DetailStatus(state.error, "youtube_playlist_error", isError = true)
                state.isEmpty -> DetailStatus(Translations.get("no_tracks_loaded"), "youtube_playlist_empty")
                else -> TrackList(state.tracks, onPlayTracks)
            }
        }

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_youtube_playlist"),
        )
    }
}

/** Botonera del detalle: reproducir todo y guardar/quitar la lista. */
@Composable
private fun DetailActions(
    state: YouTubePlaylistDetailState,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit,
    onToggleSave: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.small)) {
        ActionButton(
            data = ActionButtonData(
                text = "[ ${Translations.get("play_all")} ]",
                enabled = state.tracks.isNotEmpty(),
                onClick = { onPlayTracks(state.tracks, 0) },
            ),
            modifier = Modifier.testTag("youtube_playlist_play_all"),
        )
        ActionButton(
            data = ActionButtonData(
                text = if (state.isSaved) {
                    "[ ${Translations.get("saved")} ]"
                } else {
                    "[ ${Translations.get("save")} ]"
                },
                enabled = state.tracks.isNotEmpty() && !state.isSaving,
                onClick = onToggleSave,
            ),
            modifier = Modifier.testTag("youtube_playlist_save"),
        )
    }
}

/** Lista de pistas de la lista de YouTube. */
@Composable
private fun TrackList(
    tracks: List<TrackRecord>,
    onPlayTracks: (List<TrackRecord>, Int) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(tracks, key = { _, track -> track.remoteTrackId }) { index, track ->
            TrackRow(index = index, track = track, onClick = { onPlayTracks(tracks, index) })
        }
    }
}

/** Fila de una pista: número, título y artista; al pulsarla, reproduce desde ahí. */
@Composable
private fun TrackRow(index: Int, track: TrackRecord, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_playlist_track_$index")
            .clickable(onClick = onClick)
            .padding(vertical = PlyrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. ${track.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.artists.isNotBlank()) {
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Texto de estado del detalle (carga, error o vacío). */
@Composable
private fun DetailStatus(text: String, tag: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        ),
        modifier = Modifier.testTag(tag),
    )
}
