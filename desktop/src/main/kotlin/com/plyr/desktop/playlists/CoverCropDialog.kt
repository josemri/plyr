package com.plyr.desktop.playlists

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.service.CoverCropGeometry
import com.plyr.service.CoverCropGesture
import com.plyr.service.CoverCropMath
import com.plyr.service.CoverCropState
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Fracción del viewport que ocupa el marco cuadrado de recorte. */
private const val FRAME_FRACTION = 0.9f

/** Tamaño de la ventana del editor; en escritorio el diálogo se ajusta al contenido. */
private val DIALOG_WIDTH = 720.dp
private val DIALOG_HEIGHT = 640.dp

/**
 * Editor de recorte cuadrado de portadas (M8), equivalente al `CoverCropDialog`
 * de Android pero sobre `java.awt.image`/Skia.
 *
 * La imagen llena el viewport con un "center-crop"; se puede arrastrar para
 * encuadrar y hacer zoom (rueda/pinch y los botones `-`/`+`). Al confirmar se
 * recorta el cuadrado visible con la misma matemática pura que Android
 * ([CoverCropMath]) y se entrega como [BufferedImage].
 */
@Composable
fun CoverCropDialog(
    source: BufferedImage,
    onDismiss: () -> Unit,
    onConfirm: (BufferedImage) -> Unit,
) {
    val image = remember(source) { source.toImageBitmap() }
    val imageW = source.width.toFloat()
    val imageH = source.height.toFloat()

    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var viewportW by remember { mutableFloatStateOf(0f) }
    var viewportH by remember { mutableFloatStateOf(0f) }
    val square = minOf(viewportW, viewportH) * FRAME_FRACTION

    fun geometry() = CoverCropGeometry(viewportW, viewportH, square, imageW, imageH)
    fun currentState() = CoverCropState(zoom, offsetX, offsetY)
    fun applyTransform(next: CoverCropState) {
        val clamped = CoverCropMath.clampState(next, geometry())
        zoom = clamped.zoom
        offsetX = clamped.offsetX
        offsetY = clamped.offsetY
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.size(DIALOG_WIDTH, DIALOG_HEIGHT).background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = "> ${Translations.get("edit_cover")}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.padding(16.dp),
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged {
                            viewportW = it.width.toFloat()
                            viewportH = it.height.toFloat()
                        },
                ) {
                    if (viewportW > 0f && viewportH > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .pointerInput(viewportW, viewportH, square, imageW, imageH) {
                                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                        applyTransform(
                                            CoverCropMath.focalZoom(
                                                currentState(),
                                                CoverCropGesture(centroid.x, centroid.y, pan.x, pan.y, gestureZoom),
                                                geometry(),
                                            )
                                        )
                                    }
                                },
                        ) {
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = offsetX
                                    translationY = offsetY
                                },
                            )
                        }
                        CoverCropScrims(viewportW, viewportH, square)
                        Box(
                            Modifier
                                .size(with(LocalDensity.current) { square.toDp() })
                                .align(Alignment.Center)
                                .border(2.dp, Color.White, RoundedCornerShape(2.dp)),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(ActionButtonData(text = "<${Translations.get("cancel")}>", onClick = onDismiss))
                    ActionButton(
                        data = ActionButtonData(text = "[ - ]", onClick = {
                            applyTransform(zoomAround(currentState(), 1f / 1.25f, geometry()))
                        }),
                    )
                    ActionButton(
                        data = ActionButtonData(text = "[ + ]", onClick = {
                            applyTransform(zoomAround(currentState(), 1.25f, geometry()))
                        }),
                    )
                    ActionButton(
                        data = ActionButtonData(
                            text = "<${Translations.get("save")}>",
                            onClick = { onConfirm(crop(source, geometry(), currentState())) },
                        ),
                        modifier = Modifier.testTag("cover_crop_save"),
                    )
                }
            }
        }
    }
}

/** Zoom alrededor del centro del viewport (botones `-`/`+`). */
private fun zoomAround(state: CoverCropState, factor: Float, geometry: CoverCropGeometry): CoverCropState =
    CoverCropMath.focalZoom(
        state,
        CoverCropGesture(geometry.viewportW / 2f, geometry.viewportH / 2f, 0f, 0f, factor),
        geometry,
    )

/** Recorta el cuadrado visible de [source] según el estado del editor. */
private fun crop(source: BufferedImage, geometry: CoverCropGeometry, state: CoverCropState): BufferedImage {
    val rect = CoverCropMath.sourceRect(geometry, state)
    val left = rect.left.roundToInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val top = rect.top.roundToInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val side = rect.width().roundToInt()
        .coerceAtMost(source.width - left)
        .coerceAtMost(source.height - top)
        .coerceAtLeast(1)
    return source.getSubimage(left, top, side, side)
}

/** Oscurece suavemente la zona del viewport que queda fuera del marco. */
@Composable
private fun BoxScope.CoverCropScrims(viewportW: Float, viewportH: Float, squarePx: Float) {
    val density = LocalDensity.current
    val square = with(density) { squarePx.toDp() }
    val hScrim = with(density) { ((viewportH - squarePx) / 2f).coerceAtLeast(0f).toDp() }
    val vScrim = with(density) { ((viewportW - squarePx) / 2f).coerceAtLeast(0f).toDp() }
    CoverCropScrim(Modifier.fillMaxWidth().height(hScrim).align(Alignment.TopCenter))
    CoverCropScrim(Modifier.fillMaxWidth().height(hScrim).align(Alignment.BottomCenter))
    CoverCropScrim(Modifier.width(vScrim).height(square).align(Alignment.CenterStart))
    CoverCropScrim(Modifier.width(vScrim).height(square).align(Alignment.CenterEnd))
}

@Composable
private fun CoverCropScrim(modifier: Modifier) {
    Box(modifier.background(Color.Black.copy(alpha = 0.45f)))
}

/** Codifica la imagen a PNG y la decodifica a [ImageBitmap] (compatible Skia). */
private fun BufferedImage.toImageBitmap(): ImageBitmap {
    val output = ByteArrayOutputStream()
    ImageIO.write(this, "png", output)
    return output.toByteArray().decodeToImageBitmap()
}
