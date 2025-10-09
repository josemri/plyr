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
import androidx.compose.foundation.border
import androidx.compose.animation.core.*
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
                .offset(x = with(density) { animatedOffset.toDp() })
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
    modifier: Modifier = Modifier
) {
    // Observar estados del PlayerViewModel
    val currentTitle by playerViewModel.currentTitle.observeAsState()
    val isLoading by playerViewModel.isLoading.observeAsState(false)
    val error by playerViewModel.error.observeAsState()
    val currentTrack by playerViewModel.currentTrack.observeAsState()
    val currentPlaylist by playerViewModel.currentPlaylist.observeAsState()
    val currentTrackIndex by playerViewModel.currentTrackIndex.observeAsState()

    // Estados locales del reproductor
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }

    
    // === EFECTOS Y ACTUALIZACIONES DE ESTADO ===
    
    /**
     * Actualiza el estado del reproductor de forma periódica.
     * Obtiene información de posición, duración y estado de reproducción.
     */
    LaunchedEffect(playerViewModel.exoPlayer) {
        while (true) {
            playerViewModel.exoPlayer?.let { player ->
                isPlaying = player.isPlaying
                duration = if (player.duration > 0) player.duration else 1L
                position = player.currentPosition
                progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
            }
            delay(500) // Actualizar cada 500ms
        }
    }

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
                    position = position,
                    duration = duration
                )

                // Barra de progreso/loading
                ProgressBar(
                    isLoading = isLoading,
                    progress = progress,
                    duration = duration,
                    playerViewModel = playerViewModel
                )

                // Controles de reproducción
                PlaybackControls(
                    isLoading = isLoading,
                    isPlaying = isPlaying,
                    playerViewModel = playerViewModel
                )
            }
        }
    }
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
                Text(
                    text = "$ loading",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFFFD93D)
                    )
                )
            } else {
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
        }

        // Información de tiempo y playlist
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
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFFFFD93D),
                trackColor = Color(0xFF2C2C2C).copy(alpha = 0.3f),
            )
        } else {
            // Slider interactivo estilo Spotify/YouTube
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (duration > 0) {
                                    isDragging = true
                                    dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                }
                            },
                            onDrag = { change, _ ->
                                if (duration > 0) {
                                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (isDragging && duration > 0) {
                                    playerViewModel.exoPlayer?.seekTo((duration * dragProgress).toLong())
                                    isDragging = false
                                }
                            }
                        )
                    }
            ) {
                // Barra de progreso visual
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(displayProgress)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )

                val density = LocalDensity.current
                // Indicador circular de posición
                Box(
                    modifier = Modifier
                        .size(if (isDragging) 16.dp else 12.dp) // Más grande al arrastrar
                        .background(
                            MaterialTheme.colorScheme.primary,
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.surface,
                            androidx.compose.foundation.shape.CircleShape
                        )
                        .offset(x = (displayProgress * density.run {
                            (300.dp - 12.dp).toPx()
                        } / density.density).dp))
            }
        }
    }
}

/**
 * Fila de controles de reproducción (anterior, play/pause, siguiente, repetición).
 */
@Composable
private fun PlaybackControls(
    isLoading: Boolean,
    isPlaying: Boolean,
    playerViewModel: PlayerViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentRepeatMode by remember { mutableStateOf(Config.getRepeatMode(context)) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        // Botones principales centrados
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
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

        // Botón de repetición en la esquina inferior derecha
        RepeatButton(
            currentMode = currentRepeatMode,
            isEnabled = !isLoading,
            onClick = {
                val nextMode = Config.getNextRepeatMode(currentRepeatMode)
                currentRepeatMode = nextMode
                Config.setRepeatMode(context, nextMode)
                playerViewModel.updateRepeatMode()
            },
            modifier = Modifier.align(Alignment.BottomEnd)
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
        Config.REPEAT_MODE_ONE -> Color(0xFFFFD93D)
        Config.REPEAT_MODE_ALL -> Color(0xFF4ECDC4)
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
