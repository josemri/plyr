package com.plyr.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.database.TrackEntity
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.plyr.ui.theme.PlyrSpacing
import com.plyr.ui.theme.PlyrTextStyles

// Data class para unificar los datos de la canción
data class Song(
    val number: Int,
    val title: String,
    val artist: String
)

@Composable
fun SongListItem(
    song: Song,
    trackEntities: List<TrackEntity>,
    index: Int,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                playerViewModel?.let { viewModel ->
                    viewModel.setCurrentPlaylist(trackEntities, index)
                    val selectedTrackEntity = trackEntities[index]
                    coroutineScope.launch {
                        try {
                            viewModel.loadAudioFromTrack(selectedTrackEntity)
                            Log.d("SongListItem", "🎵 Reproduciendo track ${index + 1}/${trackEntities.size}: ${selectedTrackEntity.name}")
                        } catch (e: Exception) {
                            Log.e("SongListItem", "Error al reproducir track", e)
                        }
                    }
                }
            }
            .fillMaxWidth()
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Text(
            text = song.number.toString(),
            style = PlyrTextStyles.trackArtist(),
            modifier = Modifier.padding(end = PlyrSpacing.small)
        )
        // Song title and artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = if (isSelected)
                    PlyrTextStyles.selectableOption(true)
                else
                    PlyrTextStyles.trackTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = PlyrTextStyles.trackArtist(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.dp)
            )
        }
        // Action button ("*")
        IconButton(onClick = {
            Log.d("SongListItem", "Track options clicked for: ${song.title}")
        }, modifier = Modifier.size(32.dp)) {
            Text(text = "*", style = PlyrTextStyles.menuOption())
        }
    }
}
