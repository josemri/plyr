package com.plyr.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

data class ShareableItem(
    val spotifyId: String?,
    val spotifyUrl: String?,
    val youtubeId: String?,
    val title: String,
    val artist: String,
    val type: ShareType
)

enum class ShareType {
    TRACK, PLAYLIST, ALBUM, ARTIST
}

@Composable
fun ShareDialog(item: ShareableItem, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // URL para compartir y para el QR (la misma URL)
    val shareUrl = item.spotifyUrl ?: when {
        item.spotifyId != null -> "https://open.spotify.com/${item.type.name.lowercase()}/${item.spotifyId}"
        item.youtubeId != null -> "https://www.youtube.com/watch?v=${item.youtubeId}"
        else -> null
    }

    // Debug: Log de la URL generada
    Log.d("ShareDialog", "===========================================")
    Log.d("ShareDialog", "Generando QR con los siguientes datos:")
    Log.d("ShareDialog", "  Title: ${item.title}")
    Log.d("ShareDialog", "  Artist: ${item.artist}")
    Log.d("ShareDialog", "  Type: ${item.type}")
    Log.d("ShareDialog", "  Spotify ID: ${item.spotifyId}")
    Log.d("ShareDialog", "  Spotify URL (prop): ${item.spotifyUrl}")
    Log.d("ShareDialog", "  YouTube ID: ${item.youtubeId}")
    Log.d("ShareDialog", "  URL FINAL GENERADA: $shareUrl")
    Log.d("ShareDialog", "===========================================")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF181818)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // QR Code con la URL de Spotify
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
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Botón compartir con diálogo nativo
                    if (shareUrl != null) {
                        Text(
                            text = "<share>",
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
                    }
                }
            }
        }
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
