package com.plyr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Tamaño por defecto de la portada de una tarjeta: el del carrusel de la Home. */
val PLAYLIST_CARD_SIZE = 140.dp

/**
 * Tarjeta de portada cuadrada con el título debajo (M36).
 *
 * Es la tarjeta del carrusel de la Home, extraída a un componente compartido
 * para poder mostrar las listas de la búsqueda **igual** que allí sin que los
 * dos aspectos acaben desincronizándose.
 *
 * @param subtitle segunda línea (quien la firma y cuántas pistas tiene); se
 * omite si está en blanco, que es como se ve en la Home.
 */
@Composable
fun PlaylistCoverCard(
    coverUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    subtitle: String? = null,
    size: Dp = PLAYLIST_CARD_SIZE,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .width(size)
            .clip(shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlaylistCoverImage(
            coverUrl = coverUrl,
            title = title,
            isLiked = isLiked,
            size = size,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** La portada: corazón (me gusta), imagen o la inicial del título. */
@Composable
private fun PlaylistCoverImage(
    coverUrl: String?,
    title: String,
    isLiked: Boolean,
    size: Dp,
) {
    val shape = RoundedCornerShape(12.dp)
    val placeholderModifier = Modifier
        .size(size)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    when {
        isLiked -> Box(
            modifier = placeholderModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♥",
                fontSize = 48.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
        !coverUrl.isNullOrBlank() -> AsyncImage(
            model = coverUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape)
        )
        else -> Box(
            modifier = placeholderModifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
