package com.plyr.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    delayMillis: Int = 1000,
    velocity: Float = 30f
) {
    val density = LocalDensity.current
    var textWidth by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }

    val shouldMarquee = textWidth > containerWidth && containerWidth > 0

    val animationSpec = infiniteRepeatable<Float>(
        animation = tween(
            durationMillis = if (shouldMarquee) ((textWidth - containerWidth) / velocity * 1000).toInt() else 0,
            easing = LinearEasing
        ),
        repeatMode = RepeatMode.Restart
    )

    val animatedOffset by animateFloatAsState(
        targetValue = if (shouldMarquee) -(textWidth - containerWidth).toFloat() else 0f,
        animationSpec = animationSpec,
        label = "marquee"
    )

    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(shouldMarquee) {
        if (shouldMarquee) {
            delay(delayMillis.toLong())
            startAnimation = true
        } else {
            startAnimation = false
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidth = size.width
            }
    ) {
        BasicText(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .onSizeChanged { size ->
                    textWidth = size.width
                }
                .offset {
                    IntOffset(
                        x = if (startAnimation) animatedOffset.toInt() else 0,
                        y = 0
                    )
                }
        )
    }
}
