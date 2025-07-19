package com.plyr.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.plyr.utils.formatTime
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import com.plyr.ui.theme.TerminalTheme

@Composable
fun ExoPlyrScreen(
    player: ExoPlayer,
    onBack: () -> Unit = {}
) {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }

    var userInput by remember { mutableStateOf("") }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            duration = player.duration.coerceAtLeast(1L)
            if (!isUserSeeking) {
                position = player.currentPosition
                sliderPosition = position / duration.toFloat()
            }
            delay(500)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface  // Cambiado de background a surface para diferenciarlo
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Botón de regreso
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("← Volver", color = MaterialTheme.colorScheme.primary)
            }
        
        Text(
            text = "+-----------------------------+\n|     UR FREE MUSIC PLYR      |\n+-----------------------------+",
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 🎚️ Barra de progreso "ASCII simulada"
        AsciiSlider(
            progress = sliderPosition,
            length = 30,
            onSeek = { newProgress ->
                sliderPosition = newProgress
                player.seekTo((newProgress * duration).toLong())
            }
        )


        Text(
            text = "[${formatTime(position)} / ${formatTime(duration)}]",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = {
                player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
            }) {
                Text(
                    text = "<<",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            TextButton(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Box(modifier = Modifier.size(80.dp, 60.dp)) {
                    Text(
                        text = if (isPlaying) "||" else ">",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        maxLines = 1,
                        modifier = Modifier.fillMaxSize(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            TextButton(onClick = {
                player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration))
            }) {
                Text(
                    text = ">>",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
}

@Preview(showBackground = true)
@Composable
fun PreviewExoPlyrScreen() {
    val context = LocalContext.current
    val player = ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri("asset:///sample.mp3"))
        prepare()
    }
    ExoPlyrScreen(player = player)
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun ExoPlyrScreenPreview() {
    TerminalTheme {
        // Para preview, usamos un composable simplificado
        ExoPlyrScreenMock()
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, name = "Player Terminal Theme")
@Composable
fun ExoPlyrScreenTerminalPreview() {
    TerminalTheme {
        ExoPlyrScreenMock()
    }
}

// Composable simplificado para preview sin dependencias de ExoPlayer
@Composable
private fun ExoPlyrScreenMock() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface  // Mismo cambio para el mock
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center
        ) {
        Text(
            text = "♪ PLYR - Audio Player ♪",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.primary
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "00:45 / 03:20",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { }) {
                Text(
                    "<<", 
                    fontSize = 40.sp, 
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            TextButton(onClick = { }) {
                Text(
                    "||", 
                    fontSize = 40.sp, 
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            TextButton(onClick = { }) {
                Text(
                    ">>", 
                    fontSize = 40.sp, 
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
}