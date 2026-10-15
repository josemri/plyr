package com.plyr.desktop.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import java.util.Locale

/**
 * Formulario de alta de lista (M8). Desde M22 incluye un buscador de YouTube
 * (F21): se buscan canciones con `+`, se acumulan como seleccionadas (`x` para
 * quitarlas) y al guardar nacen ya dentro de la lista. La búsqueda llega
 * inyectada para poder testear el composable sin red.
 */
@Composable
fun CreatePlaylistView(
    controller: PlaylistsController,
    searchTracks: suspend (String) -> List<PlayerTrack> = { emptyList() },
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val search = remember(searchTracks) { TrackSearchController(scope, searchTracks) }
    val searchState by search.state.collectAsState()

    Titulo(Translations.get("create_playlist"))

    PlaylistFields(
        name = name,
        onNameChange = { name = it },
        description = desc,
        onDescriptionChange = { desc = it },
    )

    Spacer(modifier = Modifier.height(8.dp))

    TrackSearchBox(search = search, state = searchState)
    SelectedTracks(state = searchState, onToggle = search::toggleSelected)

    Spacer(modifier = Modifier.height(8.dp))

    ActionButton(
        data = ActionButtonData(
            text = Translations.get("save"),
            enabled = name.isNotBlank(),
            onClick = {
                // `createPlaylist` normaliza id/posición; la lista vacía ya no es
                // el único alta posible (M22).
                controller.createPlaylist(name, desc, searchState.selected.map { it.toTrackRecord("") })
            },
        ),
        modifier = Modifier.testTag("create_playlist_confirm")
    )
    Spacer(modifier = Modifier.height(8.dp))
    ActionButton(
        data = ActionButtonData(text = Translations.get("cancel"), onClick = { controller.hideCreate() }),
        modifier = Modifier.testTag("create_playlist_cancel")
    )
}

/** Campos de texto del nombre y la descripción. */
@Composable
private fun PlaylistFields(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(Translations.get("playlist_name")) },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_name")
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text(Translations.get("description_optional")) },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_desc")
    )
}

/** Buscador de YouTube de la lista: campo, botón, estado y resultados. */
@Composable
private fun TrackSearchBox(search: TrackSearchController, state: TrackSearchState) {
    OutlinedTextField(
        value = state.query,
        onValueChange = search::onQueryChange,
        label = { Text(Translations.get("search_tracks_label")) },
        modifier = Modifier.fillMaxWidth().testTag("create_playlist_search"),
    )
    Spacer(modifier = Modifier.height(4.dp))
    ActionButton(
        data = ActionButtonData(
            text = "[ ${Translations.get("search_title")} ]",
            enabled = state.query.isNotBlank() && !state.isSearching,
            onClick = { search.performSearch() },
        ),
        modifier = Modifier.testTag("create_playlist_search_button"),
    )

    if (state.isSearching) {
        Text(
            text = Translations.get("search_loading"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("create_playlist_searching"),
        )
    }
    if (state.error != null) {
        Text(
            text = state.error.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.error,
        )
    }

    state.results.forEachIndexed { index, track ->
        SelectionRow(
            preview = track,
            tag = "create_result_$index",
            selected = state.isSelected(track),
            onToggle = { search.toggleSelected(track) },
        )
    }
}

/** Resultados ya seleccionados, con `x` para quitarlos antes de guardar. */
@Composable
private fun SelectedTracks(state: TrackSearchState, onToggle: (PlayerTrack) -> Unit) {
    if (state.selected.isEmpty()) return

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = String.format(Locale.ROOT, Translations.get("tracks_count"), state.selected.size),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
    Column {
        state.selected.forEachIndexed { index, track ->
            SelectionRow(
                preview = track,
                tag = "create_selected_$index",
                selected = true,
                onToggle = { onToggle(track) },
            )
        }
    }
}

/**
 * Fila de un resultado: el título recortado y un botón `+`/`x` que la añade o la
 * quita de la selección.
 */
@Composable
private fun SelectionRow(
    preview: PlayerTrack,
    tag: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = listOf(preview.title, preview.artist).filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onToggle,
            modifier = Modifier.testTag(tag),
        ) {
            Text(text = if (selected) "x" else "+", fontFamily = FontFamily.Monospace)
        }
    }
}
