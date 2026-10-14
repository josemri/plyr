package com.plyr.desktop.download

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.plyr.desktop.i18n.Translations
import com.plyr.utils.StorageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lee el resumen del almacén offline (nº de pistas y bytes) y lo recalcula cada
 * vez que cambia [DownloadState.revision], es decir, tras cada descarga o
 * borrado. La lectura del directorio va en `Dispatchers.IO` porque es I/O de
 * disco y no debe bloquear la composición.
 */
@Composable
fun rememberOfflineSummary(controller: DownloadController): DownloadedAudioStore.Summary? {
    val state by controller.state.collectAsState()
    var summary by remember { mutableStateOf<DownloadedAudioStore.Summary?>(null) }
    LaunchedEffect(state.revision) {
        summary = withContext(Dispatchers.IO) { controller.summary() }
    }
    return summary
}

/**
 * Diálogo de gestión del audio offline (M17): cuántas pistas hay descargadas,
 * cuánto ocupan y un botón para borrarlas todas. Es el port del
 * `OfflineStorageDialog` de Android.
 *
 * Tras «borrar todo» el [DownloadController] sube su revisión, [remember]
 * vuelve a leer el resumen y el diálogo pasa a mostrar que está vacío.
 */
@Composable
fun OfflineStorageDialog(
    controller: DownloadController,
    onDismiss: () -> Unit,
) {
    val summary = rememberOfflineSummary(controller)
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("storage_dialog"),
        title = {
            Text(
                text = Translations.get("storage_title"),
                style = mono.copy(color = MaterialTheme.colorScheme.primary),
            )
        },
        text = {
            Text(
                text = offlineStorageMessage(summary),
                style = mono,
                modifier = Modifier.testTag("storage_message"),
            )
        },
        confirmButton = {
            val current = summary
            if (current != null && current.count > 0) {
                TextButton(
                    onClick = { controller.clearDownloads() },
                    modifier = Modifier.testTag("storage_delete_all"),
                ) {
                    Text(
                        text = Translations.get("delete"),
                        style = mono.copy(color = MaterialTheme.colorScheme.error),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("storage_close")) {
                Text(
                    text = Translations.get("close"),
                    style = mono.copy(color = MaterialTheme.colorScheme.primary),
                )
            }
        },
    )
}

/**
 * Texto del resumen: mientras se lee, «cargando»; si no hay nada, el aviso de
 * vacío; si hay audio, el recuento y el tamaño ya formateado.
 */
fun offlineStorageMessage(summary: DownloadedAudioStore.Summary?): String = when {
    summary == null -> Translations.get("loading")
    summary.count == 0 -> Translations.get("storage_empty")
    else -> String.format(
        Locale.ROOT,
        Translations.get("storage_message"),
        summary.count,
        StorageSize.format(summary.bytes),
    )
}
