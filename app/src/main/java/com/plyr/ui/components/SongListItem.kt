package com.plyr.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
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
    isSelected: Boolean = false,
    duration: String? = null
) {
    val haptic = LocalHapticFeedback.current
    var showPopup by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                playerViewModel?.let { viewModel ->
                    if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
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
        // Duración (opcional)
        if (duration != null) {
            Text(
                text = duration,
                style = PlyrTextStyles.trackArtist(),
                modifier = Modifier.padding(start = PlyrSpacing.small)
            )
        }
        // Action button (solo si no se pasa duration para mantener espacio compacto cuando hay duración al final?)
        if (duration == null) {
            IconButton(onClick = {
                showPopup = true
                }, modifier = Modifier.size(32.dp)) {
                Text(
                    text = "*", style = PlyrTextStyles.menuOption(),
                    color = Color(0xFF3FFFEF)
                )
            }
        }
    }

    if (showPopup) {
        Dialog(onDismissRequest = { showPopup = false }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF181818))
                    .padding(24.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val options = listOf(
                        "add to queue",
                        "add to playlist",
                        "copy link",
                        "fetch info",
                        "download",
                        "delete"
                    )
                    options.forEach { option ->
                        Text(
                            text = option,
                            color = Color(0xFF3FFFEF),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Log.d("SongListItemPopup", option)
                                    showPopup = false
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
