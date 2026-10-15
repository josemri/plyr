package com.plyr.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.Browser
import com.plyr.desktop.ui.MenuOption
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.theme.AsciiArt
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols
import com.plyr.desktop.utils.UpdateChecker
import com.plyr.utils.UrlParser

private val APP_VERSION = "v${UpdateChecker.CURRENT_VERSION}"

/** Portada y ancho de celda del carrusel de listas de la Home (M18). */
private val HOME_CAROUSEL_COVER = 120.dp
private val HOME_CAROUSEL_ITEM_WIDTH = 140.dp

/**
 * Pantalla de inicio del escritorio.
 *
 * Porta `com.plyr.ui.HomeScreen`: sobre el menú de consola muestra un carrusel
 * horizontal con las listas visibles ([playlists], ya filtradas por
 * `PlaylistFilters` en `AudioListScreen`) y su portada. Pulsar una lista avisa
 * por [onOpenPlaylist] para que la app abra su detalle.
 *
 * M27: el menú ya no ofrece la cola; como en Android, se abre solo con el `^` de
 * los controles flotantes. M28: la búsqueda deja de ser una entrada del menú y
 * pasa a ser la barra [onSearch] de la Home, que navega a los resultados.
 */
@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    playlists: List<PlaylistRecord> = emptyList(),
    onOpenPlaylist: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    checkForUpdate: (suspend () -> UpdateChecker.UpdateInfo?)? = null,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        MenuOption(Screen.PLAYLISTS, Translations.get(Screen.PLAYLISTS.titleKey)),
        MenuOption(Screen.FEED, Translations.get(Screen.FEED.titleKey)),
        MenuOption(Screen.CONFIG, Translations.get(Screen.CONFIG.titleKey)),
    )

    // Aviso de actualización (M15): solo si la app inyecta el comprobador; así los
    // tests no tocan la red. El clic abre el instalable (o la página de releases).
    var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    if (checkForUpdate != null) {
        LaunchedEffect(checkForUpdate) { update = checkForUpdate() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.xxl)
            .testTag("screen_HOME"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = AsciiArt.LOGO,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            ),
            textAlign = TextAlign.Center,
        )

        // Carrusel de listas (M18): solo si hay alguna visible, para no dejar un
        // hueco vacío cuando aún no se ha creado/importado nada.
        if (playlists.isNotEmpty()) {
            HomePlaylistCarousel(playlists = playlists, onOpenPlaylist = onOpenPlaylist)
        }

        HomeVersion(update)

        Text(
            text = AsciiArt.DIVIDER,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
            modifier = Modifier.padding(vertical = PlyrSpacing.large),
        )

        // Búsqueda en la Home (M28): sustituye al botón `plyr_search` del menú.
        HomeSearchBar(onSearch = onSearch)

        Spacer(Modifier.height(PlyrSpacing.large))

        Column(
            modifier = Modifier.widthIn(min = 220.dp),
            verticalArrangement = Arrangement.spacedBy(PlyrSpacing.xs),
            horizontalAlignment = Alignment.Start,
        ) {
            options.forEach { option ->
                ActionButton(
                    data = ActionButtonData(
                        text = "${PlyrSymbols.PROMPT}${option.title}",
                        onClick = { onNavigate(option.screen) },
                    ),
                    fontSize = 18.sp,
                    modifier = Modifier.testTag("menu_${option.screen.name}"),
                )
            }
        }
    }
}

/**
 * Barra de búsqueda de la Home (M28): sustituye a la entrada `plyr_search` del
 * menú. Al enviar (Enter o el botón `[ plyr_search ]`), avisa con [onSearch] para
 * que la app navegue a la pantalla de resultados y lance la consulta.
 */
@Composable
private fun HomeSearchBar(onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    fun submit() {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) onSearch(trimmed)
    }

    Row(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 420.dp)
            .fillMaxWidth(),
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
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PlyrSpacing.small)
                .testTag("home_search_input"),
        )

        ActionButton(
            data = ActionButtonData(
                text = "[ ${Translations.get("search_title")} ]",
                onClick = { submit() },
            ),
            modifier = Modifier.testTag("home_search_button"),
        )
    }
}

/** Versión de la app y, si la hay, aviso de actualización clicable (M15). */
@Composable
private fun HomeVersion(update: UpdateChecker.UpdateInfo?) {
    val latest = update?.takeIf { it.isUpdateAvailable && it.latestVersion.isNotBlank() }
    Text(
        text = if (latest != null) {
            "${PlyrSymbols.BULLET} ${Translations.get("update_available")} " +
                "$APP_VERSION ${PlyrSymbols.ARROW} v${latest.latestVersion}"
        } else {
            APP_VERSION
        },
        style = MaterialTheme.typography.bodySmall.copy(
            color = if (latest != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = Modifier
            .padding(top = PlyrSpacing.small)
            .testTag("home_version")
            .clickable(enabled = latest != null) { Browser.open(latest?.downloadUrl ?: UpdateChecker.RELEASES_URL) },
    )
}

/**
 * Carrusel horizontal con las listas visibles (M18), portado de Android.
 *
 * Empieza siempre por la primera lista a la izquierda. El estado del scroll se
 * recuerda por composición para que no "salte" al recomponer.
 */
@Composable
private fun HomePlaylistCarousel(
    playlists: List<PlaylistRecord>,
    onOpenPlaylist: (String) -> Unit,
) {
    val listState = remember { LazyListState() }
    LaunchedEffect(playlists) {
        if (playlists.isNotEmpty()) listState.scrollToItem(0)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PlyrSpacing.medium)
            .testTag("home_playlists"),
        horizontalArrangement = Arrangement.spacedBy(PlyrSpacing.medium),
    ) {
        items(playlists, key = { it.remoteId }) { playlist ->
            HomePlaylistItem(playlist = playlist, onOpenPlaylist = onOpenPlaylist)
        }
    }
}

/** Celda del carrusel: portada + nombre; al pulsarla se abre el detalle. */
@Composable
private fun HomePlaylistItem(
    playlist: PlaylistRecord,
    onOpenPlaylist: (String) -> Unit,
) {
    val isLiked = playlist.remoteId == LikedSongs.ID
    val coverUrl = if (isLiked) null else UrlParser.normalizeYoutubeThumb(playlist.imageUrl)

    Column(
        modifier = Modifier
            .width(HOME_CAROUSEL_ITEM_WIDTH)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenPlaylist(playlist.remoteId) }
            .testTag("home_playlist_${playlist.remoteId}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomePlaylistCover(isLiked = isLiked, coverUrl = coverUrl, name = playlist.name)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Portada de una lista en el carrusel (M18).
 *
 * Favoritos va con el corazón; el resto usa Coil 3 (como [com.plyr.desktop.playlists.PlaylistCover])
 * o, si no hay portada, la inicial del nombre.
 */
@Composable
private fun HomePlaylistCover(isLiked: Boolean, coverUrl: String?, name: String) {
    val shape = RoundedCornerShape(12.dp)
    when {
        isLiked -> Box(
            modifier = Modifier
                .size(HOME_CAROUSEL_COVER)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "♥", fontSize = 40.sp, color = MaterialTheme.colorScheme.error)
        }
        !coverUrl.isNullOrBlank() -> AsyncImage(
            model = coverUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(HOME_CAROUSEL_COVER)
                .clip(shape),
        )
        else -> Box(
            modifier = Modifier
                .size(HOME_CAROUSEL_COVER)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
