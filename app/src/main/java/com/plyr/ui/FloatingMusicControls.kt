package com.plyr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.formatTime
import com.plyr.ui.theme.TerminalTheme
import kotlinx.coroutines.delay
import androidx.compose.foundation.background

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
    var textWidth by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }
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
            position = position,
            duration = duration,
            progress = progress,
            isPlaying = isPlaying,
            playerViewModel = playerViewModel
        )
    }
}

/**
 * Actualiza los estados del reproductor desde ExoPlayer.
 */
private suspend fun updatePlayerState(
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
    position: Long,
    duration: Long,
    progress: Float,
    isPlaying: Boolean,
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
                playerViewModel = playerViewModel
            )
            
            // Mensaje de error si existe
            error?.let {
                ErrorMessage(it)
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
    error: String?,
    audioUrl: String?,
    currentTitle: String?,
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
            StatusIndicator(isLoading, error, audioUrl)
            
            if (audioUrl != null && !isLoading) {
                TitleWithMarquee(currentTitle)
            }
        }
        
        // Información de tiempo
        if (audioUrl != null && !isLoading) {
            TimeDisplay(position, duration)
        }
    }
}

/**
 * Indicador de estado del reproductor.
 */
@Composable
private fun StatusIndicator(isLoading: Boolean, error: String?, audioUrl: String?) {
    Text(
        text = when {
            isLoading -> "$ loading"
            error != null -> "$ error"
            audioUrl != null -> "$ "
            else -> "$ ready"
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
private fun RowScope.TitleWithMarquee(currentTitle: String?) {
    MarqueeText(
        text = currentTitle ?: "Playing audio...",
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
    val displayProgress = if (isDragging) dragProgress else progress
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        if (isLoading) {
            LoadingProgressBar()
        } else {
            InteractiveProgressBar(
                audioUrl = audioUrl,
                displayProgress = displayProgress,
                duration = duration,
                isDragging = isDragging,
                onDragStart = { progress ->
                    if (audioUrl != null && duration > 0) {
                        isDragging = true
                        dragProgress = progress
                    }
                },
                onDragEnd = { 
                    if (isDragging && duration > 0) {
                        val newPosition = (duration * dragProgress).toLong()
                        playerViewModel.seekTo(newPosition)
                        isDragging = false
                    }
                },
                onTap = { progress ->
                    if (audioUrl != null && duration > 0) {
                        val newPosition = (duration * progress).toLong()
                        playerViewModel.seekTo(newPosition)
                    }
                }
            )
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
 * Barra de progreso interactiva para reproducción.
 */
@Composable
private fun InteractiveProgressBar(
    audioUrl: String?,
    displayProgress: Float,
    duration: Long,
    isDragging: Boolean,
    onDragStart: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val progress = (offset.x / size.width).coerceIn(0f, 1f)
                        onDragStart(progress)
                    },
                    onDrag = { _, _ -> /* Handled in start/end */ },
                    onDragEnd = { onDragEnd() }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val progress = (offset.x / size.width).coerceIn(0f, 1f)
                    onTap(progress)
                }
            }
    ) {
        // Barra de progreso visual
        if (audioUrl != null) {
            ProgressBar(displayProgress)
            ProgressIndicator(displayProgress, isDragging)
        }
    }
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
 * Fila de controles de reproducción (anterior, play/pause, siguiente).
 */
@Composable
private fun PlaybackControlsRow(
    audioUrl: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    playerViewModel: PlayerViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón anterior
        PlaybackButton(
            text = "<<",
            fontSize = 16.sp,
            isEnabled = audioUrl != null && !isLoading,
            onClick = { /* TODO: Implementar anterior */ }
        )

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

        // Botón siguiente
        PlaybackButton(
            text = ">>",
            fontSize = 16.sp,
            isEnabled = audioUrl != null && !isLoading,
            onClick = { /* TODO: Implementar siguiente */ }
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
 * Mensaje de error compacto.
 */
@Composable
private fun ErrorMessage(error: String) {
    Text(
        text = "ERR: ${error.take(40)}${if (error.length > 40) "..." else ""}",
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.error
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// === PREVIEWS ===

/**
 * Preview de los controles flotantes de música.
 * Muestra una simulación del componente con datos mock.
 */
@Preview(showBackground = true, widthDp = 320, heightDp = 200)
@Composable
fun FloatingMusicControlsPreview() {
    TerminalTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Simulación de controles flotantes con datos mock
            MockFloatingControls(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Componente mock para el preview.
 */
@Composable
private fun MockFloatingControls(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Estado y título mock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$ Now Playing: Sample Song Title",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "01:23/04:56",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Barra de progreso mock
            LinearProgressIndicator(
                progress = 0.3f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Controles mock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MockPlaybackButton("<<", 16.sp)
                MockPlaybackButton("||", 24.sp) // Pause state
                MockPlaybackButton(">>", 16.sp)
            }
        }
    }
}

/**
 * Botón mock para el preview.
 */
@Composable
private fun MockPlaybackButton(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(6.dp)
    )
}
