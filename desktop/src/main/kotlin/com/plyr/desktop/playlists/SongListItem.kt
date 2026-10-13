package com.plyr.desktop.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
        } else if (onShare != null) {
            TextButton(onClick = onShare, modifier = Modifier.testTag("track_share_${track.remoteTrackId}")) {
                Text(
                    text = "⤴",
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}