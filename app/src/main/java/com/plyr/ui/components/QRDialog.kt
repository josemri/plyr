package com.plyr.ui.components

import android.app.Activity
import android.app.PendingIntent
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.plyr.utils.NfcReader
import com.plyr.utils.NfcTagEvent
import com.plyr.utils.Translations
import kotlinx.coroutines.delay

data class ShareableItem(
    val spotifyId: String?,
    val spotifyUrl: String?,
    val youtubeId: String?,
    val title: String,
    val artist: String,
    val type: ShareType
)

enum class ShareType {
    TRACK, PLAYLIST, ALBUM, ARTIST, APP
}

enum class NfcWriteState {
    IDLE,
    WAITING,
    SUCCESS,
    ERROR
}

@Composable
fun ShareDialog(item: ShareableItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val shareUrl = item.spotifyUrl ?: when {
        item.type == ShareType.APP -> "https://github.com/josemri/plyr/releases/download/latest/plyr.apk"
        item.spotifyId != null -> "https://open.spotify.com/${item.type.name.lowercase()}/${item.spotifyId}"
        item.youtubeId != null -> "https://www.youtube.com/watch?v=${item.youtubeId}"
        else -> null
    }

    var nfcState by remember { mutableStateOf(NfcWriteState.IDLE) }
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }

    val detectedTag by NfcTagEvent.detectedTag.collectAsState()

    // Procesar el tag cuando se detecte
    LaunchedEffect(detectedTag) {
        val tag = detectedTag
        if (tag != null && nfcState == NfcWriteState.WAITING && shareUrl != null) {
            val fullUrl = if (!shareUrl.startsWith("http://") && !shareUrl.startsWith("https://")) {
                "https://$shareUrl"
            } else {
                shareUrl
            }
            val message = NdefMessage(arrayOf(NdefRecord.createUri(fullUrl)))
            val success = writeNdefMessageToTag(tag, message)
            nfcState = if (success) NfcWriteState.SUCCESS else NfcWriteState.ERROR
            NfcTagEvent.clear()
        }
    }

    // Inicializar NFC adapter
    LaunchedEffect(Unit) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context)
    }

    // Manejar el foreground dispatch para NFC
    DisposableEffect(lifecycleOwner, nfcState) {
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

    // Resetear estado después de éxito/error
    LaunchedEffect(nfcState) {
        if (nfcState == NfcWriteState.SUCCESS || nfcState == NfcWriteState.ERROR) {
            delay(2000)
            nfcState = NfcWriteState.IDLE
        }
    }

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
                    val qrBitmap = generateQrBitmap(shareUrl)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (shareUrl != null) {
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
                                    val chooserIntent = Intent.createChooser(sendIntent, "Compartir via")
                                    chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(chooserIntent)
                                }
                                .padding(8.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        NfcButton(
                            state = nfcState,
                            onToggle = {
                                when (nfcState) {
                                    NfcWriteState.IDLE -> {
                                        nfcState = if (nfcAdapter == null || nfcAdapter?.isEnabled == false) {
                                            NfcWriteState.ERROR
                                        } else {
                                            NfcWriteState.WAITING
                                        }
                                    }
                                    NfcWriteState.WAITING -> nfcState = NfcWriteState.IDLE
                                    else -> {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NfcButton(
    state: NfcWriteState,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val rings = remember { mutableStateListOf<Int>() }
    var frameCounter by remember { mutableIntStateOf(0) }
    val width = 11
    val center = width / 2

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

    LaunchedEffect(state) {
        if (state == NfcWriteState.WAITING) {
            rings.clear()
            frameCounter = 0
            while (state == NfcWriteState.WAITING) {
                frameCounter++
                if (frameCounter % 3 == 0) rings.add(0)
                for (i in rings.indices) rings[i] = rings[i] + 1
                rings.removeAll { r -> (center - r < 0) && (center + r > width - 1) }
                delay(200L)
            }
            rings.clear()
        } else {
            rings.clear()
        }
    }

    val displayText = when (state) {
        NfcWriteState.IDLE, NfcWriteState.SUCCESS, NfcWriteState.ERROR -> Translations.get(context, "btn_nfc")
        NfcWriteState.WAITING -> {
            val chars = CharArray(width) { ' ' }
            for (r in rings) {
                val left = center - r
                val right = center + r
                if (left == right && left in 0 until width) {
                    chars[left] = '•'
                } else {
                    if (left in 0 until width) chars[left] = '('
                    if (right in 0 until width) chars[right] = ')'
                }
            }
            String(chars)
        }
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
