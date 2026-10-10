package com.plyr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.*
import androidx.media3.common.Player
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.formatTime
import com.plyr.utils.Config
import com.plyr.database.TrackEntity
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator

/**
 * FloatingMusicControls - Controles flotantes de música que aparecen en la parte inferior
 * 
 * Este composable proporciona:
 * - Controles de reproducción (play/pause, seek)
 * - Información de la canción actual con texto en marquesina
 * - Barra de progreso interactiva
 * - Indicadores de carga y error
 * - Diseño terminal-style consistente con el tema de la app
 * 
 * Se posiciona como overlay flotante que no interfiere con el contenido principal.
 */

/**
 * Componente de texto con efecto marquesina para textos largos.
 * 
 * @param text Texto a mostrar
 * @param modifier Modificadores de Compose
 * @param style Estilo del texto
 * @param color Color del texto
 * @param maxLines Número máximo de líneas
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1
) {
    val density = LocalDensity.current
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val shouldAnimate = textWidth > containerWidth && containerWidth > 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) -(textWidth - containerWidth).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (shouldAnimate) maxOf(text.length * 100, 3000) else 0,
                easing = LinearEasing,
                delayMillis = 1500 // Pausa al inicio
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_animation"
    )
    
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidth = size.width
            }
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { size ->
                    textWidth = size.width
                }
                .offset { with(density) { androidx.compose.ui.unit.IntOffset(animatedOffset.toDp().roundToPx(), 0) } }
        )
    }
}

/**
 * Controles flotantes principales de música.
 * 
 * @param playerViewModel ViewModel que maneja la lógica de reproducción
 * @param modifier Modificadores de Compose para personalización
 */
@Composable
fun FloatingMusicControls(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onShowQueue: () -> Unit = {}
) {
    // Observar estados del PlayerViewModel
    val currentTitle by playerViewModel.currentTitle.observeAsState()
    val isLoading by playerViewModel.isLoading.observeAsState(false)
    val error by playerViewModel.error.observeAsState()
    val currentTrack by playerViewModel.currentTrack.observeAsState()
    val currentPlaylist by playerViewModel.currentPlaylist.observeAsState()
    val currentTrackIndex by playerViewModel.currentTrackIndex.observeAsState()

    val playback = rememberPlaybackState(playerViewModel)

    // Mostrar controles solo si hay contenido o estado relevante
    if (currentTitle != null || isLoading || error != null) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Línea de estado y título
                StatusAndTitleRow(
                    isLoading = isLoading,
                    currentTitle = currentTitle,
                    currentTrack = currentTrack,
                    currentPlaylist = currentPlaylist,
                    currentTrackIndex = currentTrackIndex,
                    position = playback.position,
                    duration = playback.duration
                )

                // Barra de progreso/loading
                ProgressBar(
                    isLoading = isLoading,
                    progress = playback.progress,
                    duration = playback.duration,
                    playerViewModel = playerViewModel
                )

                // Controles de reproducción
                PlaybackControls(
                    isLoading = isLoading,
                    isPlaying = playback.isPlaying,
                    playerViewModel = playerViewModel,
                    currentTrack = currentTrack,
                    onShowQueue = onShowQueue
                )
            }
        }
    }
}

private data class PlaybackState(
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    val progress: Float
)

private data class PlaybackSnapshot(
    val isPlaying: Boolean,
    val total: Long,
    val position: Long,
    val progress: Float
)

/**
 * Refleja el estado del reproductor en la UI.
 *
 * Antes se escribía en los estados sin comparar con el valor anterior, así
 * que cada tick provocaba una recomposición aunque nada hubiera cambiado.
 * Ahora solo se escribe cuando algún valor difiere, y se lee de una
 * referencia fija al reproductor para no tocar uno ya liberado.
 */
