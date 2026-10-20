package com.plyr.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.theme.AppIcon
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.utils.UrlParser

/** Ancho del panel lateral de listas y tamaño de su portada (M28). */
val PLAYLIST_RAIL_WIDTH: Dp = 88.dp

/** Tamaño de la portada de una lista dentro del panel. */
val PLAYLIST_RAIL_COVER: Dp = 64.dp

/** Tamaño del botón con el logo de la app (M36). */
private val LOGO_BUTTON_WIDTH: Dp = PLAYLIST_RAIL_WIDTH - PlyrSpacing.small * 2
private val LOGO_BUTTON_HEIGHT: Dp = 56.dp

/**
 * Panel lateral de listas, presente en **todas** las pantallas del escritorio
 * (M36). Nació en la Home (M28) y allí se quedaba: ahora se compone una sola vez
 * en el contenedor, así que sigue visible al cambiar de sección.
 *
 * Arriba, fuera del desplazamiento, va el botón con el icono de la app: siempre
 * a la vista y siempre vuelve al inicio con [onHome]. Debajo, la columna con la
 * portada de cada lista y, al final, un `>` que abre la pantalla de listas.
 */
@Composable
fun AppPlaylistRail(
    onHome: () -> Unit,
    onNavigate: (Screen) -> Unit,
    playlists: List<PlaylistRecord> = emptyList(),
    onOpenPlaylist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(PLAYLIST_RAIL_WIDTH)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogoButton(onClick = onHome)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("home_playlists"),
            contentPadding = PaddingValues(PlyrSpacing.small),
            verticalArrangement = Arrangement.spacedBy(PlyrSpacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(playlists, key = { it.remoteId }) { playlist ->
                PlaylistRailItem(playlist = playlist, onOpenPlaylist = onOpenPlaylist)
            }

            // Atajo a la pantalla de listas (M29), al final del panel.
            item(key = "home_more_lists") {
                ActionButton(
                    data = ActionButtonData(text = ">", onClick = { onNavigate(Screen.PLAYLISTS) }),
                    fontSize = 22.sp,
                    modifier = Modifier
                        .padding(top = PlyrSpacing.medium)
                        .testTag("home_more_lists"),
                )
            }
        }
    }
}

/**
 * Botón-logo (M36): el mismo `ic_launcher` de Android que llevan el instalador y
 * la ventana, sin teñir —el icono ya trae sus colores—. Si el recurso no cargara
 * se sustituye por el texto, para que el botón nunca desaparezca: es la única
 * vía de vuelta al inicio y tiene que estar visible siempre.
 */
@Composable
private fun AppLogoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bitmap = remember { AppIcon.load() }

    Box(
        modifier = modifier
            .padding(PlyrSpacing.small)
            .size(width = LOGO_BUTTON_WIDTH, height = LOGO_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("app_logo_home"),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                painter = remember(bitmap) { BitmapPainter(bitmap) },
                contentDescription = Translations.get("app_logo"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(PlyrSpacing.xxs),
            )
        } else {
            Text(
                text = "plyr",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Portada de una lista en el panel lateral; al pulsarla se abre el detalle. */
@Composable
private fun PlaylistRailItem(
    playlist: PlaylistRecord,
    onOpenPlaylist: (String) -> Unit,
) {
    val isLiked = playlist.remoteId == LikedSongs.ID
    val coverUrl = if (isLiked) null else UrlParser.normalizeYoutubeThumb(playlist.imageUrl)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenPlaylist(playlist.remoteId) }
            .testTag("home_playlist_${playlist.remoteId}"),
    ) {
        PlaylistRailCover(
            isLiked = isLiked,
            coverUrl = coverUrl,
            name = playlist.name,
            size = PLAYLIST_RAIL_COVER,
        )
    }
}

/** Portada de una fila del panel lateral: imagen, corazón (me gusta) o inicial. */
@Composable
private fun PlaylistRailCover(isLiked: Boolean, coverUrl: String?, name: String, size: Dp) {
    val shape = RoundedCornerShape(8.dp)
    when {
        isLiked -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "♥", fontSize = 20.sp, color = MaterialTheme.colorScheme.error)
        }
        !coverUrl.isNullOrBlank() -> AsyncImage(
            model = coverUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
