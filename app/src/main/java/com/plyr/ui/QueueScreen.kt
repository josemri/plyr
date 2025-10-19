package com.plyr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import com.plyr.utils.Translations
import com.plyr.ui.components.Titulo
import androidx.compose.ui.platform.LocalContext

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
		Titulo(Translations.get(context, "plyr_queue"))

        // Contenido de la playlist
        if (playerViewModel != null) {
            val currentPlaylist by playerViewModel.currentPlaylist.observeAsState()
            val currentTrackIndex by playerViewModel.currentTrackIndex.observeAsState()

            if (currentPlaylist != null && currentPlaylist!!.isNotEmpty()) {
                // Header de la playlist
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        count = currentPlaylist!!.size,
                        key = { index -> currentPlaylist!![index].id }
                    ) { index ->
                        val track = currentPlaylist!![index]
                        val isCurrentTrack = currentTrackIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        if (currentTrackIndex != index) {
                                            playerViewModel.setCurrentPlaylist(
                                                currentPlaylist!!,
                                                index
                                            )
                                            playerViewModel.loadAudioFromTrack(track)
                                        } else {
                                            playerViewModel.exoPlayer?.play()
                                        }
                                    }
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
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                MarqueeText(
                                    text = track.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = if (isCurrentTrack) Color(0xFFE0E0E0) else Color(0xFFBDC3C7)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (track.artists.isNotEmpty()) {
                                    Text(
                                        text = track.artists,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF95A5A6)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Estado vacío
            Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
				    Text(
                        text = Translations.get(context, "No tracks loaded"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
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
                    text = Translations.get(context, "Player not available"),
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