@Composable
private fun rememberPlaybackState(playerViewModel: PlayerViewModel): PlaybackState {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTotal = 0L
        while (true) {
            // B35: `exoPlayer` es un `var` no observable; si se usara de clave,
            // una composición con el reproductor a null (y sin recomposiciones
            // posteriores) dejaba el poll sin arrancar nunca. Se relee en cada
            // vuelta y el lazo aguanta hasta que el reproductor exista.
            val player = playerViewModel.exoPlayer
            if (player == null) {
                delay(500)
                continue
            }
            val snapshot = readPlaybackSnapshot(player)

            if (snapshot.isPlaying != isPlaying) isPlaying = snapshot.isPlaying
            if (snapshot.total != lastTotal) {
                lastTotal = snapshot.total
                duration = if (snapshot.total > 0) snapshot.total else 1L
            }
            if (snapshot.position != position) position = snapshot.position
            if (snapshot.progress != progress) progress = snapshot.progress

            delay(500)
        }
    }

    return PlaybackState(isPlaying, duration, position, progress)
}

private fun readPlaybackSnapshot(player: Player): PlaybackSnapshot {
    val active = player.playbackState == Player.STATE_READY ||
        player.playbackState == Player.STATE_BUFFERING
    val total = if (active && player.duration > 0) player.duration else 0L
    val pos = if (active) player.currentPosition else 0L
    val ratio = if (total > 0) pos.toFloat() / total.toFloat() else 0f
    return PlaybackSnapshot(player.isPlaying, total, pos, ratio)
}

/**
 * Fila que muestra el estado y título de la canción.
 */
@Composable
private fun StatusAndTitleRow(
    isLoading: Boolean,
    currentTitle: String?,
    currentTrack: TrackEntity?,
    currentPlaylist: List<TrackEntity>?,
    currentTrackIndex: Int?,
    position: Long,
    duration: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Estado y título
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                LoadingStatusLabel()
            } else {
                NowPlayingTitle(
                    currentTitle = currentTitle,
                    currentTrack = currentTrack
                )
            }
        }

        // Información de tiempo y playlist
        PlaybackTimeAndPlaylist(
            isLoading = isLoading,
            position = position,
            duration = duration,
            currentPlaylist = currentPlaylist,
            currentTrackIndex = currentTrackIndex
        )
    }
}

@Composable
private fun LoadingStatusLabel() {
    Text(
        text = "$ loading",
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.tertiary
        )
    )
}

