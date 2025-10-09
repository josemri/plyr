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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
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
    val audioUrl by playerViewModel.audioUrl.observeAsState()
    val currentTitle by playerViewModel.currentTitle.observeAsState()
    val isLoading by playerViewModel.isLoading.observeAsState(false)
    val error by playerViewModel.error.observeAsState()
    
    // Observar estados de navegación de playlist
    val hasPrevious by playerViewModel.hasPrevious.observeAsState(false)
    val hasNext by playerViewModel.hasNext.observeAsState(false)
    val observedCurrentTrack by playerViewModel.currentTrack.observeAsState()
    val observedCurrentPlaylist by playerViewModel.currentPlaylist.observeAsState()
    val observedCurrentTrackIndex by playerViewModel.currentTrackIndex.observeAsState()
    
    // Observar estados de cola
    val isQueueMode by playerViewModel.isQueueMode.observeAsState(false)
    val playbackQueue by playerViewModel.playbackQueue.observeAsState(mutableListOf())
    
    // Debug: Log de estados de navegación
    LaunchedEffect(hasPrevious, hasNext, observedCurrentPlaylist?.size, observedCurrentTrackIndex, isQueueMode, playbackQueue.size) {
        Log.d("FloatingControls", "Navigation states: hasPrevious=$hasPrevious, hasNext=$hasNext, playlistSize=${observedCurrentPlaylist?.size}, currentIndex=$observedCurrentTrackIndex, isQueue=$isQueueMode, queueSize=${playbackQueue.size}")
    }
    
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
            updatePlayerState(playerViewModel) { newIsPlaying, newDuration, newPosition, newProgress ->
                isPlaying = newIsPlaying
                duration = newDuration
                position = newPosition
                progress = newProgress
            }
            delay(500) // Actualizar cada 500ms
        }
    }

    // Mostrar controles solo si hay contenido o estado relevante
    if (shouldShowControls(audioUrl, isLoading, error)) {
        FloatingControlsCard(
            modifier = modifier,
            isLoading = isLoading,
            error = error,
            audioUrl = audioUrl,
            currentTitle = currentTitle,
            currentTrack = observedCurrentTrack,
            currentPlaylist = observedCurrentPlaylist,
            currentTrackIndex = observedCurrentTrackIndex,
            position = position,
            duration = duration,
            progress = progress,
            isPlaying = isPlaying,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            isQueueMode = isQueueMode,
            queueSize = playbackQueue.size,
            playerViewModel = playerViewModel
        )
    }
}

/**
 * Actualiza los estados del reproductor desde ExoPlayer.
 */
private fun updatePlayerState(
    playerViewModel: PlayerViewModel,
    onUpdate: (Boolean, Long, Long, Float) -> Unit
) {
    playerViewModel.exoPlayer?.let { player ->
        val currentIsPlaying = player.isPlaying
        val currentDuration = if (player.duration > 0) player.duration else 1L
        val currentPosition = player.currentPosition
        val currentProgress = if (currentDuration > 0) currentPosition.toFloat() / currentDuration.toFloat() else 0f
        
        onUpdate(currentIsPlaying, currentDuration, currentPosition, currentProgress)
    }
}

/**
 * Determina si los controles deben mostrarse.
 */
private fun shouldShowControls(audioUrl: String?, isLoading: Boolean, error: String?): Boolean {
    return audioUrl != null || isLoading || error != null
}

/**
 * Card principal que contiene todos los controles flotantes.
 */
@Composable
private fun FloatingControlsCard(
    modifier: Modifier,
    isLoading: Boolean,
    error: String?,
    audioUrl: String?,
    currentTitle: String?,
    currentTrack: TrackEntity?,
    currentPlaylist: List<TrackEntity>?,
    currentTrackIndex: Int?,
    position: Long,
    duration: Long,
    progress: Float,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    isQueueMode: Boolean,
    queueSize: Int,
    playerViewModel: PlayerViewModel
) {
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
                error = error,
                audioUrl = audioUrl,
                currentTitle = currentTitle,
                currentTrack = currentTrack,
                currentPlaylist = currentPlaylist,
                currentTrackIndex = currentTrackIndex,
                position = position,
                duration = duration
            )
            
            // Barra de progreso/loading
            ProgressSection(
                isLoading = isLoading,
                audioUrl = audioUrl,
                progress = progress,
                duration = duration,
                playerViewModel = playerViewModel
            )
            
            // Controles de reproducción
            PlaybackControlsRow(
                audioUrl = audioUrl,
                isLoading = isLoading,
                isPlaying = isPlaying,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                playerViewModel = playerViewModel
            )
            
            // Indicador de cola si está activa
            if (isQueueMode && queueSize > 0) {
                QueueIndicator(queueSize = queueSize)
            }
            
            // Mensaje de error si existe
//            error?.let {
//                ErrorMessage(it)
//            }
        }
    }
}

/**
 * Fila que muestra el estado y título de la canción.
 */
@Composable
private fun StatusAndTitleRow(
    isLoading: Boolean,
    error: String?,
    audioUrl: String?,
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
            StatusIndicator(isLoading, error, audioUrl, currentPlaylist)
            
            if (audioUrl != null && !isLoading) {
                TitleWithMarquee(currentTitle, currentTrack)
            }
        }
        
        // Información de tiempo y playlist
        Column(
            horizontalAlignment = Alignment.End
        ) {
            if (audioUrl != null && !isLoading) {
                TimeDisplay(position, duration)
                
                // Mostrar información de playlist si está disponible
                PlaylistInfoDisplay(currentPlaylist, currentTrackIndex)
            }
        }
    }
}

