package com.plyr.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.playlists.PlaylistCover
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
import com.plyr.network.YouTubePlaylistInfo
import java.util.Locale


/**
 * Pantalla de búsqueda de escritorio (M7).
 *
 * Puerto "de consola" de `com.plyr.ui.SearchScreen`: como en Android, la consulta
 * se lanza contra YouTube y los resultados se pueden reproducir. Desde M19 muestra
 * las listas de YouTube junto a los vídeos (dos secciones plegables, `v`/`>`) y
 * avisa al pulsarlas para abrir su detalle. Desde M21 ofrece «cargar más» al
 * pie de los vídeos (mismo umbral que Android: ≥10 resultados) y va añadiendo
 * páginas. M28 retiró el lector de QR sin cámara (M11): la consulta llega desde la
 * barra de búsqueda de la Home.
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
    onToggleVideos: () -> Unit = {},
    onTogglePlaylists: () -> Unit = {},
    onOpenPlaylist: ((YouTubePlaylistInfo) -> Unit)? = null,
    onLoadMore: () -> Unit = {},
    menuActions: TrackMenuActions = TrackMenuActions(),
    modifier: Modifier = Modifier,
) {
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

/** Tamaño de cada paquete de canciones del carrusel (M36). */
private const val SONG_PACK_SIZE = 5

/** Ancho de un paquete de canciones y de una tarjeta de lista. */
private val SONG_PACK_WIDTH = 300.dp
private val PLAYLIST_CARD_WIDTH = 140.dp
private val PLAYLIST_CARD_COVER = 140.dp

/** Separación común de los dos carruseles de resultados. */
private val CAROUSEL_SPACING = 12.dp

/**
 * Resultados de una búsqueda (M36): dos carruseles horizontales en una sola
 * `LazyColumn` — arriba las listas, como tarjetas iguales a las del carrusel de
 * la Home de Android, y debajo las canciones agrupadas en paquetes de cinco.
 *
 * Sustituyen a las dos listas verticales plegables de M19; las cabeceras
 * siguen siendo las mismas (`v`/`>`) y siguen plegando su carrusel.
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs),
    ) {
        playlistsSection(state, onTogglePlaylists, onOpenPlaylist)
        videosSection(
            state = state,
            onToggle = onToggleVideos,
            onLoadMore = onLoadMore,
            itemContent = { index, track ->
                ResultRow(
                    index = index,
                    track = track,
                    onPlayResult = onPlayResult,
                    menuItems = menuActions.itemsFor(track, onAddToPlaylist),
                )
            },
        )
    }
}

/**
 * ¿Toca pedir la página siguiente del carrusel? Solo cuando el último paso está
 * a la vista **tras haber desplazado**: si todo cabe en pantalla, el final ya se
 * ve sin mover el ratón y no debe cargar por su cuenta.
 */
private fun debePedirMas(
    scrolled: Boolean,
    lastVisible: Int?,
    packCount: Int,
    trailerCount: Int,
    canLoadMore: Boolean,
): Boolean {
    val lastItem = packCount + trailerCount - 1
    return scrolled && lastItem >= 0 && lastVisible == lastItem && canLoadMore
}

