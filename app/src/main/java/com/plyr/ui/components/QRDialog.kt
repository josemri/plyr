package com.plyr.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.plyr.model.Group
import com.plyr.network.SupabaseClient
import com.plyr.utils.QrCodec
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class RecommendationState {
    IDLE,
    ADDING,
    SUCCESS,
    ERROR
}

/**
 * Genera el QR de [content] como `Bitmap` Android reutilizando el codec
 * compartido [QrCodec] (M11). Devuelve `null` si el contenido no se puede
 * codificar.
 */
fun generateQrBitmap(content: String): Bitmap? {
    val matrix = QrCodec.generate(content) ?: return null
    val bmp = createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    bmp.setPixels(QrCodec.toArgb(matrix), 0, matrix.width, 0, 0, matrix.width, matrix.height)
    return bmp
}

private data class ShareDialogState(
    val groups: MutableState<List<Group>>,
    val recommendationState: MutableState<RecommendationState>,
)

@Composable
fun ShareDialog(item: ShareableItem, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // La URL la decide ShareUrlPolicy, no quien la construyó: antes se cogía
    // `shareUrl` por precedencia y eso descartaba el `youtubeVideoId` correcto
    // que ya viajaba (B52).
    val shareUrl = ShareUrlPolicy.resolve(
        type = item.type,
        shareUrl = item.shareUrl,
        youtubeId = item.youtubeId,
        playlistOrigin = item.playlistOrigin,
    )

    val dialogState = rememberShareDialogState()

    ShareDialogContent(
        context = context,
        shareUrl = shareUrl,
        state = dialogState,
        onDismiss = onDismiss,
    )
}

@Composable
private fun rememberShareDialogState(): ShareDialogState {
    val groups = remember { mutableStateOf<List<Group>>(emptyList()) }
    val recommendationState = remember { mutableStateOf<RecommendationState>(RecommendationState.IDLE) }

    // Load groups on start
    LaunchedEffect(Unit) {
        groups.value = SupabaseClient.getGroups()
    }

    // Resetear estado de recomendación después de éxito/error
    LaunchedEffect(recommendationState.value) {
        if (recommendationState.value == RecommendationState.SUCCESS || recommendationState.value == RecommendationState.ERROR) {
            delay(2000)
            recommendationState.value = RecommendationState.IDLE
        }
    }

    return ShareDialogState(groups, recommendationState)
}

@Composable
private fun ShareDialogContent(
    context: Context,
    shareUrl: String?,
    state: ShareDialogState,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (shareUrl != null) {
                    ShareQrSection(shareUrl)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (shareUrl == null) {
                        NoShareUrlSection(context, onDismiss)
                    }
                    if (shareUrl != null) {
                        ShareLinksRow(context, shareUrl)
                    }
                }

                // Recommend button in a new row
                if (shareUrl != null) {
                    RecommendRow(shareUrl, state)
                }
            }
        }
    }
}

@Composable
private fun ShareQrSection(shareUrl: String) {
    val qrBitmap = remember(shareUrl) { generateQrBitmap(shareUrl) }
    if (qrBitmap != null) {
        Card(
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Text(
            text = "Error generando QR",
            color = Color(0xFFFF6B6B),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

// Sin URL no hay QR, ni <share>, ni NFC, ni <recomendar>. Antes
// todo eso se ocultaba y lo que quedaba era un `Card` con padding
// y nada dentro: un diálogo en blanco sin explicación (B54).
@Composable
private fun NoShareUrlSection(context: Context, onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = Translations.get(context, "no_share_url"),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            ),
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onDismiss) {
            Text(
                text = Translations.get(context, "close"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun ShareLinksRow(context: Context, shareUrl: String) {
    Text(
        text = Translations.get(context, "btn_share"),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = Color(0xFFFF6B9D)
        ),
        modifier = Modifier
            .clickable {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                    type = "text/plain"
                }
                val chooserIntent = Intent.createChooser(sendIntent, Translations.get(context, "share_via"))
                chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(chooserIntent)
            }
            .padding(8.dp)
    )

}

@Composable
private fun RecommendRow(shareUrl: String, state: ShareDialogState) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecommendButton(
            state = state.recommendationState.value,
            onClick = {
                requestRecommendation(
                    context = context,
                    haptic = haptic,
                    scope = scope,
                    shareUrl = shareUrl,
                    state = state,
                )
            }
        )
    }
}

private fun requestRecommendation(
    context: Context,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    shareUrl: String,
    state: ShareDialogState,
) {
    if (state.recommendationState.value != RecommendationState.IDLE) return
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    state.recommendationState.value = RecommendationState.ADDING
    scope.launch {
        val nickname = com.plyr.utils.FeedConfig.getUserNickname(context)
        val generalGroup = state.groups.value.find { it.groupType == "general" }
        if (!nickname.isNullOrBlank() && generalGroup != null) {
            val result = SupabaseClient.createRecommendation(
                groupId = generalGroup.id,
                nickname = nickname,
                url = shareUrl,
                comment = null
            )
            state.recommendationState.value = if (result != null) {
                RecommendationState.SUCCESS
            } else {
                RecommendationState.ERROR
            }
        } else {
            state.recommendationState.value = RecommendationState.ERROR
        }
    }
}

@Composable
fun RecommendButton(
    state: RecommendationState,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val text = when (state) {
        RecommendationState.IDLE -> "<${Translations.get(context, "add_recommendation")}>"
        RecommendationState.ADDING -> "..."
        RecommendationState.SUCCESS -> "✓"
        RecommendationState.ERROR -> "✗"
    }

    val color = when (state) {
        RecommendationState.IDLE -> MaterialTheme.colorScheme.secondary
        RecommendationState.ADDING -> MaterialTheme.colorScheme.primary
        RecommendationState.SUCCESS -> Color(0xFF4CAF50)
        RecommendationState.ERROR -> Color(0xFFFF5252)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = color
        ),
        modifier = Modifier
            .clickable(enabled = state == RecommendationState.IDLE) { onClick() }
            .padding(8.dp)
    )
}
