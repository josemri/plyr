package com.plyr.ui.components

import android.content.Context
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.plyr.service.CoverCropGeometry
import com.plyr.service.CoverCropGesture
import com.plyr.service.CoverCropMath
import com.plyr.service.CoverCropState
import com.plyr.service.CoverImageManager
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CoverCropDialog - Editor de recorte cuadrado de portadas.
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
                loadError = e.message ?: "error"
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
            CoverCropLoading(context, loadError)
        } else {
            CoverCropEditor(currentBitmap, onDismiss, onConfirm)
        }
    }
}

@Composable
private fun CoverCropLoading(context: Context, loadError: String?) {
    // Cargando o error
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = loadError ?: Translations.get(context, "loading"),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = if (loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

private data class CoverCropEditorState(
    val zoom: MutableState<Float>,
    val offsetX: MutableState<Float>,
    val offsetY: MutableState<Float>,
    val viewportPxW: MutableState<Float>,
    val viewportPxH: MutableState<Float>,
    val squarePx: MutableState<Float>,
)

@Composable
private fun CoverCropEditor(
    currentBitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state = remember {
        CoverCropEditorState(
            zoom = mutableFloatStateOf(1f),
            offsetX = mutableFloatStateOf(0f),
            offsetY = mutableFloatStateOf(0f),
            viewportPxW = mutableFloatStateOf(0f),
            viewportPxH = mutableFloatStateOf(0f),
            squarePx = mutableFloatStateOf(0f),
        )
    }

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
            CoverCropArea(currentBitmap, state)

            // Botones
            CoverCropButtons(currentBitmap, state, scope, onDismiss, onConfirm)
        }
    }
}

@Composable
private fun ColumnScope.CoverCropArea(currentBitmap: Bitmap, state: CoverCropEditorState) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val containerW = maxWidth
            val containerH = maxHeight
            val squareDp = minOf(containerW, containerH) * 0.9f
            state.viewportPxW.value = with(density) { containerW.toPx() }
            state.viewportPxH.value = with(density) { containerH.toPx() }
            state.squarePx.value = with(density) { squareDp.toPx() }

            val imageW = currentBitmap.width.toFloat()
            val imageH = currentBitmap.height.toFloat()

            CoverCropGestures(currentBitmap, state, imageW, imageH)

            // Oscurecer suavemente la zona fuera del marco de recorte
            val scrim = Color.Black.copy(alpha = 0.45f)
            val hScrim = (containerH - squareDp) / 2f
            val vScrim = (containerW - squareDp) / 2f
            CoverCropScrims(hScrim, vScrim, squareDp, scrim)

            // Marco del recorte
            Box(
                Modifier
                    .size(squareDp)
                    .align(Alignment.Center)
                    .border(2.dp, Color.White, RoundedCornerShape(2.dp))
            )
        }
    }
}

// Imagen a sangre con gestos (pinch-zoom + arrastre).
// El zoom se aplica como transformación gráfica real (graphicsLayer),
// así el tamaño de layout queda fijo y la imagen crece de verdad.
@Composable
private fun CoverCropGestures(
    currentBitmap: Bitmap,
    state: CoverCropEditorState,
    imageW: Float,
    imageH: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(state.viewportPxW.value, state.viewportPxH.value, state.squarePx.value, imageW, imageH) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    val geometry = CoverCropGeometry(state.viewportPxW.value, state.viewportPxH.value, state.squarePx.value, imageW, imageH)
                    val next = CoverCropMath.focalZoom(
                        CoverCropState(state.zoom.value, state.offsetX.value, state.offsetY.value),
                        CoverCropGesture(centroid.x, centroid.y, pan.x, pan.y, gestureZoom),
                        geometry
                    )
                    val clamped = CoverCropMath.clampState(next, geometry)
                    state.zoom.value = clamped.zoom
                    state.offsetX.value = clamped.offsetX
                    state.offsetY.value = clamped.offsetY
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
                    scaleX = state.zoom.value
                    scaleY = state.zoom.value
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = state.offsetX.value
                    translationY = state.offsetY.value
                }
        )
    }
}

@Composable
private fun BoxScope.CoverCropScrims(hScrim: Dp, vScrim: Dp, squareDp: Dp, scrim: Color) {
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
}

@Composable
private fun CoverCropButtons(
    currentBitmap: Bitmap,
    state: CoverCropEditorState,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
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
                    saveCroppedBitmap(currentBitmap, state, scope, onConfirm)
                }
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

private fun saveCroppedBitmap(
    currentBitmap: Bitmap,
    state: CoverCropEditorState,
    scope: CoroutineScope,
    onConfirm: (Bitmap) -> Unit,
) {
    val rect = CoverCropMath.sourceRect(
        CoverCropGeometry(
            viewportW = state.viewportPxW.value,
            viewportH = state.viewportPxH.value,
            square = state.squarePx.value,
            imageW = currentBitmap.width.toFloat(),
            imageH = currentBitmap.height.toFloat()
        ),
        CoverCropState(state.zoom.value, state.offsetX.value, state.offsetY.value)
    )
    // Decodificar/escalar/recortar un bitmap a tamaño completo
    // fuera del hilo principal: antes bloqueaba cada frame del clic.
    scope.launch {
        val cropped = withContext(Dispatchers.Default) {
            CoverImageManager.resizeToSquare(
                CoverImageManager.crop(currentBitmap, rect)
            )
        }
        onConfirm(cropped)
    }
}