/** Carrusel de canciones (M36): paquetes de [SONG_PACK_SIZE] apilados en columna. */
@Composable
private fun SongPacksCarousel(
    tracks: List<PlayerTrack>,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    itemContent: @Composable (index: Int, track: PlayerTrack) -> Unit,
) {
    val listState = rememberLazyListState()
    val packCount = (tracks.size + SONG_PACK_SIZE - 1) / SONG_PACK_SIZE
    val trailerCount = if (isLoadingMore || canLoadMore) 1 else 0

    // Al desplazarse hasta el final se pide la página siguiente (M21), igual que
    // el «cargar más» del último paso. Solo se dispara tras desplazar: si todo
    // cabe en pantalla el final ya se ve sin mover el ratón y no debe cargar solo.
    // `canLoadMore` baja al empezar la carga, así que no se dispara dos veces.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val lastVisible by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index } }
    LaunchedEffect(scrolled, lastVisible, packCount, trailerCount, canLoadMore) {
        if (debePedirMas(scrolled, lastVisible, packCount, trailerCount, canLoadMore)) onLoadMore()
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(CAROUSEL_SPACING),
        contentPadding = PaddingValues(horizontal = PlyrSpacing.xs),
        modifier = Modifier.fillMaxWidth().testTag("search_songs_carousel"),
    ) {
        items(packCount, key = { pack -> "search_pack_$pack" }) { pack ->
            val start = pack * SONG_PACK_SIZE
            Column(
                modifier = Modifier
                    .width(SONG_PACK_WIDTH)
                    .testTag("search_pack_$pack"),
                verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs),
            ) {
                tracks.subList(start, minOf(start + SONG_PACK_SIZE, tracks.size))
                    .forEachIndexed { offset, track -> itemContent(start + offset, track) }
            }
        }

        if (isLoadingMore) {
            item(key = "search_loading_more") {
                Box(modifier = Modifier.width(SONG_PACK_WIDTH)) {
                    StatusText(Translations.get("search_loading"), "search_loading_more")
                }
            }
        } else if (canLoadMore) {
            item(key = "search_load_more") {
                Box(modifier = Modifier.width(SONG_PACK_WIDTH)) { LoadMoreRow(onLoadMore) }
            }
        }
    }
}

/** Carrusel de listas (M36): tarjetas iguales a las de la Home de Android. */
@Composable
private fun PlaylistCarousel(
    playlists: List<YouTubePlaylistInfo>,
    onOpenPlaylist: ((YouTubePlaylistInfo) -> Unit)?,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CAROUSEL_SPACING),
        contentPadding = PaddingValues(horizontal = PlyrSpacing.xs),
        modifier = Modifier.fillMaxWidth().testTag("search_playlists_carousel"),
    ) {
        items(playlists, key = { it.playlistId }) { playlist ->
            PlaylistCard(playlist = playlist, onClick = onOpenPlaylist)
        }
    }
}

/** Tarjeta de una lista en el carrusel: portada cuadrada con el título debajo. */
@Composable
private fun PlaylistCard(
    playlist: YouTubePlaylistInfo,
    onClick: ((YouTubePlaylistInfo) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(PLAYLIST_CARD_WIDTH)
            .testTag("search_playlist_${playlist.playlistId}")
            .clickable(enabled = onClick != null) { onClick?.invoke(playlist) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaylistCover(
            url = playlist.thumbnailUrl,
            size = PLAYLIST_CARD_COVER,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(PlyrSpacing.xs))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = listOf(
                playlist.uploader,
                String.format(Locale.ROOT, Translations.get("tracks_count"), playlist.videoCount),
            ).filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Sección de canciones: cabecera plegable y su carrusel de paquetes (M36). */
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
        item(key = "search_songs_carousel") {
            SongPacksCarousel(
                tracks = state.results,
                isLoadingMore = state.isLoadingMore,
                canLoadMore = state.canLoadMore,
                onLoadMore = onLoadMore,
                itemContent = itemContent,
            )
        }
    }
}

/** Fila «> cargar más» al final del carrusel de canciones (M21). */
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

/** Sección de listas: cabecera plegable y, si está abierta, su carrusel. */
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
        item(key = "search_playlists_carousel") {
            PlaylistCarousel(playlists = state.playlists, onOpenPlaylist = onOpenPlaylist)
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

/**
 * Fila de una canción dentro de un paquete (M36): solo título y artista, con el
 * menú `⋯` al final. El número es el global de la búsqueda, no el del paquete.
 */
@Composable
private fun ResultRow(
    index: Int,
    track: PlayerTrack,
    onPlayResult: (Int) -> Unit,
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
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
