package com.plyr.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Handle back button
    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "$ plyr_queue",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Contenido de la cola
        if (playerViewModel != null) {
            val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
            val currentQueue = queueState.queue

            if (currentQueue.isNotEmpty()) {
                // Header de la cola
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current queue [${currentQueue.size}]",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = Color(0xFFFFD93D)
                        )
                    )

                    // Botón para limpiar la cola
                    TextButton(
                        onClick = {
                            playerViewModel.clearQueue()
                            Log.d("QueueScreen", "Cola limpiada por el usuario")
                        }
                    ) {
                        Text(
                            text = "clear",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Lista de tracks en la cola
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        count = currentQueue.size,
                        key = { index -> currentQueue[index].id }
                    ) { index ->
                        val track = currentQueue[index]
                        val isCurrentTrack = queueState.currentIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        if (queueState.currentIndex != index) {
                                            playerViewModel.playQueueFromIndex(index)
                                        } else {
                                            playerViewModel.exoPlayer?.play()
                                        }
                                    }
                                    Log.d("QueueScreen", "Iniciando cola desde índice: $index")
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Indicador de posición y estado
                            Text(
                                text = if (isCurrentTrack) "♪ " else "${index + 1}. ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = if (isCurrentTrack) Color(0xFF4ECDC4) else Color(0xFF95A5A6)
                                ),
                                modifier = Modifier.width(32.dp)
                            )

                            // Nombre del track
                            MarqueeText(
                                text = track.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = if (isCurrentTrack) Color(0xFFE0E0E0) else Color(0xFFBDC3C7)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // Botón para remover de la cola
                            TextButton(
                                onClick = {
                                    playerViewModel.removeFromQueue(index)
                                    Log.d("QueueScreen", "Track removido de la cola en índice: $index")
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp,
                                        color = Color(0xFF95A5A6)
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Estado vacío
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Queue is empty",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
                    )

                    Text(
                        text = "Add tracks from search to start playing",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF7F8C8D)
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            // PlayerViewModel no disponible
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Player not available",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    )
                )
            }
        }
    }
}

@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
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
                delayMillis = 1500
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_animation"
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size -> containerWidth = size.width }
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { size -> textWidth = size.width }
                .offset(x = with(density) { animatedOffset.toDp() })
        )
    }
}
