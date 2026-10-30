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
    val pixels = QrCodec.toArgb(matrix)
    bmp.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    return bmp
}

private data class ShareDialogState(
    val groups: MutableState<List<Group>>,
    val recommendationState: MutableState<RecommendationState>,
)

@Composable
fun ShareDialog(item: ShareableItem, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val shareUrl = ShareUrlPolicy.resolve(
        type = item.type,
        shareUrl = item.shareUrl,
        youtubeId = item.youtubeId,
        playlistOrigin = item.playlistOrigin,
    )

    val dialogState = rememberShareDialogState(context)

    ShareDialogContent(
        context = context,
        shareUrl = shareUrl,
        state = dialogState,
        onDismiss = onDismiss,
    )
}

@Composable
private fun rememberShareDialogState(context: Context): ShareDialogState {
    val groups = remember { mutableStateOf<List<Group>>(emptyList()) }
    val recommendationState = remember { mutableStateOf<RecommendationState>(RecommendationState.IDLE) }

    LaunchedEffect(Unit) {
        groups.value = SupabaseClient.getGroups()
    }

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
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "<${Translations.get(context, "share")}>",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center
                )

                if (shareUrl != null) {
                    val qrBitmap = remember(shareUrl) { generateQrBitmap(shareUrl) }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = Translations.get(context, "share_qr"),
                            modifier = Modifier.size(200.dp)
                        )
                    }

                    ShareLinksRow(context, shareUrl)
                    RecommendRow(shareUrl, state)
                } else {
                    Text(
                        text = Translations.get(context, "no_url_to_share"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ShareLinksRow(context: Context, shareUrl: String) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "<${Translations.get(context, "btn_share")}>",
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
