package com.plyr.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val APP_VERSION = "v${UpdateChecker.CURRENT_VERSION}"

/** Tamaño máximo del logo ASCII de la Home (M32), como el `imageMax*` de Android. */
private val LOGO_MAX_WIDTH = 280.dp
private val LOGO_MAX_HEIGHT = 220.dp

/**
 * Pantalla de inicio del escritorio.
 *
 * Porta `com.plyr.ui.HomeScreen`, reorganizada en M28: la búsqueda está arriba
 * centrada (se envía con Enter, ya sin botón `plyr_search`) y, debajo, el logo,
 * la versión y el menú de consola.
 *
 * M36: el panel lateral de listas dejó de ser parte de esta pantalla —pasa a
 * [com.plyr.desktop.ui.components.AppPlaylistRail], compuesto por el contenedor
 * para estar visible en todas las secciones.
 *
 * M27: el menú ya no ofrece la cola; como en Android, se abre solo con el `^` de
 * los controles flotantes.
 *
 * M29: el menú se reduce a `feed` y los ajustes viven en un engranaje junto al
 * buscador.
 */
@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
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

    HomeMainContent(
        options = options,
        update = update,
        logoIndex = selectedLogo,
        onSearch = onSearch,
        onNavigate = onNavigate,
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_HOME"),
    )
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
