package com.plyr.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.config.ThemeMode
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Subtitulo
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols
import androidx.compose.ui.unit.dp

/**
 * Pantalla de ajustes del esqueleto (M5).
 *
 * Cablea [DesktopConfig] y [Translations]: al cambiar el tema el fondo y los
 * colores se actualizan al momento, y al cambiar el idioma se vuelven a leer
 * todas las etiquetas. El backend real (persistente) es M9.
 */
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
            .testTag("screen_CONFIG"),
    ) {
        Titulo(Translations.get("config_title"))

        // === TEMA ===
        Subtitulo(Translations.get("theme"))
        Row {
            ThemeMode.entries.forEach { mode ->
                val selected = DesktopConfig.theme == mode
                ActionButton(
                    data = ActionButtonData(
                        text = selectionLabel(Translations.get(themeKey(mode)), selected),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onClick = { DesktopConfig.theme = mode },
                    ),
                    modifier = Modifier.testTag("theme_${mode.id}"),
                )
            }
        }

        Spacer(Modifier.height(PlyrSpacing.large))

        // === IDIOMA ===
        Subtitulo(Translations.get("language"))
        Row {
            Language.entries.forEach { language ->
                val selected = DesktopConfig.language == language
                ActionButton(
                    data = ActionButtonData(
                        text = selectionLabel(Translations.get("lang_${language.name.lowercase()}"), selected),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onClick = { DesktopConfig.language = language },
                    ),
                    modifier = Modifier.testTag("lang_${language.name.lowercase()}"),
                )
            }
        }

        Spacer(Modifier.height(PlyrSpacing.large))

        Text(
            text = "${Translations.get("theme")}: ${Translations.get(themeKey(DesktopConfig.theme))}  ·  " +
                "${Translations.get("language")}: ${Translations.get("lang_${DesktopConfig.language.name.lowercase()}")}",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.weight(1f))

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_CONFIG"),
        )
    }
}

/** Marca visualmente la opción activa con el símbolo de "seleccionado". */
private fun selectionLabel(text: String, selected: Boolean): String =
    if (selected) "${PlyrSymbols.BULLET} $text" else "  $text"

/** Clave de traducción del nombre de cada modo de tema. */
private fun themeKey(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> "theme_dark"
    ThemeMode.LIGHT -> "theme_light"
    ThemeMode.SYSTEM -> "theme_system"
    ThemeMode.AUTO -> "theme_auto"
}
