package com.plyr.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Pantalla de inicio del escritorio.
 *
 * Es el equivalente de `com.plyr.ui.HomeScreen`, pero en vez del carrusel de
 * playlists (que necesita base de datos, M6) muestra un menú de consola con las
 * pantallas disponibles. Sirve de puerta de entrada a la navegación.
 */
@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    checkForUpdate: (suspend () -> UpdateChecker.UpdateInfo?)? = null,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        MenuOption(Screen.SEARCH, Translations.get(Screen.SEARCH.titleKey)),
        MenuOption(Screen.QUEUE, Translations.get(Screen.QUEUE.titleKey)),
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

        HomeVersion(update)

        Text(
            text = AsciiArt.DIVIDER,
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline),
            modifier = Modifier.padding(vertical = PlyrSpacing.large),
        )

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
