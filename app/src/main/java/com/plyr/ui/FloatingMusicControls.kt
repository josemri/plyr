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

@Composable
fun FloatingMusicControls(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val audioUrl by playerViewModel.audioUrl.observeAsState()
    val currentTitle by playerViewModel.currentTitle.observeAsState()
    val isLoading by playerViewModel.isLoading.observeAsState(false)
    val error by playerViewModel.error.observeAsState()
    
    // Estados del reproductor
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Actualizar estado del reproductor
    LaunchedEffect(playerViewModel.exoPlayer) {
        while (true) {
            playerViewModel.exoPlayer?.let { player ->
                isPlaying = player.isPlaying
                duration = if (player.duration > 0) player.duration else 1L
                position = player.currentPosition
                progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
            }
            delay(500)
        }
    }

    // Siempre mostrar controles
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A) // Fondo sólido sin transparencia
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Línea única con estado, título y tiempo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Estado y título en una sola línea
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isLoading -> "$ loading"
                            error != null -> "$ error"
                            audioUrl != null -> "$ "
                            else -> "$ ready"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = when {
                                error != null -> Color(0xFFFF6B6B)
                                isLoading -> Color(0xFFFFD93D)
                                audioUrl != null -> Color(0xFF4ECDC4)
                                else -> Color(0xFF95A5A6)
                            }
                        )
                    )
                    
                    if (audioUrl != null && !isLoading) {
                        MarqueeText(
                            text = currentTitle ?: "Playing audio...",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = Color(0xFF4ECDC4)
                        )
                    }
                }
                
                // Tiempo solo si hay audio
                if (audioUrl != null && !isLoading) {
                    Text(
                        text = "${formatTime(position)}/${formatTime(duration)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Barra de progreso mejorada y más interactiva
            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableFloatStateOf(0f) }
            val displayProgress = if (isDragging) dragProgress else progress
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                // Barra de fondo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2C2C2C))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (audioUrl != null && !isLoading && duration > 0) {
                                        isDragging = true
                                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                    }
                                },
                                onDrag = { _, _ ->
                                    // El drag se maneja en onDragStart y onDragEnd
                                },
                                onDragEnd = {
                                    if (isDragging && duration > 0) {
                                        val newPosition = (duration * dragProgress).toLong()
                                        playerViewModel.seekTo(newPosition)
                                        isDragging = false
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (audioUrl != null && !isLoading && duration > 0) {
                                    val clickProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                    val newPosition = (duration * clickProgress).toLong()
                                    playerViewModel.seekTo(newPosition)
                                }
                            }
                        }
                ) {
                    // Barra de progreso
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(displayProgress)
                            .background(
                                Color(0xFF4ECDC4),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
                
                // Indicador circular más visible
                if (audioUrl != null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                Color(0xFF4ECDC4),
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .border(
                                2.dp,
                                Color(0xFF1A1A1A),
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .align(Alignment.CenterStart)
                            .offset(x = (displayProgress * (LocalDensity.current.run { 
                                (300.dp - 16.dp).toPx() 
                            }) / LocalDensity.current.density).dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Controles de reproducción sin botones visibles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón anterior (invisible)
                Text(
                    text = "<<",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp
                    ),
                    color = if (audioUrl != null && !isLoading) Color(0xFF4ECDC4) else Color(0xFF666666),
                    modifier = Modifier
                        .clickable(enabled = audioUrl != null && !isLoading) { 
                            /* TODO: Implementar anterior */ 
                        }
                        .padding(12.dp)
                )

                // Botón play/pause principal (invisible)
                Text(
                    text = if (isPlaying) "//" else ">",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 32.sp
                    ),
                    color = if (audioUrl != null && !isLoading) Color(0xFF4ECDC4) else Color(0xFF666666),
                    modifier = Modifier
                        .clickable(enabled = audioUrl != null && !isLoading) {
                            if (isPlaying) {
                                playerViewModel.pausePlayer()
                            } else {
                                playerViewModel.playPlayer()
                            }
                        }
                        .padding(12.dp)
                )

                // Botón siguiente (invisible)
                Text(
                    text = ">>",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp
                    ),
                    color = if (audioUrl != null && !isLoading) Color(0xFF4ECDC4) else Color(0xFF666666),
                    modifier = Modifier
                        .clickable(enabled = audioUrl != null && !isLoading) { 
                            /* TODO: Implementar siguiente */ 
                        }
                        .padding(12.dp)
                )
            }

            // Mostrar error si existe
            error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ERR: ${it.take(60)}${if (it.length > 60) "..." else ""}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFFF6B6B)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Loading indicator
            if (isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFFFD93D),
                    trackColor = Color(0xFF2C2C2C),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 200)
@Composable
fun FloatingMusicControlsPreview() {
    TerminalTheme {
        // Mock data for preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Simulación de controles flotantes
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Barra de progreso mock
                    LinearProgressIndicator(
                        progress = 0.3f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "01:23",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "04:56",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Controles mock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("<<", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("||", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(">>", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
