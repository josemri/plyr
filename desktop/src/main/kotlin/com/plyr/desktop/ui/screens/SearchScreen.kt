package com.plyr.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.qr.QrScanDialog
import com.plyr.desktop.search.SearchState
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Subtitulo
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols
import com.plyr.model.ScanResult

/**
 * Pantalla de búsqueda de escritorio (M7).
 *
 * Puerto "de consola" de `com.plyr.ui.SearchScreen`: como en Android, la consulta
 * se lanza contra YouTube y los resultados se pueden reproducir. Desde M11
 * incluye el lector de QR sin cámara ([QrScanDialog]): pegar una URL o abrir una
 * imagen, que se traduce al mismo [ScanResult] que Android.
 *
 * El estado y los callbacks llegan de fuera ([SearchState] + lambdas) para que el
 * composable sea testeable sin red ni motor de audio.
 */
@Composable
fun SearchScreen(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPlayResult: (Int) -> Unit,
    onBack: () -> Unit,
    playlists: List<PlaylistRecord> = emptyList(),
    onAddToPlaylist: ((PlayerTrack, String) -> Unit)? = null,
    onScanResult: ((ScanResult) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showScan by remember { mutableStateOf(false) }
    var pendingAdd by remember { mutableStateOf<PlayerTrack?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
            .testTag("screen_${Screen.SEARCH.name}"),
    ) {
        Titulo(Translations.get("search_title"))

        SearchBar(
            query = state.query,
            isLoading = state.isLoading,
            onQueryChange = onQueryChange,
            onSearch = { onSearch(state.query) },
        )

        if (onScanResult != null) {
            ActionButton(
                data = ActionButtonData(
                    text = "[ ${Translations.get("scan_title")} ]",
                    onClick = { showScan = true },
                ),
                modifier = Modifier.testTag("search_scan"),
            )
        }

        Spacer(Modifier.height(PlyrSpacing.small))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> StatusText(Translations.get("search_loading"), "search_loading")
                state.error != null -> StatusText(state.error, "search_error", isError = true)
                state.isEmptyResult -> StatusText(Translations.get("no_tracks_loaded"), "search_empty")
                state.results.isNotEmpty() -> Column(modifier = Modifier.fillMaxSize()) {
                    Subtitulo(Translations.get("search_youtube_results"))
                    ResultsList(
                        results = state.results,
                        onPlayResult = onPlayResult,
                        onAddToPlaylist = onAddToPlaylist?.let { { track -> pendingAdd = track } },
                    )
                }
            }
        }

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_${Screen.SEARCH.name}"),
        )
    }

    if (showScan) {
        QrScanDialog(
            onScanned = { onScanResult?.invoke(it) },
            onDismiss = { showScan = false },
        )
    }

    pendingAdd?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { playlistId ->
                onAddToPlaylist?.invoke(track, playlistId)
                pendingAdd = null
            },
            onDismiss = { pendingAdd = null },
        )
    }
}

/** Picker de lista destino para "añadir a lista" desde los resultados (M8). */
@Composable
private fun AddToPlaylistDialog(
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

@Composable
private fun SearchBar(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = PlyrSymbols.PROMPT,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            ),
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PlyrSpacing.small)
                .testTag("search_input"),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = Translations.get("search_placeholder"),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
                inner()
            },
        )

        ActionButton(
            data = ActionButtonData(
                text = "[ ${Translations.get("search_title")} ]",
                onClick = onSearch,
                enabled = !isLoading,
            ),
            modifier = Modifier.testTag("search_button"),
        )
    }
}

@Composable
private fun ResultsList(
    results: List<PlayerTrack>,
    onPlayResult: (Int) -> Unit,
    onAddToPlaylist: ((PlayerTrack) -> Unit)? = null,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(results, key = { index, track -> "${track.id}#$index" }) { index, track ->
            ResultRow(index, track, onPlayResult, onAddToPlaylist)
        }
    }
}

@Composable
private fun ResultRow(
    index: Int,
    track: PlayerTrack,
    onPlayResult: (Int) -> Unit,
    onAddToPlaylist: ((PlayerTrack) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag("search_result_$index")
                .clickable { onPlayResult(index) }
                .padding(vertical = PlyrSpacing.xs),
        ) {
            Text(
                text = "${index + 1}. ${track.title}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(track.artist, track.duration).filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onAddToPlaylist != null) {
            TextButton(
                onClick = { onAddToPlaylist(track) },
                modifier = Modifier.testTag("search_add_$index"),
            ) {
                Text(text = "+", fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun StatusText(text: String, tag: String, isError: Boolean = false) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            ),
            modifier = Modifier.testTag(tag),
        )
    }
}
