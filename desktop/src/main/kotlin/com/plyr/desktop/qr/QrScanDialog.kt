package com.plyr.desktop.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.FileDialogs
import com.plyr.model.ScanResult
import java.io.File

/**
 * Lector de QR de escritorio (M11).
 *
 * Sustituye al `QrScannerDialog` de Android (CameraX, imposible en JVM) por las
 * dos alternativas que sí tienen sentido aquí: pegar una URL y abrir una imagen
 * con el QR. Ambas se decodifican con [QrScan] y producen un [ScanResult]. Se usa
 * el mismo diálogo en la pantalla de búsqueda para lanzar la búsqueda.
 */
@Composable
fun QrScanDialog(
    onScanned: (ScanResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ScanResult?>(null) }

    fun handle(scanned: ScanResult?) {
        if (scanned == null) {
            error = Translations.get("scan_error")
        } else {
            result = scanned
            error = null
            onScanned(scanned)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("scan_close")) {
                Text(Translations.get("close"))
            }
        },
        title = {
            Text(text = Translations.get("scan_title"), fontFamily = FontFamily.Monospace)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text(Translations.get("scan_hint")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("scan_input"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { handle(QrScan.fromText(text)) },
                        modifier = Modifier.testTag("scan_decode"),
                    ) {
                        Text(Translations.get("scan_decode"), fontFamily = FontFamily.Monospace)
                    }
                    TextButton(
                        onClick = { handle(openImage()) },
                        modifier = Modifier.testTag("scan_open_image"),
                    ) {
                        Text(Translations.get("scan_open_image"), fontFamily = FontFamily.Monospace)
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("scan_error"),
                    )
                }
                result?.let {
                    Text(
                        text = Translations.get("scan_result").format("${it.source}/${it.type}/${it.id}"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("scan_result"),
                    )
                }
            }
        },
    )
}

/** Abre el selector de imágenes; si el usuario cancela, no hay resultado. */
private fun openImage(): ScanResult? {
    val file: File = FileDialogs.chooseImageToOpen(Translations.get("scan_open_image")) ?: return null
    return QrScan.fromImage(file)
}