/**
 * Indicador de estado del reproductor.
 */
@Composable
private fun StatusIndicator(
    isLoading: Boolean, 
    error: String?, 
    audioUrl: String?,
    currentPlaylist: List<TrackEntity>?
) {
    currentPlaylist != null && currentPlaylist.isNotEmpty()
    
    Text(
        text = when {
            isLoading -> "$ loading"
            else -> ""
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = when {
                error != null -> MaterialTheme.colorScheme.error
                isLoading -> Color(0xFFFFD93D)
                audioUrl != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondary
            }
        )
    )
}

/**
 * Título con efecto marquesina.
 */
@Composable
private fun RowScope.TitleWithMarquee(currentTitle: String?, currentTrack: TrackEntity?) {
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

/**
 * Display de tiempo actual y duración.
 */
@Composable
private fun TimeDisplay(position: Long, duration: Long) {
    Text(
        text = "${formatTime(position)}/${formatTime(duration)}",
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    )
}

/**
 * Display de información de playlist.
 */
@Composable
private fun PlaylistInfoDisplay(currentPlaylist: List<TrackEntity>?, currentTrackIndex: Int?) {
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

/**
 * Sección de barra de progreso o indicador de carga.
 */
@Composable
private fun ProgressSection(
    isLoading: Boolean,
    audioUrl: String?,
    progress: Float,
    duration: Long,
    playerViewModel: PlayerViewModel
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastDragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else progress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        if (isLoading) {
            LoadingProgressBar()
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
                                if (audioUrl != null && duration > 0) {
                                    isDragging = true
                                    dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                    lastDragProgress = dragProgress
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (audioUrl != null && duration > 0) {
                                    val newProgress = ((change.position.x) / size.width).coerceIn(0f, 1f)
                                    dragProgress = newProgress
                                    lastDragProgress = newProgress
                                }
                            },
                            onDragEnd = {
                                if (isDragging && duration > 0) {
                                    val newPosition = (duration * lastDragProgress).toLong()
                                    playerViewModel.exoPlayer?.seekTo(newPosition)
                                    isDragging = false
                                }
                            }
                        )
                    }
            ) {
                // Barra de progreso visual
                if (audioUrl != null) {
                    ProgressBar(displayProgress)
                    ProgressIndicator(displayProgress, isDragging)
                }
            }
        }
    }
}

/**
 * Barra de progreso para estado de carga.
 */
@Composable
private fun LoadingProgressBar() {
    LinearProgressIndicator(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        color = Color(0xFFFFD93D),
        trackColor = Color(0xFF2C2C2C).copy(alpha = 0.3f),
    )
}

/**
 * Barra visual de progreso.
 */
@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(progress)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(2.dp)
            )
    )
}

/**
 * Indicador circular de posición.
 */
@Composable
private fun ProgressIndicator(progress: Float, isDragging: Boolean) {
    val density = LocalDensity.current
    
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
            .offset(x = (progress * density.run { 
                (300.dp - 12.dp).toPx() 
            } / density.density).dp)
    )
}

/**
 * Fila de controles de reproducción (anterior, play/pause, siguiente, repetición).
 */
@Composable
private fun PlaybackControlsRow(
    audioUrl: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    playerViewModel: PlayerViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Estado del modo de repetición
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
                isEnabled = audioUrl != null && !isLoading,
                onClick = {
                    coroutineScope.launch {
                        playerViewModel.navigateToPrevious()
                    }
                }
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Botón play/pause principal
            PlaybackButton(
                text = if (isPlaying) "//" else ">",
                fontSize = 24.sp,
                isEnabled = audioUrl != null && !isLoading,
                onClick = {
                    if (isPlaying) {
                        playerViewModel.pausePlayer()
                    } else {
                        playerViewModel.playPlayer()
                    }
                }
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Botón siguiente
            PlaybackButton(
                text = ">>",
                fontSize = 16.sp,
                isEnabled = audioUrl != null && !isLoading,
                onClick = {
                    coroutineScope.launch {
                        playerViewModel.navigateToNext()
                    }
                }
            )
        }

        // Botón de repetición en la esquina inferior derecha
        RepeatButton(
            currentMode = currentRepeatMode,
            isEnabled = audioUrl != null && !isLoading,
            onClick = {
                val nextMode = Config.getNextRepeatMode(currentRepeatMode)
                currentRepeatMode = nextMode
                Config.setRepeatMode(context, nextMode)
                // TODO: Aplicar la lógica de repetición al PlayerViewModel
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

/**
 * Mensaje de error compacto.
 */
//@Composable
//private fun ErrorMessage(error: String) {
//    Text(
//        text = "ERR: ${error.take(40)}${if (error.length > 40) "..." else ""}",
//        style = MaterialTheme.typography.bodyMedium.copy(
//            fontFamily = FontFamily.Monospace,



@Composable
private fun QueueIndicator(queueSize: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "● QUEUE: $queueSize pending",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFFF6B6B)
            )
        )
    }
}