@Composable
private fun RowScope.NowPlayingTitle(
    currentTitle: String?,
    currentTrack: TrackEntity?
) {
    val displayTitle = when {
        currentTrack != null -> "${currentTrack.name} - ${currentTrack.artists}"
        currentTitle != null -> currentTitle
        else -> "Playing audio..."
    }

    MarqueeText(
        text = displayTitle,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        ),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PlaybackTimeAndPlaylist(
    isLoading: Boolean,
    position: Long,
    duration: Long,
    currentPlaylist: List<TrackEntity>?,
    currentTrackIndex: Int?
) {
    Column(
        horizontalAlignment = Alignment.End
    ) {
        if (!isLoading) {
            Text(
                text = "${formatTime(position)}/${formatTime(duration)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            )

            if (currentPlaylist != null && currentTrackIndex != null && currentPlaylist.isNotEmpty()) {
                Text(
                    text = "${currentTrackIndex + 1}/${currentPlaylist.size}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }
    }
}

/**
 * Sección de barra de progreso o indicador de carga.
 */
@Composable
private fun ProgressBar(
    isLoading: Boolean,
    progress: Float,
    duration: Long,
    playerViewModel: PlayerViewModel
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else progress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        if (isLoading) {
            LoadingProgressIndicator()
        } else {
            DraggableProgressBar(
                displayProgress = displayProgress,
                duration = duration,
                onDragStart = { fraction ->
                    isDragging = true
                    dragProgress = fraction
                },
                onDrag = { fraction ->
                    dragProgress = fraction
                },
                onDragEnd = {
                    if (isDragging && duration > 0) {
                        playerViewModel.exoPlayer?.seekTo((duration * dragProgress).toLong())
                        isDragging = false
                    }
                }
            )
        }
    }
}

@Composable
private fun LoadingProgressIndicator() {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = MaterialTheme.colorScheme.tertiary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    )
}

@Composable
private fun DraggableProgressBar(
    displayProgress: Float,
    duration: Long,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    // Barra de progreso minimalista - solo barra, sin indicador circular
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (duration > 0) {
                            onDragStart((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    },
                    onDrag = { change, _ ->
                        if (duration > 0) {
                            onDrag((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    },
                    onDragEnd = { onDragEnd() }
                )
            }
    ) {
        // Barra de progreso (parte llena)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(displayProgress)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        // Barra vacía (parte restante)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
    }
}

/**
 * Fila de controles de reproducción (anterior, play/pause, siguiente, repetición).
 */
@Composable
private fun PlaybackControls(
    isLoading: Boolean,
    isPlaying: Boolean,
    playerViewModel: PlayerViewModel,
    currentTrack: TrackEntity?,
    onShowQueue: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentRepeatMode by remember { mutableStateOf(Config.getRepeatMode(context)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        // Layout con el botón * centrado entre borde izquierdo y los controles centrales
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Espacio izquierdo con el botón ^ centrado
            QueueToggleButton(
                isEnabled = !isLoading && currentTrack != null,
                onClick = onShowQueue
            )

            // Botones principales centrados
            MainPlaybackButtons(
                isLoading = isLoading,
                isPlaying = isPlaying,
                playerViewModel = playerViewModel
            )

            // Espacio derecho con el botón de repetición centrado
            RepeatControl(
                currentMode = currentRepeatMode,
                isEnabled = !isLoading,
                onClick = {
                    val nextMode = Config.getNextRepeatMode(currentRepeatMode)
                    currentRepeatMode = nextMode
                    Config.setRepeatMode(context, nextMode)
                    playerViewModel.updateRepeatMode()
                }
            )
        }
    }

    // Popup del menú de canción usando el componente reutilizable (mantenido para uso futuro)
}

@Composable
private fun RowScope.QueueToggleButton(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "^",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            ),
            color = if (isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .offset(y = (-2).dp)
                .clickable(enabled = isEnabled) { onClick() }
                .padding(6.dp)
        )
    }
}

@Composable
private fun MainPlaybackButtons(
    isLoading: Boolean,
    isPlaying: Boolean,
    playerViewModel: PlayerViewModel
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón anterior
        PlaybackButton(
            text = "<<",
            fontSize = 16.sp,
            isEnabled = !isLoading,
            onClick = { playerViewModel.navigateToPrevious() }
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Botón play/pause principal
        PlaybackButton(
            text = if (isPlaying) "//" else ">",
            fontSize = 24.sp,
            isEnabled = !isLoading,
            onClick = {
                if (isPlaying) playerViewModel.pausePlayer()
                else playerViewModel.playPlayer()
            }
        )

        Spacer(modifier = Modifier.width(24.dp))

        // Botón siguiente
        PlaybackButton(
            text = ">>",
            fontSize = 16.sp,
            isEnabled = !isLoading,
            onClick = { playerViewModel.navigateToNext() }
        )
    }
}

@Composable
private fun RowScope.RepeatControl(
    currentMode: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center
    ) {
        RepeatButton(
            currentMode = currentMode,
            isEnabled = isEnabled,
            onClick = onClick
        )
    }
}

/**
 * Botón de control de reproducción reutilizable.
 */
@Composable
private fun PlaybackButton(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize
        ),
        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .clickable(enabled = isEnabled) { onClick() }
            .padding(6.dp)
    )
}

/**
 * Botón de repetición con estilo terminal que cicla entre modos.
 */
@Composable
private fun RepeatButton(
    currentMode: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val symbol = when (currentMode) {
        Config.REPEAT_MODE_OFF -> "o"   // Sin repetición
        Config.REPEAT_MODE_ONE -> "1"   // Repetir una vez
        Config.REPEAT_MODE_ALL -> "*"   // Repetir indefinidamente
        else -> "o"
    }
    val color = when (currentMode) {
        Config.REPEAT_MODE_OFF -> MaterialTheme.colorScheme.outline
        Config.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.tertiary
        Config.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Text(
        text = symbol,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        ),
        color = if (isEnabled) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        modifier = modifier
            .clickable(enabled = isEnabled) { onClick() }
            .padding(4.dp)
    )
}
