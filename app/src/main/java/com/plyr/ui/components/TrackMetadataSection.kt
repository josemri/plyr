package com.plyr.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plyr.utils.formatDurationMs

/**
 * Fila de metadatos de una canción (Album / Release / Duration) dentro de un
 * LazyColumn. Emite los items por separado para conservar el espaciado.
 */
fun LazyListScope.trackMetadataSection(
    albumName: String?,
    releaseDate: String?,
    durationMs: Int?
) {
    albumName?.let { album ->
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Album: $album",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
        }
    }
    releaseDate?.let { date ->
        item {
            Text(
                text = "Release: $date",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            )
        }
    }
    item {
        Text(
            text = "Duration: ${durationMs?.let { formatDurationMs(it) } ?: "N/A"}",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        )
    }
}
