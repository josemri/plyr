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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.playlists.PlaylistCover
import com.plyr.desktop.qr.QrScanDialog
import com.plyr.desktop.search.SearchState
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.AddToPlaylistDialog
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.components.TrackMenuActions
import com.plyr.desktop.ui.components.TrackMenuButton
import com.plyr.desktop.ui.components.TrackMenuItem
import com.plyr.desktop.ui.components.itemsFor
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols
import com.plyr.model.ScanResult
import com.plyr.network.YouTubePlaylistInfo
import java.util.Locale


/**
 * Pantalla de búsqueda de escritorio (M7).
 *
 * Puerto "de consola" de `com.plyr.ui.SearchScreen`: como en Android, la consulta
 * se lanza contra YouTube y los resultados se pueden reproducir. Desde M11
 * incluye el lector de QR sin cámara ([QrScanDialog]): pegar una URL o abrir una
 * imagen, que se traduce al mismo [ScanResult] que Android. Desde M19 muestra
 * las listas de YouTube junto a los vídeos (dos secciones plegables, `v`/`>`) y
 * avisa al pulsarlas para abrir su detalle. Desde M21 ofrece «cargar más» al
 * pie de los vídeos (mismo umbral que Android: ≥10 resultados) y va añadiendo
 * páginas.
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
    onToggleVideos: () -> Unit = {},
    onTogglePlaylists: () -> Unit = {},
    onOpenPlaylist: ((YouTubePlaylistInfo) -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    menuActions: TrackMenuActions = TrackMenuActions(),
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
                state.hasResults -> SearchResultsList(
                    state = state,
                    onPlayResult = onPlayResult,
                    onAddToPlaylist = onAddToPlaylist?.let { { track -> pendingAdd = track } },
                    onToggleVideos = onToggleVideos,
                    onTogglePlaylists = onTogglePlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                    onLoadMore = onLoadMore,
                    menuActions = menuActions,
                )
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

/**
 * Resultados de una búsqueda (M19): una sola `LazyColumn` con la sección de
 * vídeos y la de listas, ambas plegables —el mismo `v`/`>` que Android.
 */
@Composable
private fun SearchResultsList(
    state: SearchState,
    onPlayResult: (Int) -> Unit,
    onAddToPlaylist: ((PlayerTrack) -> Unit)?,
    onToggleVideos: () -> Unit,
    onTogglePlaylists: () -> Unit,
    onOpenPlaylist: ((YouTubePlaylistInfo) -> Unit)?,
    onLoadMore: () -> Unit,
    menuActions: TrackMenuActions,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        videosSection(
            state = state,
            onToggle = onToggleVideos,
            onLoadMore = onLoadMore,
            itemContent = { index, track ->
                ResultRow(
                    index = index,
                    track = track,
                    onPlayResult = onPlayResult,
                    onAddToPlaylist = onAddToPlaylist,
                    menuItems = menuActions.itemsFor(track, onAddToPlaylist),
                )
            },
        )
        playlistsSection(state, onTogglePlaylists, onOpenPlaylist)
    }
}

/** Sección de vídeos: cabecera plegable, resultados y «cargar más» (M21). */
private fun LazyListScope.videosSection(
    state: SearchState,
    onToggle: () -> Unit,
    onLoadMore: () -> Unit,
    itemContent: @Composable (index: Int, track: PlayerTrack) -> Unit,
) {
    if (state.results.isEmpty()) return
    item(key = "search_videos_header") {
        SectionHeader(
            expanded = state.videosExpanded,
            label = Translations.get("search_youtube_results"),
            count = state.results.size,
            onClick = onToggle,
            tag = "search_videos_header",
        )
    }
    if (state.videosExpanded) {
        itemsIndexed(state.results, key = { index, track -> "${track.id}#$index" }) { index, track ->
            itemContent(index, track)
        }
        when {
            state.isLoadingMore ->
                item(key = "search_loading_more") {
                    StatusText(Translations.get("search_loading"), "search_loading_more")
                }
            state.canLoadMore ->
                item(key = "search_load_more") { LoadMoreRow(onLoadMore) }
        }
    }
}

/** Fila «> cargar más» al pie de los vídeos (M21), igual que en Android. */
@Composable
private fun LoadMoreRow(onLoadMore: () -> Unit) {
    Text(
        text = "> ${Translations.get("search_load_more")}",
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.secondary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_load_more")
            .clickable(onClick = onLoadMore)
            .padding(vertical = PlyrSpacing.small),
    )
}

/** Sección de listas: cabecera plegable y, si está abierta, cada lista. */
private fun LazyListScope.playlistsSection(
    state: SearchState,
    onToggle: () -> Unit,
    onOpenPlaylist: ((YouTubePlaylistInfo) -> Unit)?,
) {
    if (state.playlists.isEmpty()) return
    item(key = "search_playlists_header") {
        SectionHeader(
            expanded = state.playlistsExpanded,
            label = Translations.get("youtube_playlists"),
            count = state.playlists.size,
            onClick = onToggle,
            tag = "search_playlists_header",
        )
    }
    if (state.playlistsExpanded) {
        itemsIndexed(state.playlists, key = { _, playlist -> playlist.playlistId }) { _, playlist ->
            PlaylistResultRow(playlist, onOpenPlaylist)
        }
    }
}

/** Cabecera plegable estilo consola: `v etiqueta [n]` / `> etiqueta [n]`. */
@Composable
private fun SectionHeader(
    expanded: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit,
    tag: String,
) {
    Text(
        text = "${if (expanded) "v" else ">"} $label [$count]",
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(vertical = PlyrSpacing.small),
    )
}

/** Fila de una lista en los resultados: portada, título y datos (M19). */
@Composable
private fun PlaylistResultRow(
    playlist: YouTubePlaylistInfo,
    onOpenPlaylist: ((YouTubePlaylistInfo) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_playlist_${playlist.playlistId}")
            .clickable(enabled = onOpenPlaylist != null) { onOpenPlaylist?.invoke(playlist) }
            .padding(vertical = PlyrSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(url = playlist.thumbnailUrl, size = 40.dp)
        Spacer(Modifier.width(PlyrSpacing.small))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(
                    playlist.uploader,
                    String.format(Locale.ROOT, Translations.get("tracks_count"), playlist.videoCount),
                ).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultRow(
    index: Int,
    track: PlayerTrack,
    onPlayResult: (Int) -> Unit,
    onAddToPlaylist: ((PlayerTrack) -> Unit)? = null,
    menuItems: List<TrackMenuItem> = emptyList(),
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
        TrackMenuButton(
            items = menuItems,
            menuTag = "search_menu_$index",
        )
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
