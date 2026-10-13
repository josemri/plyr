package com.plyr.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.database.TrackRecord
import com.plyr.desktop.ui.theme.PlyrSpacing

/**
 * Data class para unificar los datos de la canción en escritorio
 */
data class Song(
    val number: Int,
    val title: String,
    val artist: String,
    val remoteId: String? = null,
    val youtubeId: String? = null,
    val shareUrl: String? = null
)

@Composable
fun SongListItem(
    song: Song,
    index: Int,
    isCurrentlyPlaying: Boolean = false,
    isDraggable: Boolean = false,
    reorderState: ReorderState? = null,
    onClick: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    customButtonIcon: String? = null,
    customButtonAction: (() -> Unit)? = null,
) {
    val showMenu = remember { mutableStateOf(false) }
    
    val itemModifier = if (isDraggable && reorderState != null) {
        reorderState.itemModifier(song.remoteId ?: "$index", index, Int.MAX_VALUE) { _, _ -> }
    } else {
        Modifier
    }
    
    Row(
        modifier = itemModifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${song.number}.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            modifier = Modifier.width(24.dp),
        )
        Spacer(modifier = Modifier.width(PlyrSpacing.small))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else Color.Unspecified
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (customButtonIcon != null && customButtonAction != null) {
            TextButton(onClick = customButtonAction) {
                Text(
                    text = customButtonIcon,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }
        if (isDraggable) {
            Text(
                text = "≡",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
        Box {
            TextButton(onClick = { showMenu.value = true }) {
                Text(
                    text = "⋯",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
            DropdownMenu(
                expanded = showMenu.value,
                onDismissRequest = { showMenu.value = false }
            ) {
                onPlay?.let {
                    DropdownMenuItem(
                        text = { Text("Play", fontFamily = FontFamily.Monospace) },
                        onClick = {
                            showMenu.value = false
                            it()
                        }
                    )
                }
                onRemove?.let {
                    DropdownMenuItem(
                        text = { Text("Remove", fontFamily = FontFamily.Monospace) },
                        onClick = {
                            showMenu.value = false
                            it()
                        }
                    )
                }
            }
        }
    }
}

fun TrackRecord.toSong(): Song = Song(
    number = position + 1,
    title = name,
    artist = artists,
    remoteId = remoteTrackId,
    youtubeId = youtubeVideoId,
    shareUrl = null
)