package com.plyr.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                // Título
                Text(
                    text = "$ share",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        color = Color(0xFF4ECDC4)
                    )
                )

                // Info del item
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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

                    // Mostrar la URL en texto pequeño
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = Color(0xFF666666)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Texto explicativo
                Text(
                    text = "Escanea o comparte la URL de Spotify",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF666666)
                    )
                )

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Botón compartir con diálogo nativo
                    if (shareUrl != null) {
                        Button(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                    type = "text/plain"
                                }
                                val chooserIntent = Intent.createChooser(sendIntent, "Compartir via")
                                chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(chooserIntent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4ECDC4)
                            )
                        ) {
                            Text(
                                text = "share",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Black
                                )
                            )
                        }
                    }

                    // Botón cerrar
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF666666)
                        )
                    ) {
                        Text(
                            text = "close",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            )
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
