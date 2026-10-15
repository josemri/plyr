package com.plyr.desktop.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.sp
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerTrack

/**
 * Una entrada del menú `⋯` de una pista (M22).
 *
 * [tag] es un literal estable para los tests; como solo hay un menú abierto a la
 * vez, no hace falta hacerlo único por fila.
 */
data class TrackMenuItem(
    val label: String,
    val tag: String,
    val onClick: () -> Unit,
)

/**
 * Botón `⋯` con el menú de acciones de una pista (M22).
 *
 * Si [items] está vacío no pinta nada, así las filas de tests que no cablean
 * acciones no muestran un menú vacío. [menuTag] identifica el botón de la fila.
 */
@Composable
fun TrackMenuButton(
    items: List<TrackMenuItem>,
    menuTag: String,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = modifier.testTag(menuTag)) {
            Text("⋯", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label, fontFamily = FontFamily.Monospace) },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    modifier = Modifier.testTag(item.tag),
                )
            }
        }
    }
}

/**
 * Acciones disponibles para el menú `⋯` de una pista (M22).
 *
 * Se agrupan en un solo tipo para no arrastrar cuatro callbacks por cada fila y
 * para que cada pantalla cablee solo lo que puede ofrecer (los `null` se omiten).
 * "Añadir a lista" no vive aquí: la pantalla la intercepta para abrir su
 * selector, así que se pasa aparte a [itemsFor].
 */
data class TrackMenuActions(
    val isLiked: (PlayerTrack) -> Boolean = { false },
    val onToggleLike: ((PlayerTrack) -> Unit)? = null,
    val onAddToQueue: ((PlayerTrack) -> Unit)? = null,
    val onShare: ((PlayerTrack) -> Unit)? = null,
)

/**
 * Entradas del menú `⋯` para [track]: me gusta, añadir a cola, añadir a lista y
 * compartir. Solo se incluyen las acciones con callback.
 *
 * @param onAddToPlaylist acción de *abrir el selector* de lista destino.
 */
fun TrackMenuActions.itemsFor(
    track: PlayerTrack,
    onAddToPlaylist: ((PlayerTrack) -> Unit)? = null,
): List<TrackMenuItem> = buildList {
    onToggleLike?.let { toggle ->
        add(TrackMenuItem(Translations.get(if (isLiked(track)) "liked" else "like"), "track_like") { toggle(track) })
    }
    onAddToQueue?.let { queue ->
        add(TrackMenuItem(Translations.get("add_to_queue"), "track_add_queue") { queue(track) })
    }
    onAddToPlaylist?.let { pick ->
        add(TrackMenuItem(Translations.get("add_to_playlist"), "track_add_playlist") { pick(track) })
    }
    onShare?.let { share ->
        add(TrackMenuItem(Translations.get("share"), "track_share") { share(track) })
    }
}

