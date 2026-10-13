package com.plyr.desktop.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage

/**
 * Portada de una lista (M8) con Coil 3.
 *
 * [url] puede ser una URL remota (portada de YouTube/Spotify) o `file://`
 * (portada personalizada); Coil resuelve ambos con sus componentes por defecto.
 * Sin portada queda el hueco del color de superficie para no romper la rejilla.
 */
@Composable
fun PlaylistCover(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
