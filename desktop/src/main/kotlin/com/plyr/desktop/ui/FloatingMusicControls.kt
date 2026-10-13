package com.plyr.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.desktop.ui.utils.formatTime
import com.plyr.domain.RepeatMode
import kotlin.math.roundToInt

/**
 * Estado de la barra de controles, ya resuelto para pintarse.
 *
 * En Android se lee del `PlayerViewModel`; en el esqueleto de M5 lo rellena
 * quien lo use. La reproducción real se conecta en M7.
 */
data class PlayerUiState(
    val title: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueIndex: Int? = null,
    val queueSize: Int? = null,
    val repeatMode: String = RepeatMode.OFF,
)

/**
 * Controles flotantes de música, portados de `com.plyr.ui.FloatingMusicControls`.
 *
 * Misma estética terminal (símbolos `//`, `>`, `<<`, `>>`, `^` y el ciclo de
 * repetición `o`/`1`/`*`). Sin reproducción real todavía: los callbacks los
 * cablea M7. Se muestra solo cuando hay título, carga o error, como en Android.
 */
@Composable
fun FloatingMusicControls(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    onPlayPause: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onShowQueue: () -> Unit = {},
) {
    val visible = state.title != null || state.isLoading || state.error != null
    if (!visible) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("floating_controls"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            StatusAndTitleRow(state)
            ProgressBar(state, onSeek)
            PlaybackControls(state, onPlayPause, onPrevious, onNext, onRepeat, onShowQueue)
        }
    }
}

@Composable
private fun StatusAndTitleRow(state: PlayerUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isLoading) {
                Text(
                    text = "$ loading",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    ),
                )
            } else {
                MarqueeText(
                    text = state.title ?: state.error.orEmpty(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (!state.isLoading) {
                Text(
                    text = "${formatTime(state.positionMs)}/${formatTime(state.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                    ),
                )
                if (state.queueIndex != null && state.queueSize != null && state.queueSize > 0) {
                    Text(
                        text = "${state.queueIndex + 1}/${state.queueSize}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs.toFloat() else 0f
    val displayProgress = if (isDragging) dragProgress else progress

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
    ) {
        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag("progress_bar")
                    .pointerInput(state.durationMs) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (state.durationMs > 0) {
                                    isDragging = true
                                    dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                                }
                            },
                            onDrag = { change, _ ->
                                if (state.durationMs > 0) {
                                    dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (isDragging && state.durationMs > 0) {
                                    onSeek((state.durationMs * dragProgress).toLong())
                                    isDragging = false
                                }
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(displayProgress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShowQueue: () -> Unit,
) {
    val enabled = !state.isLoading

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "^",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .offset(y = (-2).dp)
                    .testTag("control_queue")
                    .clickable(enabled = enabled) { onShowQueue() }
                    .padding(6.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackButton(text = "<<", fontSize = 16.sp, isEnabled = enabled, testTag = "control_prev", onClick = onPrevious)
            Spacer(Modifier.width(24.dp))
            PlaybackButton(
                text = if (state.isPlaying) "//" else ">",
                fontSize = 24.sp,
                isEnabled = enabled,
                testTag = "control_play_pause",
                onClick = onPlayPause,
            )
            Spacer(Modifier.width(24.dp))
            PlaybackButton(text = ">>", fontSize = 16.sp, isEnabled = enabled, testTag = "control_next", onClick = onNext)
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            RepeatButton(currentMode = state.repeatMode, isEnabled = enabled, onClick = onRepeat)
        }
    }
}

@Composable
private fun PlaybackButton(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isEnabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
        ),
        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .testTag(testTag)
            .clickable(enabled = isEnabled) { onClick() }
            .padding(6.dp),
    )
}

@Composable
private fun RepeatButton(
    currentMode: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val symbol = when (currentMode) {
        RepeatMode.OFF -> "o"
        RepeatMode.ONE -> "1"
        RepeatMode.ALL -> "*"
        else -> "o"
    }
    val color = when (currentMode) {
        RepeatMode.OFF -> MaterialTheme.colorScheme.outline
        RepeatMode.ONE -> MaterialTheme.colorScheme.tertiary
        RepeatMode.ALL -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Text(
        text = symbol,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        ),
        color = if (isEnabled) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        modifier = modifier
            .testTag("control_repeat")
            .clickable(enabled = isEnabled) { onClick() }
            .padding(4.dp),
    )
}

/**
 * Texto con efecto marquesina para títulos largos. Portado de Android; si el
 * texto cabe en el contenedor, no se anima.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
) {
    var textWidth by remember { mutableIntStateOf(0) }
    var containerWidth by remember { mutableIntStateOf(0) }
    val shouldAnimate = textWidth > containerWidth && containerWidth > 0

    // La animación infinita solo se crea si el texto realmente desborda. Si no,
    // se deja el texto estático: así la composición queda ociosa (importante
    // para los tests de Compose, que esperan a que no haya frames pendientes).
    var offsetPx = 0
    if (shouldAnimate) {
        val infiniteTransition = rememberInfiniteTransition(label = "marquee")
        val animatedOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -(textWidth - containerWidth).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = maxOf(text.length * 100, 3000),
                    easing = LinearEasing,
                    delayMillis = 1500,
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
            ),
            label = "marquee_animation",
        )
        offsetPx = animatedOffset.roundToInt()
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerWidth = it.width },
    ) {
        Text(
            text = text,
            style = style.copy(color = color),
            maxLines = maxLines,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { textWidth = it.width }
                .offset { IntOffset(offsetPx, 0) },
        )
    }
}
