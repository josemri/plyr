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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import com.plyr.database.TrackEntity
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.Config
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.plyr.ui.theme.PlyrSpacing
import com.plyr.ui.theme.PlyrTextStyles

// Data class para unificar los datos de la canción
data class Song(
    val number: Int,
    val title: String,
    val artist: String,
    val remoteId: String? = null,
    val youtubeId: String? = null,
    val shareUrl: String? = null
)

// Helper function para obtener icono y color según la acción
@Composable
private fun getSwipeIconAndColor(action: String): Pair<String, Color> {
    return when (action) {
        Config.SWIPE_ACTION_ADD_TO_QUEUE -> "+" to MaterialTheme.colorScheme.primary
        Config.SWIPE_ACTION_ADD_TO_LIKED -> "♥" to MaterialTheme.colorScheme.error
        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> "≡" to MaterialTheme.colorScheme.tertiary
        Config.SWIPE_ACTION_SHARE -> "⤴" to MaterialTheme.colorScheme.secondary
        else -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun SongListItem(
    song: Song,
    trackEntities: List<TrackEntity>,
    index: Int,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isCurrentlyPlaying: Boolean = false, // Indica si esta canción está sonando actualmente
    onLikedStatusChanged: (() -> Unit)? = null,
    customButtonIcon: String? = null, // Nueva: Icono personalizado para el botón (ej: "+")
    customButtonAction: (() -> Unit)? = null // Nueva: Acción personalizada para el botón
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showPopup by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    // Obtener las acciones configuradas y sus iconos/colores
    val swipeRightAction = Config.getSwipeRightAction(context)
    val swipeLeftAction = Config.getSwipeLeftAction(context)
    val (rightIcon, rightColor) = getSwipeIconAndColor(swipeRightAction)
    val (leftIcon, leftColor) = getSwipeIconAndColor(swipeLeftAction)

    // Swipe gesture state
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 30.dp.toPx() } // Umbral muy bajo, solo para detectar intención

    // Reset swipe position
    fun resetSwipe() {
        coroutineScope.launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        // Background actions - Right swipe (like/favorite)
        if (offsetX.value > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { offsetX.value.toDp() })
                    .background(Color.Transparent),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = rightIcon,
                    color = rightColor,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        // Background actions - Left swipe (add to queue)
        if (offsetX.value < 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (-offsetX.value).toDp() })
                    .align(Alignment.CenterEnd)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = leftIcon,
                    color = leftColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Main content (draggable)
        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    offsetX.value > swipeThreshold -> {
                                        val action = Config.getSwipeRightAction(context)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        executeSwipeAction(
                                            action = action,
                                            song = song,
                                            context = context,
                                            playerViewModel = playerViewModel,
                                            trackEntities = trackEntities,
                                            index = index,
                                            coroutineScope = coroutineScope,
                                            onLikedStatusChanged = onLikedStatusChanged,
                                            onShowPlaylistDialog = {},
                                            onShowShareDialog = { showShareDialog = true }
                                        )
                                        resetSwipe()
                                    }
                                    offsetX.value < -swipeThreshold -> {
                                        val action = Config.getSwipeLeftAction(context)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        executeSwipeAction(
                                            action = action,
                                            song = song,
                                            context = context,
                                            playerViewModel = playerViewModel,
                                            trackEntities = trackEntities,
                                            index = index,
                                            coroutineScope = coroutineScope,
                                            onLikedStatusChanged = onLikedStatusChanged,
                                            onShowPlaylistDialog = {},
                                            onShowShareDialog = { showShareDialog = true }
                                        )
                                        resetSwipe()
                                    }
                                    else -> {
                                        // Return to center
                                        resetSwipe()
                                    }
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newValue = (offsetX.value + dragAmount).coerceIn(-200f, 150f)
                                offsetX.snapTo(newValue)
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetX.value.absoluteValue < 10f) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playerViewModel?.let { viewModel ->
                            if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                viewModel.clearPlayerState()

                                viewModel.setCurrentPlaylist(trackEntities, index)
                                val selectedTrackEntity = trackEntities[index]
                                coroutineScope.launch {
                                    try {
                                        viewModel.loadAudioFromTrack(selectedTrackEntity)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    } else {
                        resetSwipe()
                    }
                }
                .fillMaxWidth()
                .height(32.dp)
                .background(Color.Transparent),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number
            Text(
                text = song.number.toString(),
                style = PlyrTextStyles.trackArtist(),
                modifier = Modifier.padding(end = PlyrSpacing.small, start = 8.dp)
            )
            // Song title and artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = when {
                        isCurrentlyPlaying -> PlyrTextStyles.trackTitle().copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        isSelected -> PlyrTextStyles.selectableOption(true)
                        else -> PlyrTextStyles.trackTitle()
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = if (isCurrentlyPlaying)
                        PlyrTextStyles.trackArtist().copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    else
                        PlyrTextStyles.trackArtist(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 0.dp)
                )
            }
            // Botón personalizable
            IconButton(onClick = {
                if (customButtonAction != null) {
                    customButtonAction()
                } else {
                    showPopup = true
                }
            }, modifier = Modifier.size(32.dp)) {
                Text(
                    text = customButtonIcon ?: "*",
                    style = PlyrTextStyles.menuOption(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Solo mostrar popup si no hay acción personalizada
    if (showPopup && customButtonAction == null) {
        Dialog(onDismissRequest = {
            showPopup = false
        }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Song info
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    )

                    // Action buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Add to Queue
                        Text(
                            text = Translations.get(context, "add_to_queue"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPopup = false
                                    playerViewModel?.let { viewModel ->
                                        if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                            val trackToAdd = trackEntities[index]
                                            viewModel.addToQueue(trackToAdd)
                                            Log.d("SongListItem", "✓ Track added to queue: ${trackToAdd.name}")
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Share
                        Text(
                            text = Translations.get(context, "share"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareDialog = true
                                    showPopup = false
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    if (showShareDialog) {
        ShareDialog(
            item = ShareableItem(
                remoteId = song.remoteId,
                shareUrl = song.shareUrl,
                youtubeId = song.youtubeId,
                title = song.title,
                artist = song.artist,
                type = ShareType.TRACK
            ),
            onDismiss = { showShareDialog = false }
        )
    }
}

fun executeSwipeAction(
    action: String,
    song: Song,
    context: android.content.Context,
    playerViewModel: PlayerViewModel?,
    trackEntities: List<TrackEntity>,
    index: Int,
    coroutineScope: CoroutineScope,
    onLikedStatusChanged: (() -> Unit)?,
    onShowPlaylistDialog: () -> Unit,
    onShowShareDialog: () -> Unit
) {
    when (action) {
        Config.SWIPE_ACTION_ADD_TO_LIKED -> {
            Log.d("SongListItem", "Add to liked (no-op): ${song.title}")
            onLikedStatusChanged?.invoke()
        }
        Config.SWIPE_ACTION_ADD_TO_QUEUE -> {
            // Añadir a cola
            playerViewModel?.let { viewModel ->
                if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                    val trackToAdd = trackEntities[index]
                    viewModel.addToQueue(trackToAdd)
                    Log.d("SongListItem", "✓ Track added to queue: ${trackToAdd.name}")
                } else {
                    Log.e("SongListItem", "✗ Invalid index or empty trackEntities")
                }
            } ?: Log.e("SongListItem", "✗ PlayerViewModel is null")
        }
        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> {
            Log.d("SongListItem", "Add to playlist (no-op): ${song.title}")
        }
        Config.SWIPE_ACTION_SHARE -> {
            // Compartir
            onShowShareDialog()
        }
        else -> {
            Log.w("SongListItem", "Acción desconocida para swipe: $action")
        }
    }
}

// Helper para validar si el remoteId es válido (no un placeholder generado localmente)
private fun isValidRemoteId(sId: String?): Boolean {
    return sId != null && sId.isNotBlank() && !sId.startsWith("recommended_") && !sId.startsWith("temp_")
}
