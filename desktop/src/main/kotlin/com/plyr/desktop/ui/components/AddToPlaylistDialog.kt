package com.plyr.desktop.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.theme.PlyrSpacing

/**
 * Selector de lista destino para «añadir a lista» (M8).
 *
 * Vivía privado en `SearchScreen`; desde M22 lo comparten la búsqueda, la cola y
 * el detalle de lista.
 */
@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistRecord>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Translations.get("add_to_playlist")) },
        text = {
            if (playlists.isEmpty()) {
                Text(Translations.get("no_playlists"))
            } else {
                Column {
                    playlists.forEach { playlist ->
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(playlist.remoteId) }
                                .padding(vertical = PlyrSpacing.xs)
                                .testTag("add_to_${playlist.remoteId}"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Translations.get("close")) }
        },
    )
}
