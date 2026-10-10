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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import com.plyr.database.PlaylistEntity
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.Config
import com.plyr.utils.SwipeConfig
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

// Iconos/colores resueltos para los fondos de swipe (izquierda/derecha)
private data class SwipeVisuals(
    val rightIcon: String,
    val rightColor: Color,
    val leftIcon: String,
    val leftColor: Color
)

// Estado y callbacks que necesitan tanto el gesto de swipe como los diálogos
private data class SongActionContext(
    val song: Song,
    val context: android.content.Context,
    val playerViewModel: PlayerViewModel?,
    val trackEntities: List<TrackEntity>,
    val index: Int,
    val coroutineScope: CoroutineScope,
    val onLikedStatusChanged: (() -> Unit)?,
    val onShowPlaylistDialog: () -> Unit,
    val onShowShareDialog: () -> Unit
)

// Estado observable del desplazamiento de swipe y su animación de retorno
private class SongSwipeState(
    val dragOffset: MutableFloatState,
    val settleJob: MutableState<Job?>,
    val threshold: Float,
    private val coroutineScope: CoroutineScope
) {
    // Reset swipe position (un solo job: un drag nuevo lo cancela)
    fun reset() {
        settleJob.value?.cancel()
        settleJob.value = coroutineScope.launch {
            val start = dragOffset.floatValue
            if (start != 0f) {
                Animatable(start).animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300)
                ) { dragOffset.floatValue = value }
            }
        }
    }

    fun cancelSettle() {
        settleJob.value?.cancel()
    }

    fun dragBy(amount: Float) {
        dragOffset.floatValue = (dragOffset.floatValue + amount).coerceIn(-200f, 150f)
    }
}

@Composable
private fun rememberSongSwipeState(coroutineScope: CoroutineScope): SongSwipeState {
    // Swipe gesture state
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val threshold = with(density) { 30.dp.toPx() } // Umbral muy bajo, solo para detectar intención
    return SongSwipeState(dragOffset, settleJob, threshold, coroutineScope)
}

@Composable
private fun rememberSwipeVisuals(context: android.content.Context): SwipeVisuals {
    // Obtener las acciones configuradas y sus iconos/colores
    val swipeRightAction = SwipeConfig.getSwipeRightAction(context)
    val swipeLeftAction = SwipeConfig.getSwipeLeftAction(context)
    val (rightIcon, rightColor) = getSwipeIconAndColor(swipeRightAction)
    val (leftIcon, leftColor) = getSwipeIconAndColor(swipeLeftAction)
    return SwipeVisuals(rightIcon, rightColor, leftIcon, leftColor)
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
    customButtonAction: (() -> Unit)? = null, // Nueva: Acción personalizada para el botón
    isDownloaded: Boolean = false, // El audio de esta pista está en el almacén offline
    downloadProgress: Float? = null, // 0..1 si esta pista se está bajando ahora (null si no)
    onDeleteDownload: (() -> Unit)? = null // Borrar el audio offline de esta pista
) {
    SongListItemContent(
        song = song,
        trackEntities = trackEntities,
        index = index,
        playerViewModel = playerViewModel,
        coroutineScope = coroutineScope,
        modifier = modifier,
        isSelected = isSelected,
        isCurrentlyPlaying = isCurrentlyPlaying,
        onLikedStatusChanged = onLikedStatusChanged,
        customButtonIcon = customButtonIcon,
        customButtonAction = customButtonAction,
        isDownloaded = isDownloaded,
        downloadProgress = downloadProgress,
        onDeleteDownload = onDeleteDownload
    )
}

