package com.plyr.desktop.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.database.TrackRecord
import com.plyr.desktop.ui.components.ReorderState

@Composable
fun PlaylistTrackItem(
    track: TrackRecord,
    index: Int,
    itemCount: Int = 0,
    isEditing: Boolean = false,
    reorderState: ReorderState? = null,
    onClick: () -> Unit = {},
    onRemove: () -> Unit = {},
    onMove: (Int, Int) -> Unit = { _, _ -> },
    onShare: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onDeleteDownload: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isEditing && reorderState != null) {
                    reorderState.itemModifier(track.remoteTrackId, index, itemCount, onMove)
                } else Modifier
            )
            .clickable(enabled = !isEditing) { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Text("${track.position + 1}. ", fontFamily = FontFamily.Monospace)
        Column(modifier = Modifier.weight(1f, fill = true)) {
            Text(track.name, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artists, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isEditing) {
            Text(
                text = "≡",
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(4.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onRemove) {
                Text(
                    text = "×",
                    fontFamily = FontFamily.Monospace,
                )
            }
        } else {
            // Indicador de descarga offline (M17): progreso mientras baja o `↓`
            // si ya está en el almacén, más la acción de borrar la descarga.
            TrackDownloadControls(track.remoteTrackId, isDownloaded, downloadProgress, onDeleteDownload)
            if (onShare != null) {
                TextButton(onClick = onShare, modifier = Modifier.testTag("track_share_${track.remoteTrackId}")) {
                    Text(
                        text = "⤴",
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/**
 * Cola derecha de una pista: `↓` (o `↓ NN%` mientras baja) y el botón `×` para
 * borrar su descarga. Se separa de [PlaylistTrackItem] para no alargar la fila.
 */
@Composable
private fun TrackDownloadControls(
    remoteTrackId: String,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onDeleteDownload: (() -> Unit)?,
) {
    if (downloadProgress != null) {
        Text(
            text = "↓ ${(downloadProgress * 100).toInt()}%",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.testTag("track_downloading_$remoteTrackId"),
        )
    } else if (isDownloaded) {
        Text(
            text = "↓",
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("track_downloaded_$remoteTrackId"),
        )
    }
    if (isDownloaded && onDeleteDownload != null) {
        TextButton(
            onClick = onDeleteDownload,
            modifier = Modifier.testTag("track_delete_download_$remoteTrackId"),
        ) {
            Text(
                text = "×",
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}