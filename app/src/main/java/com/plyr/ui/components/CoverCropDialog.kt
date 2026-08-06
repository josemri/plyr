package com.plyr.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plyr.service.CoverCropMath
import com.plyr.service.CoverCropState
import com.plyr.service.CoverImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * CoverCropDialog - Editor de recorte cuadrado de portadas (estilo Spotify).
 *
 * Muestra la imagen elegida dentro de un marco cuadrado; se puede hacer
 * pinch-zoom y arrastrar para encuadrar. Al confirmar se recorta el cuadrado
 * visible y se entrega como Bitmap vía [onConfirm].
 */
@Composable
fun CoverCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                CoverImageManager.decode(context, uri)
            } catch (e: Exception) {
                loadError = e.message
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            // Cargando o error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (loadError != null) "$ load_error" else "$ loading...",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = if (loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            return@Dialog
        }

        var zoom by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Título
                Text(
                    text = "> edit_cover",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(16.dp)
                )

                // Zona de recorte (tamaño del marco cuadrado calculado aquí)
                var squarePx by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val containerW = maxWidth
                        val containerH = maxHeight
                        val squareDp = minOf(containerW, containerH) * 0.92f
                        squarePx = with(density) { squareDp.toPx() }

                        val imageW = currentBitmap.width.toFloat()
                        val imageH = currentBitmap.height.toFloat()
                        val scale = CoverCropMath.baseScale(squarePx, imageW, imageH) * zoom
                        val scaledW = imageW * scale
                        val scaledH = imageH * scale
                        val scrim = Color.Black.copy(alpha = 0.75f)
                        val hScrim = (containerH - squareDp) / 2f
                        val vScrim = (containerW - squareDp) / 2f

                        // Imagen con gestos (pinch-zoom + arrastre)
                        Box(
                            modifier = Modifier
                                .size(squareDp)
                                .align(Alignment.Center)
                                .clipToBounds()
                                .background(Color.DarkGray)
                                .pointerInput(imageW, imageH, squarePx) {
                                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                        val newZoom = (zoom * gestureZoom).coerceAtLeast(1f)
                                        val base = CoverCropMath.baseScale(squarePx, imageW, imageH)
                                        val scaleBefore = base * zoom
                                        val scaleAfter = base * newZoom
                                        val halfSquare = squarePx / 2f
                                        // Punto de la imagen original bajo el centroide del gesto
                                        val imgFX = (centroid.x - (halfSquare + offsetX)) / scaleBefore + imageW / 2f
                                        val imgFY = (centroid.y - (halfSquare + offsetY)) / scaleBefore + imageH / 2f
                                        // Ajustar zoom y desplazamiento para que ese punto siga
                                        // bajo los dedos (centroide + pan) tras el cambio de escala
                                        zoom = newZoom
                                        offsetX = (centroid.x + pan.x) - halfSquare - (imgFX - imageW / 2f) * scaleAfter
                                        offsetY = (centroid.y + pan.y) - halfSquare - (imgFY - imageH / 2f) * scaleAfter
                                        val clamped = CoverCropMath.clampState(
                                            CoverCropState(zoom, offsetX, offsetY),
                                            squarePx, imageW, imageH
                                        )
                                        zoom = clamped.zoom
                                        offsetX = clamped.offsetX
                                        offsetY = clamped.offsetY
                                    }
                                }
                        ) {
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (((squarePx - scaledW) / 2f) + offsetX).roundToInt(),
                                            (((squarePx - scaledH) / 2f) + offsetY).roundToInt()
                                        )
                                    }
                                    .size(with(density) { scaledW.toDp() }, with(density) { scaledH.toDp() })
                            )
                        }

                        // Scrims alrededor del marco cuadrado
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(hScrim)
                                .align(Alignment.TopCenter)
                                .background(scrim)
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(hScrim)
                                .align(Alignment.BottomCenter)
                                .background(scrim)
                        )
                        Box(
                            Modifier
                                .width(vScrim)
                                .height(squareDp)
                                .align(Alignment.CenterStart)
                                .background(scrim)
                        )
                        Box(
                            Modifier
                                .width(vScrim)
                                .height(squareDp)
                                .align(Alignment.CenterEnd)
                                .background(scrim)
                        )

                        // Marco del recorte
                        Box(
                            Modifier
                                .size(squareDp)
                                .align(Alignment.Center)
                                .border(2.dp, Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Botones
                ActionButtonsGroup(
                    buttons = listOf(
                        ActionButtonData(
                            text = "<cancel>",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onDismiss
                        ),
                        ActionButtonData(
                            text = "<save>",
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                val rect = CoverCropMath.sourceRect(
                                    square = squarePx,
                                    imageW = currentBitmap.width.toFloat(),
                                    imageH = currentBitmap.height.toFloat(),
                                    state = CoverCropState(zoom, offsetX, offsetY)
                                )
                                val cropped = CoverImageManager.crop(currentBitmap, rect)
                                onConfirm(CoverImageManager.resizeToSquare(cropped))
                            }
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}