@Composable
private fun SongListItemContent(
    song: Song,
    trackEntities: List<TrackEntity>,
    index: Int,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    modifier: Modifier,
    isSelected: Boolean,
    isCurrentlyPlaying: Boolean,
    onLikedStatusChanged: (() -> Unit)?,
    customButtonIcon: String?,
    customButtonAction: (() -> Unit)?,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    onDeleteDownload: (() -> Unit)?
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showPopup by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val swipeState = rememberSongSwipeState(coroutineScope)
    val actionContext = SongActionContext(
        song = song,
        context = context,
        playerViewModel = playerViewModel,
        trackEntities = trackEntities,
        index = index,
        coroutineScope = coroutineScope,
        onLikedStatusChanged = onLikedStatusChanged,
        onShowPlaylistDialog = { showPlaylistPicker = true },
        onShowShareDialog = { showShareDialog = true }
    )
    val visuals = rememberSwipeVisuals(context)

    SongSwipeContainer(
        actionContext = actionContext,
        isSelected = isSelected,
        isCurrentlyPlaying = isCurrentlyPlaying,
        downloadProgress = downloadProgress,
        isDownloaded = isDownloaded,
        customButtonIcon = customButtonIcon,
        customButtonAction = customButtonAction,
        modifier = modifier,
        swipeState = swipeState,
        visuals = visuals,
        haptic = haptic,
        onOpenPopup = { showPopup = true }
    )

    SongListItemDialogs(
        showPopup = showPopup && customButtonAction == null,
        showShareDialog = showShareDialog,
        showPlaylistPicker = showPlaylistPicker,
        actionContext = actionContext,
        song = song,
        isDownloaded = isDownloaded,
        onDeleteDownload = onDeleteDownload,
        onDismissPopup = { showPopup = false },
        onDismissShare = { showShareDialog = false },
        onDismissPlaylistPicker = { showPlaylistPicker = false }
    )
}

@Composable
private fun SongSwipeContainer(
    actionContext: SongActionContext,
    isSelected: Boolean,
    isCurrentlyPlaying: Boolean,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    customButtonIcon: String?,
    customButtonAction: (() -> Unit)?,
    modifier: Modifier,
    swipeState: SongSwipeState,
    visuals: SwipeVisuals,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onOpenPopup: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
    ) {
        SwipeBackgrounds(
            dragOffset = swipeState.dragOffset.floatValue,
            visuals = visuals
        )
        SongRowContent(
            actionContext = actionContext,
            isSelected = isSelected,
            isCurrentlyPlaying = isCurrentlyPlaying,
            downloadProgress = downloadProgress,
            isDownloaded = isDownloaded,
            customButtonIcon = customButtonIcon,
            customButtonAction = customButtonAction,
            swipeState = swipeState,
            haptic = haptic,
            onOpenPopup = onOpenPopup
        )
    }
}

@Composable
private fun BoxScope.SwipeBackgrounds(
    dragOffset: Float,
    visuals: SwipeVisuals
) {
    val density = LocalDensity.current
    // Background actions - Right swipe (like/favorite)
    if (dragOffset > 0) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { dragOffset.toDp() })
                .background(Color.Transparent),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = visuals.rightIcon,
                color = visuals.rightColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }

    // Background actions - Left swipe (add to queue)
    if (dragOffset < 0) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (-dragOffset).toDp() })
                .align(Alignment.CenterEnd)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = visuals.leftIcon,
                color = visuals.leftColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SongRowContent(
    actionContext: SongActionContext,
    isSelected: Boolean,
    isCurrentlyPlaying: Boolean,
    downloadProgress: Float?,
    isDownloaded: Boolean,
    customButtonIcon: String?,
    customButtonAction: (() -> Unit)?,
    swipeState: SongSwipeState,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onOpenPopup: () -> Unit
) {
    val song = actionContext.song
    val dragOffset = swipeState.dragOffset.floatValue
    // Main content (draggable)
    Row(
        modifier = Modifier
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .pointerInput(song.youtubeId, actionContext.index, actionContext.trackEntities) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeState.cancelSettle() },
                    onDragEnd = {
                        handleSwipeEnd(swipeState.dragOffset.floatValue, swipeState, haptic, actionContext)
                    },
                    onHorizontalDrag = { _, dragAmount -> swipeState.dragBy(dragAmount) }
                )
            }
            .clickable {
                handleSongClick(swipeState.dragOffset.floatValue, haptic, actionContext, swipeState)
            }
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        TrackNumberLabel(number = song.number)
        // Song title and artist
        SongTitleAndArtist(
            song = song,
            isSelected = isSelected,
            isCurrentlyPlaying = isCurrentlyPlaying
        )
        // Estado offline: progreso de la descarga en curso, o marca de que
        // ya está bajada (la flecha cabe en el estilo terminal/monoespaciado)
        DownloadStatusIndicator(
            downloadProgress = downloadProgress,
            isDownloaded = isDownloaded
        )
        // Botón personalizable
        SongCustomActionButton(customButtonIcon = customButtonIcon) {
            if (customButtonAction != null) {
                customButtonAction()
            } else {
                onOpenPopup()
            }
        }
    }
}

