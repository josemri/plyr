package com.plyr.desktop.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.FileDialogs
import com.plyr.ui.components.ShareUrlPolicy
import com.plyr.ui.components.ShareableItem
import com.plyr.utils.QrCodec
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Diálogo de compartir por QR en escritorio (M11).
 *
 * Es el puerto del `ShareDialog` de Android sin las partes que no existen aquí
 * (NFC, recomendaciones a grupos): muestra el QR de la URL que decide
 * [ShareUrlPolicy], permite copiar el enlace y guardar el PNG. Si no hay nada que
 * compartir, avisa en lugar de dejar un diálogo vacío (B54).
 */
@Composable
fun QrShareDialog(item: ShareableItem, onDismiss: () -> Unit) {
    val shareUrl = ShareUrlPolicy.resolve(
        type = item.type,
        shareUrl = item.shareUrl,
        youtubeId = item.youtubeId,
        playlistOrigin = item.playlistOrigin,
    )
    val matrix = remember(shareUrl) { shareUrl?.let { QrCodec.generate(it) } }
    val bitmap = remember(matrix) { matrix?.let { QrImages.toImageBitmap(it) } }
    var status by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("qr_close")) {
                Text(Translations.get("close"))
            }
        },
        title = {
            Text(
                text = Translations.get("qr_title"),
                fontFamily = FontFamily.Monospace,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (shareUrl == null) {
                    Text(
                        text = Translations.get("no_share_url"),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("qr_no_url"),
                    )
                } else {
                    QrImage(bitmap, shareUrl)
                    Text(
                        text = shareUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("qr_url"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(StringSelection(shareUrl), null)
                                status = Translations.get("qr_copied")
                            },
                            modifier = Modifier.testTag("qr_copy"),
                        ) {
                            Text(Translations.get("qr_copy"), fontFamily = FontFamily.Monospace)
                        }
                        TextButton(
                            onClick = {
                                val file = FileDialogs.saveImage(
                                    title = Translations.get("qr_save"),
                                    suggestedName = "plyr-qr.png",
                                )
                                if (file != null && matrix != null) {
                                    QrImages.writePng(matrix, file)
                                    status = file.absolutePath
                                }
                            },
                            modifier = Modifier.testTag("qr_save"),
                        ) {
                            Text(Translations.get("qr_save"), fontFamily = FontFamily.Monospace)
                        }
                    }
                    status?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("qr_status"),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun QrImage(bitmap: androidx.compose.ui.graphics.ImageBitmap?, shareUrl: String) {
    if (bitmap == null) {
        Text(
            text = Translations.get("qr_error"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("qr_error"),
        )
    } else {
        Image(
            bitmap = bitmap,
            contentDescription = shareUrl,
            modifier = Modifier.size(220.dp).testTag("qr_image"),
        )
    }
}
