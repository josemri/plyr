package com.plyr.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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

/** Ancho del panel lateral de listas y tamaño de su portada (M28). */
private val HOME_RAIL_WIDTH = 88.dp
private val HOME_RAIL_COVER = 64.dp

/** Tamaño máximo del logo ASCII de la Home (M32), como el `imageMax*` de Android. */
private val LOGO_MAX_WIDTH = 280.dp
private val LOGO_MAX_HEIGHT = 220.dp

/**
 * Pantalla de inicio del escritorio.
 *
 * Porta `com.plyr.ui.HomeScreen`, reorganizada en M28: la búsqueda está arriba
 * centrada (se envía con Enter, ya sin botón `plyr_search`) y las listas visibles
 * ([playlists], ya filtradas por `PlaylistFilters` en `AudioListScreen`) viven en
 * un panel **vertical pegado a la izquierda**, al estilo de la biblioteca de
 * Spotify en escritorio: solo la portada (sin nombre), siempre visible; pulsar
 * una avisa por [onOpenPlaylist] para abrir su detalle.
 *
 * M27: el menú ya no ofrece la cola; como en Android, se abre solo con el `^` de
 * los controles flotantes.
 *
 * M29: el menú se reduce a `feed`; las listas se abren con un `>` al final del
 * panel lateral y los ajustes con un engranaje a la derecha del buscador.
 */
@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    playlists: List<PlaylistRecord> = emptyList(),
    onOpenPlaylist: (String) -> Unit = {},
    onSearch: (String) -> Unit = {},
    checkForUpdate: (suspend () -> UpdateChecker.UpdateInfo?)? = null,
    logoIndex: Int? = null,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        MenuOption(Screen.FEED, Translations.get(Screen.FEED.titleKey)),
    )

    // Logo ASCII aleatorio (M32): como Android, se elige una vez por composición.
    // Los tests pueden fijarlo con [logoIndex] para ser deterministas.
    val selectedLogo = remember(logoIndex) { logoIndex ?: AsciiArt.randomIndex() }

    // Aviso de actualización (M15): solo si la app inyecta el comprobador; así los
    // tests no tocan la red. El clic abre el instalable (o la página de releases).
    var update by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    if (checkForUpdate != null) {
        LaunchedEffect(checkForUpdate) { update = checkForUpdate() }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_HOME"),
    ) {
        // Panel de listas lateral (M28): vertical y pegado a la izquierda, visible
        // siempre (aunque no haya listas) para que la Home conserve el layout.
        HomePlaylistRail(
            playlists = playlists,
            onOpenPlaylist = onOpenPlaylist,
            onNavigate = onNavigate,
            modifier = Modifier
                .fillMaxHeight()
                .width(HOME_RAIL_WIDTH),
        )

        HomeMainContent(
            options = options,
            update = update,
            logoIndex = selectedLogo,
            onSearch = onSearch,
            onNavigate = onNavigate,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Columna central de la Home (M28/M29): buscador arriba centrado con el engranaje
 * de Ajustes a su derecha y, debajo, el logo, la versión y el menú de consola,
 * centrados verticalmente.
 */
@Composable
private fun HomeMainContent(
    options: List<MenuOption>,
    update: UpdateChecker.UpdateInfo?,
    logoIndex: Int,
    onSearch: (String) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(PlyrSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Arriba: buscador centrado y, a su derecha, el engranaje de Ajustes (M29).
        Box(modifier = Modifier.fillMaxWidth()) {
            HomeSearchBar(onSearch = onSearch, modifier = Modifier.align(Alignment.Center))
            HomeConfigButton(
                onClick = { onNavigate(Screen.CONFIG) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeLogo(update = update, index = logoIndex)

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
}

/** Acceso a Ajustes (M29): engranaje a la derecha del buscador; antes era `config` en el menú. */
@Composable
private fun HomeConfigButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ActionButton(
        data = ActionButtonData(text = PlyrSymbols.GEAR, onClick = onClick),
        fontSize = 22.sp,
        modifier = modifier.testTag("home_config"),
    )
}

/**
 * Barra de búsqueda de la Home (M28): sustituye a la entrada `plyr_search` del
 * menú. Se envía con Intro y avisa con [onSearch] para que la app navegue a los
 * resultados con esa consulta.
 */
@Composable
private fun HomeSearchBar(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }

    fun submit() {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) onSearch(trimmed)
    }

    Row(
        modifier = modifier
            .widthIn(max = 520.dp)
            .testTag("home_search"),
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
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PlyrSpacing.small)
                .testTag("home_search_input"),
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
    }
}

/**
 * Panel lateral de listas (M28/M29): columna vertical con la portada de cada
 * lista, pegada al borde izquierdo como la biblioteca de Spotify, y un `>` al
 * final que lleva a la pantalla de listas. Se muestra siempre (aunque no haya
 * listas) y cada portada avisa por [onOpenPlaylist] para abrir el detalle.
 */
@Composable
private fun HomePlaylistRail(
    playlists: List<PlaylistRecord>,
    onOpenPlaylist: (String) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .testTag("home_playlists"),
        contentPadding = PaddingValues(PlyrSpacing.small),
        verticalArrangement = Arrangement.spacedBy(PlyrSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(playlists, key = { it.remoteId }) { playlist ->
            HomePlaylistRailItem(playlist = playlist, onOpenPlaylist = onOpenPlaylist)
        }

        // Atajo a la pantalla de listas (M29), al final del panel; sustituye al
        // antiguo botón `plyr_lists` del menú.
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

/** Portada de una lista en el panel lateral; al pulsarla se abre el detalle. */
@Composable
private fun HomePlaylistRailItem(
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
        HomePlaylistCover(
            isLiked = isLiked,
            coverUrl = coverUrl,
            name = playlist.name,
            size = HOME_RAIL_COVER,
        )
    }
}

/**
 * Logo ASCII de la Home (M32): el mismo PNG que Android (`ascii_1..50`), teñido
 * con el color primario del tema. La columna alinea al final (derecha), así que la
 * versión ([HomeVersion]) queda **debajo del logo, pegada a su derecha**, igual
 * que en Android.
 */
@Composable
private fun HomeLogo(update: UpdateChecker.UpdateInfo?, index: Int) {
    val bitmap = remember(index) { AsciiArt.load(index) } ?: return
    val painter = remember(bitmap) { BitmapPainter(bitmap) }
    val intrinsic = painter.intrinsicSize

    Column(horizontalAlignment = Alignment.End) {
        Image(
            painter = painter,
            contentDescription = Translations.get("app_logo"),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .widthIn(max = LOGO_MAX_WIDTH)
                .heightIn(max = LOGO_MAX_HEIGHT)
                .aspectRatio(intrinsic.width / intrinsic.height)
                .testTag("home_logo"),
        )
        HomeVersion(update)
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

/** Portada de una fila del panel lateral: imagen, corazón (me gusta) o inicial. */
@Composable
private fun HomePlaylistCover(isLiked: Boolean, coverUrl: String?, name: String, size: Dp) {
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
