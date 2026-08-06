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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plyr.service.CoverCropMath
import com.plyr.service.CoverCropState
import com.plyr.service.CoverImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CoverCropDialog - Editor de recorte cuadrado de portadas (estilo Spotify).
 *
 * La imagen llena por completo la pantalla de recorte; sobre ella se dibuja un
 * marco cuadrado centrado que delimita la zona que se guardará. Se puede hacer
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

                // Zona de recorte: la imagen llena todo el área disponible
                var viewportPxW by remember { mutableFloatStateOf(0f) }
                var viewportPxH by remember { mutableFloatStateOf(0f) }
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
                        val squareDp = minOf(containerW, containerH) * 0.9f
                        viewportPxW = with(density) { containerW.toPx() }
                        viewportPxH = with(density) { containerH.toPx() }
                        squarePx = with(density) { squareDp.toPx() }

                        val imageW = currentBitmap.width.toFloat()
                        val imageH = currentBitmap.height.toFloat()

                        // Imagen a sangre con gestos (pinch-zoom + arrastre).
                        // El zoom se aplica como transformación gráfica real (graphicsLayer),
                        // así el tamaño de layout queda fijo y la imagen crece de verdad.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .pointerInput(viewportPxW, viewportPxH, squarePx, imageW, imageH) {
                                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                        val next = CoverCropMath.focalZoom(
                                            CoverCropState(zoom, offsetX, offsetY),
                                            centroid.x, centroid.y, pan.x, pan.y, gestureZoom,
                                            viewportPxW, viewportPxH, squarePx, imageW, imageH
                                        )
                                        val clamped = CoverCropMath.clampState(
                                            next, viewportPxW, viewportPxH, squarePx, imageW, imageH
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
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        translationX = offsetX
                                        translationY = offsetY
                                    }
                            )
                        }

                        // Oscurecer suavemente la zona fuera del marco de recorte
                        val scrim = Color.Black.copy(alpha = 0.45f)
                        val hScrim = (containerH - squareDp) / 2f
                        val vScrim = (containerW - squareDp) / 2f
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
                                    viewportW = viewportPxW,
                                    viewportH = viewportPxH,
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
