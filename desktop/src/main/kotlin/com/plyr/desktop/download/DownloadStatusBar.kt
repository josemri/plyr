package com.plyr.desktop.download

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.theme.PlyrSpacing
import java.util.Locale

/**
 * Barra de estado de la descarga offline de una lista (M17).
 *
 * Es el port de la `PlaylistDownloadBar` de Android: mientras se baja muestra el
 * recuento (`descargando 2/5`) y una barra de progreso con opción de cancelar;
 * al terminar deja el resultado hasta que [DownloadController.dismissResult] lo
 * olvida (la pantalla lo descarta sola pasados unos segundos).
 *
 * [playlistId] limita la barra a la descarga de esa lista (el detalle); `null`
 * la muestra para cualquier descarga en curso (el listado de listas). El estado
 * es el mismo [DownloadState] de M16: aquí solo se traduce y se pinta.
 */
@Composable
fun DownloadStatusBar(
    state: DownloadState,
    controller: DownloadController,
    playlistId: String? = null,
    modifier: Modifier = Modifier,
) {
    if (playlistId != null && state.playlistId != playlistId) return
    val message = downloadStatusMessage(state)
    if (message.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PlyrSpacing.xs)
            .testTag("download_status_bar"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (state.isDownloading) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (state.isDownloading) {
                Spacer(Modifier.width(PlyrSpacing.small))
                ActionButton(
                    data = ActionButtonData(
                        text = Translations.get("cancel"),
                        color = MaterialTheme.colorScheme.error,
                        onClick = { controller.cancel() },
                    ),
                    modifier = Modifier.testTag("download_cancel"),
                )
            }
        }
        if (state.isDownloading) {
            Spacer(Modifier.height(PlyrSpacing.xs))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().testTag("download_progress"),
            )
        }
    }
}

/**
 * Texto del estado de descarga, ya traducido. Devuelve cadena vacía si no hay
 * nada que mostrar (ni descarga en curso ni resultado pendiente).
 */
fun downloadStatusMessage(state: DownloadState): String = when {
    state.isDownloading -> String.format(
        Locale.ROOT,
        Translations.get("download_downloading"),
        (state.completed + 1).coerceAtMost(state.total),
        state.total,
    )
    state.outcome == DownloadOutcome.DOWNLOADED -> String.format(
        Locale.ROOT,
        Translations.get("download_done"),
        state.completed,
        state.total,
    )
    state.outcome == DownloadOutcome.PARTIAL -> String.format(
        Locale.ROOT,
        Translations.get("download_partial"),
        state.completed,
        state.total,
    )
    state.outcome == DownloadOutcome.ALREADY_DOWNLOADED -> Translations.get("download_already")
    state.outcome == DownloadOutcome.CANCELLED -> Translations.get("download_cancelled")
    else -> ""
}
