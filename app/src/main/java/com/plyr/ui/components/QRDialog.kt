package com.plyr.ui.components

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.plyr.model.Group
import com.plyr.network.SupabaseClient
import com.plyr.utils.NfcReader
import com.plyr.utils.NfcTagEvent
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ShareableItem(
    val remoteId: String?,
    val shareUrl: String?,
    val youtubeId: String?,
    val title: String,
    val artist: String,
    val type: ShareType,
    /**
     * Origen de la lista, solo para `ShareType.PLAYLIST`. Sin esto la URL se
     * deducía de los prefijos del id y una lista importada de Spotify se
     * compartía como si fuera un vídeo (B53).
     */
    val playlistOrigin: PlaylistOrigin = PlaylistOrigin.UNKNOWN,
)

enum class ShareType {
    TRACK, PLAYLIST, APP
}

enum class NfcWriteState {
    IDLE,
    WAITING,
    SUCCESS,
    ERROR
}

enum class RecommendationState {
    IDLE,
    ADDING,
    SUCCESS,
    ERROR
}

// Helper functions defined before usage
private fun writeNdefMessageToTag(tag: Tag, message: NdefMessage): Boolean {
    return try {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            if (!ndef.isWritable || ndef.maxSize < message.toByteArray().size) {
                ndef.close()
                return false
            }
            ndef.writeNdefMessage(message)
            ndef.close()
            true
        } else {
            val format = NdefFormatable.get(tag) ?: return false
            format.connect()
            format.format(message)
            format.close()
            true
        }
    } catch (_: Exception) {
        false
    }
}