@Composable
private fun TrackNumberLabel(number: Int) {
    Text(
        text = number.toString(),
        style = PlyrTextStyles.trackArtist(),
        modifier = Modifier.padding(end = PlyrSpacing.small, start = 8.dp)
    )
}

@Composable
private fun RowScope.SongTitleAndArtist(
    song: Song,
    isSelected: Boolean,
    isCurrentlyPlaying: Boolean
) {
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
}

@Composable
private fun DownloadStatusIndicator(
    downloadProgress: Float?,
    isDownloaded: Boolean
) {
    if (downloadProgress != null) {
        Text(
            text = "↓ ${(downloadProgress * 100).toInt()}%",
            style = PlyrTextStyles.trackArtist().copy(color = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(end = 4.dp)
        )
    } else if (isDownloaded) {
        Text(
            text = "↓",
            style = PlyrTextStyles.trackArtist().copy(color = MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
private fun SongCustomActionButton(
    customButtonIcon: String?,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Text(
            text = customButtonIcon ?: "*",
            style = PlyrTextStyles.menuOption(),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun handleSongClick(
    dragOffset: Float,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    actionContext: SongActionContext,
    swipeState: SongSwipeState
) {
    if (dragOffset.absoluteValue < 10f) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        actionContext.playerViewModel?.let { viewModel ->
            val tracks = actionContext.trackEntities
            val index = actionContext.index
            if (tracks.isNotEmpty() && index in tracks.indices) {
                viewModel.clearPlayerState()

                viewModel.setCurrentPlaylist(tracks, index)
                val selectedTrackEntity = tracks[index]
                viewModel.playback.play(selectedTrackEntity)
            }
        }
    } else {
        swipeState.reset()
    }
}

private fun handleSwipeEnd(
    dragOffset: Float,
    swipeState: SongSwipeState,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    actionContext: SongActionContext
) {
    when (swipeActionFor(dragOffset, swipeState.threshold)) {
        SwipeAction.RIGHT -> {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            executeSwipeAction(
                action = SwipeConfig.getSwipeRightAction(actionContext.context),
                actionContext = actionContext
            )
            swipeState.reset()
        }
        SwipeAction.LEFT -> {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            executeSwipeAction(
                action = SwipeConfig.getSwipeLeftAction(actionContext.context),
                actionContext = actionContext
            )
            swipeState.reset()
        }
        SwipeAction.NONE -> swipeState.reset()
    }
}

@Composable
private fun SongListItemDialogs(
    showPopup: Boolean,
    showShareDialog: Boolean,
    showPlaylistPicker: Boolean,
    actionContext: SongActionContext,
    song: Song,
    isDownloaded: Boolean,
    onDeleteDownload: (() -> Unit)?,
    onDismissPopup: () -> Unit,
    onDismissShare: () -> Unit,
    onDismissPlaylistPicker: () -> Unit
) {
    // Solo mostrar popup si no hay acción personalizada
    if (showPopup) {
        SongActionsDialog(
            actionContext = actionContext,
            isDownloaded = isDownloaded,
            onDeleteDownload = onDeleteDownload,
            onDismiss = onDismissPopup
        )
    }

    if (showShareDialog) {
        SongShareDialog(song = song, onDismiss = onDismissShare)
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            actionContext = actionContext,
            onDismiss = onDismissPlaylistPicker
        )
    }
}

@Composable
private fun SongActionsDialog(
    actionContext: SongActionContext,
    isDownloaded: Boolean,
    onDeleteDownload: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(false) }
    LaunchedEffect(actionContext.song.youtubeId) {
        actionContext.coroutineScope.launch {
            val repo = PlaylistLocalRepository(context)
            val song = actionContext.song
            isLiked = repo.liked.isTrackLikedByKey(song.title, song.artist, song.remoteId ?: song.title, song.youtubeId)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
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
                SongDialogHeader(song = actionContext.song)

                // Action buttons
                SongActionButtons(
                    song = actionContext.song,
                    actionContext = actionContext,
                    isLiked = isLiked,
                    isDownloaded = isDownloaded,
                    onDeleteDownload = onDeleteDownload,
                    onLikedChanged = { isLiked = it },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun SongDialogHeader(song: Song) {
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
}

@Composable
private fun SongActionButtons(
    song: Song,
    actionContext: SongActionContext,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onDeleteDownload: (() -> Unit)?,
    onLikedChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Like toggle
        SongLikeAction(
            song = song,
            isLiked = isLiked,
            actionContext = actionContext,
            onLikedChanged = onLikedChanged,
            onDismiss = onDismiss
        )

        // Add to Queue
        SongMenuItem(
            text = Translations.get(context, "add_to_queue"),
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                onDismiss()
                addTrackToQueue(
                    playerViewModel = actionContext.playerViewModel,
                    trackEntities = actionContext.trackEntities,
                    index = actionContext.index
                )
            }
        )

        // Add to Playlist (abre el selector que ya usa el swipe)
        SongMenuItem(
            text = Translations.get(context, "add_to_playlist"),
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                onDismiss()
                actionContext.onShowPlaylistDialog()
            }
        )

        // Share
        SongMenuItem(
            text = Translations.get(context, "share"),
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                onDismiss()
                actionContext.onShowShareDialog()
            }
        )

        // Delete download (solo si la pista está en el almacén offline)
        if (isDownloaded && onDeleteDownload != null) {
            SongDeleteDownloadAction(
                onDeleteDownload = onDeleteDownload,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun SongMenuItem(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun SongLikeAction(
    song: Song,
    isLiked: Boolean,
    actionContext: SongActionContext,
    onLikedChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SongMenuItem(
        text = if (isLiked) {
            Translations.get(context, "liked")
        } else {
            Translations.get(context, "like")
        },
        color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        onClick = {
            actionContext.coroutineScope.launch {
                val repo = PlaylistLocalRepository(context)
                val id = song.youtubeId ?: ""
                onLikedChanged(
                    repo.liked.toggleLikeTrack(
                        youtubeVideoId = id,
                        name = song.title,
                        artists = song.artist,
                        remoteTrackId = song.remoteId ?: song.title
                    )
                )
                actionContext.onLikedStatusChanged?.invoke()
            }
            onDismiss()
        }
    )
}

@Composable
private fun SongDeleteDownloadAction(
    onDeleteDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SongMenuItem(
        text = Translations.get(context, "delete_download"),
        color = MaterialTheme.colorScheme.error,
        onClick = {
            onDismiss()
            onDeleteDownload()
        }
    )
}

@Composable
private fun SongShareDialog(
    song: Song,
    onDismiss: () -> Unit
) {
    ShareDialog(
        item = ShareableItem(
            remoteId = song.remoteId,
            shareUrl = song.shareUrl,
            youtubeId = song.youtubeId,
            title = song.title,
            artist = song.artist,
            type = ShareType.TRACK
        ),
        onDismiss = onDismiss
    )
}

@Composable
private fun PlaylistPickerDialog(
    actionContext: SongActionContext,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var targetPlaylists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        playlistsLoading = true
        val repo = PlaylistLocalRepository(context)
        targetPlaylists = repo.getAllPlaylists()
            .filter { it.remoteId != PlaylistLocalRepository.LIKED_SONGS_ID && !it.remoteId.startsWith("album_") }
            .sortedBy { it.name.lowercase() }
        playlistsLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
                .fillMaxWidth(0.9f)
        ) {
            PlaylistPickerBody(
                targetPlaylists = targetPlaylists,
                playlistsLoading = playlistsLoading,
                onPlaylistClick = { playlist ->
                    addTrackToPlaylist(actionContext, playlist.remoteId, onDismiss)
                },
                onClose = onDismiss
            )
        }
    }
}

@Composable
private fun PlaylistPickerBody(
    targetPlaylists: List<PlaylistEntity>,
    playlistsLoading: Boolean,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = Translations.get(context, "add_to_playlist"),
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        )

        PlaylistPickerContent(
            playlists = targetPlaylists,
            isLoading = playlistsLoading,
            onPlaylistClick = onPlaylistClick
        )

        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = Translations.get(context, "close"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun PlaylistPickerContent(
    playlists: List<PlaylistEntity>,
    isLoading: Boolean,
    onPlaylistClick: (PlaylistEntity) -> Unit
) {
    val context = LocalContext.current
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp
        )
    } else if (playlists.isEmpty()) {
        Text(
            text = Translations.get(context, "no_playlists"),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            playlists.forEach { playlist ->
                Text(
                    text = playlist.name,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(playlist) }
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

private fun executeSwipeAction(
    action: String,
    actionContext: SongActionContext
) {
    when (action) {
        Config.SWIPE_ACTION_ADD_TO_LIKED -> {
            actionContext.coroutineScope.launch {
                val repo = PlaylistLocalRepository(actionContext.context)
                val song = actionContext.song
                val isNowLiked = repo.liked.toggleLikeTrack(
                    youtubeVideoId = song.youtubeId ?: "",
                    name = song.title,
                    artists = song.artist,
                    remoteTrackId = song.remoteId ?: song.title
                )
                Log.d("SongListItem", if (isNowLiked) "♥ Liked: ${song.title}" else "♡ Unliked: ${song.title}")
                actionContext.onLikedStatusChanged?.invoke()
            }
        }
        Config.SWIPE_ACTION_ADD_TO_QUEUE -> {
            // Añadir a cola
            addTrackToQueue(
                playerViewModel = actionContext.playerViewModel,
                trackEntities = actionContext.trackEntities,
                index = actionContext.index
            )
        }
        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> {
            actionContext.onShowPlaylistDialog()
        }
        Config.SWIPE_ACTION_SHARE -> {
            // Compartir
            actionContext.onShowShareDialog()
        }
        else -> {
            Log.w("SongListItem", "Acción desconocida para swipe: $action")
        }
    }
}

private fun addTrackToQueue(
    playerViewModel: PlayerViewModel?,
    trackEntities: List<TrackEntity>,
    index: Int
) {
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

private fun addTrackToPlaylist(
    actionContext: SongActionContext,
    playlistId: String,
    onDone: () -> Unit
) {
    val song = actionContext.song
    val track = actionContext.trackEntities.getOrNull(actionContext.index) ?: TrackEntity(
        id = "",
        playlistId = playlistId,
        remoteTrackId = song.remoteId ?: song.title,
        name = song.title,
        artists = song.artist,
        youtubeVideoId = song.youtubeId,
        audioUrl = null,
        position = 0
    )
    actionContext.coroutineScope.launch {
        val repo = PlaylistLocalRepository(actionContext.context)
        repo.tracks.addTrackToYouTubePlaylist(playlistId, track)
        onDone()
    }
}