fun generateQrBitmap(content: String): Bitmap? {
    return try {
        val size = 512
        val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp[x, y] = if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

private data class ShareDialogState(
    val nfcState: MutableState<NfcWriteState>,
    val nfcAdapter: MutableState<NfcAdapter?>,
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

    val dialogState = rememberShareDialogState(context, shareUrl)

    ShareDialogContent(
        context = context,
        shareUrl = shareUrl,
        state = dialogState,
        onDismiss = onDismiss,
    )
}

@Composable
private fun rememberShareDialogState(context: Context, shareUrl: String?): ShareDialogState {
    val nfcState = remember { mutableStateOf(NfcWriteState.IDLE) }
    val nfcAdapter = remember { mutableStateOf<NfcAdapter?>(null) }
    val groups = remember { mutableStateOf<List<Group>>(emptyList()) }
    val recommendationState = remember { mutableStateOf<RecommendationState>(RecommendationState.IDLE) }

    // Load groups on start
    LaunchedEffect(Unit) {
        groups.value = SupabaseClient.getGroups()
    }

    // Procesar el tag cuando se detecte
    ShareDialogTagProcessor(shareUrl, nfcState)

    // Inicializar NFC adapter
    LaunchedEffect(Unit) {
        nfcAdapter.value = NfcAdapter.getDefaultAdapter(context)
    }

    ShareDialogForegroundDispatch(context, nfcState.value, nfcAdapter.value)

    // Resetear estado después de éxito/error
    LaunchedEffect(nfcState.value) {
        if (nfcState.value == NfcWriteState.SUCCESS || nfcState.value == NfcWriteState.ERROR) {
            delay(2000)
            nfcState.value = NfcWriteState.IDLE
        }
    }

    // Resetear estado de recomendación después de éxito/error
    LaunchedEffect(recommendationState.value) {
        if (recommendationState.value == RecommendationState.SUCCESS || recommendationState.value == RecommendationState.ERROR) {
            delay(2000)
            recommendationState.value = RecommendationState.IDLE
        }
    }

    return ShareDialogState(nfcState, nfcAdapter, groups, recommendationState)
}

// Procesar el tag cuando se detecte
@Composable
private fun ShareDialogTagProcessor(
    shareUrl: String?,
    nfcState: MutableState<NfcWriteState>,
) {
    val detectedTag by NfcTagEvent.detectedTag.collectAsState()
    LaunchedEffect(detectedTag) {
        val tag = detectedTag
        if (tag != null && nfcState.value == NfcWriteState.WAITING && shareUrl != null) {
            val fullUrl = if (!shareUrl.startsWith("http://") && !shareUrl.startsWith("https://")) {
                "https://$shareUrl"
            } else {
                shareUrl
            }
            val message = NdefMessage(arrayOf(NdefRecord.createUri(fullUrl)))
            val success = writeNdefMessageToTag(tag, message)
            nfcState.value = if (success) NfcWriteState.SUCCESS else NfcWriteState.ERROR
            NfcTagEvent.clear()
        }
    }
}

// Manejar el foreground dispatch para NFC
// `nfcAdapter` es clave también: se asigna en LaunchedEffect(Unit), así que
// si solo dependiera del estado, el primer frame (adapter == null) dejaría
// el dispatch sin activar nunca (B19).
@Composable
private fun ShareDialogForegroundDispatch(
    context: Context,
    nfcState: NfcWriteState,
    nfcAdapter: NfcAdapter?,
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, nfcState, nfcAdapter) {
        val activity = context as? Activity
        val adapter = nfcAdapter

        if (activity != null && adapter != null && nfcState == NfcWriteState.WAITING) {
            // Detener NfcReader para evitar conflictos de foreground dispatch
            NfcReader.stopReading(activity)

            val intent = Intent(context, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)
            try {
                adapter.enableForegroundDispatch(activity, pendingIntent, null, null)
            } catch (_: Exception) {}
        }

        onDispose {
            if (activity != null && adapter != null) {
                try {
                    adapter.disableForegroundDispatch(activity)
                } catch (_: Exception) {}
                // Reactivar NfcReader cuando salimos del modo escritura
                NfcReader.startReading(activity)
            }
        }
    }
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
                        ShareLinksRow(context, shareUrl, state)
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
private fun ShareLinksRow(context: Context, shareUrl: String, state: ShareDialogState) {
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

    Spacer(modifier = Modifier.width(16.dp))

    NfcButton(
        state = state.nfcState.value,
        onToggle = {
            state.nfcState.value = nextNfcState(state.nfcState.value, state.nfcAdapter.value)
        }
    )
}

private fun nextNfcState(current: NfcWriteState, adapter: NfcAdapter?): NfcWriteState {
    return when (current) {
        NfcWriteState.IDLE -> if (adapter == null || !adapter.isEnabled) {
            NfcWriteState.ERROR
        } else {
            NfcWriteState.WAITING
        }
        NfcWriteState.WAITING -> NfcWriteState.IDLE
        else -> current
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
        val nickname = com.plyr.utils.Config.getUserNickname(context)
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
fun NfcButton(
    state: NfcWriteState,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    var frame by remember { mutableIntStateOf(0) }

    // Activar/desactivar modo escritura global cuando cambia el estado
    LaunchedEffect(state) {
        NfcTagEvent.setWriteMode(state == NfcWriteState.WAITING)
    }

    // Asegurar que se desactiva el modo escritura cuando el componente se desmonta
    DisposableEffect(Unit) {
        onDispose {
            NfcTagEvent.setWriteMode(false)
        }
    }

    // Un solo Int como estado de animación: antes se mutaba una
    // SnapshotStateList por frame, lo que invalidaba el texto hasta 5 veces por
    // segundo y además depended de un delay fijo (resto de B20). Ahora el frame
    // lo marca el vsync y el dibujo sale de una función pura.
    LaunchedEffect(state) {
        frame = 0
        if (state == NfcWriteState.WAITING) {
            while (true) {
                withFrameNanos { }
                frame = (frame + 1) % (NfcPulse.FRAME_CYCLE)
            }
        }
    }

    val displayText = when (state) {
        NfcWriteState.IDLE, NfcWriteState.SUCCESS, NfcWriteState.ERROR -> Translations.get(context, "btn_nfc")
        NfcWriteState.WAITING -> NfcPulse.textAt(frame)
    }

    val textColor = when (state) {
        NfcWriteState.IDLE -> MaterialTheme.colorScheme.secondary
        NfcWriteState.WAITING -> MaterialTheme.colorScheme.primary
        NfcWriteState.SUCCESS -> Color(0xFF4CAF50)
        NfcWriteState.ERROR -> Color(0xFFFF5252)
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = textColor
        ),
        modifier = Modifier
            .clickable(enabled = state != NfcWriteState.SUCCESS && state != NfcWriteState.ERROR) { onToggle() }
            .padding(8.dp)
    )
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
