package com.plyr.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.config.ThemeMode
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Subtitulo
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.desktop.ui.theme.PlyrSymbols
import com.plyr.domain.RepeatMode

/**
 * Pantalla de ajustes del escritorio (M9).
 *
 * Puerto de `com.plyr.ui.ConfigScreen` con lo que aplica a escritorio: tema,
 * idioma y modo de repetición, más las rutas de datos y caché del SO. Las
 * secciones de móvil (gestos de swipe, importar de Spotify, copia de seguridad,
 * compartir por QR/NFC) no existen aquí: la de copia llega en M10 y el resto se
 * descartan en el port.
 *
 * Todos los valores se leen y escriben en [DesktopConfig], que los persiste entre
 * reinicios; el tema y el idioma cambian la UI al momento porque viven en estado
 * de Compose.
 */
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    onRepeatModeSelected: (String) -> Unit = { DesktopConfig.repeatMode = it },
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PlyrSpacing.large)
            .testTag("screen_CONFIG"),
    ) {
        Titulo(Translations.get("config_title"))

        // === TEMA ===
        OptionSection(
            title = Translations.get("theme"),
            options = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.AUTO),
            selected = DesktopConfig.theme,
            label = { themeLabel(it) },
            tag = { "theme_${it.id}" },
            onSelect = { DesktopConfig.theme = it },
        )

        Spacer(Modifier.height(PlyrSpacing.large))

        // === IDIOMA ===
        OptionSection(
            title = Translations.get("language"),
            options = Language.entries,
            selected = DesktopConfig.language,
            label = { languageLabel(it) },
            tag = { "lang_${it.name.lowercase()}" },
            onSelect = { DesktopConfig.language = it },
        )

        Spacer(Modifier.height(PlyrSpacing.large))

        // === REPETICIÓN ===
        OptionSection(
            title = Translations.get("repeat"),
            options = listOf(RepeatMode.OFF, RepeatMode.ONE, RepeatMode.ALL),
            selected = DesktopConfig.repeatMode,
            label = { repeatLabel(it) },
            tag = { "repeat_$it" },
            onSelect = onRepeatModeSelected,
        )

        Spacer(Modifier.height(PlyrSpacing.large))

        // === ALMACENAMIENTO (rutas por SO) ===
        Subtitulo(Translations.get("paths_title"))
        PathRow(Translations.get("data_folder"), AppPaths.directories.dataDir, "path_data")
        PathRow(Translations.get("cache_folder"), AppPaths.directories.cacheDir, "path_cache")

        Spacer(Modifier.height(PlyrSpacing.xxl))

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_CONFIG"),
        )
    }
}

/**
 * Una fila de ajuste con un título y una tira de opciones mutuamente excluyentes.
 * La opción activa se marca con el símbolo de "seleccionado".
 */
@Composable
private fun <T> OptionSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = PlyrSpacing.small),
        )
        options.forEach { option ->
            val isSelected = option == selected
            ActionButton(
                data = ActionButtonData(
                    text = if (isSelected) "${PlyrSymbols.BULLET} ${label(option)}" else "  ${label(option)}",
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    onClick = { onSelect(option) },
                ),
                modifier = Modifier.testTag(tag(option)),
            )
        }
    }
}

/** Ruta de una carpeta del SO, con su etiqueta traducida. */
@Composable
private fun PathRow(label: String, path: String, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = PlyrSpacing.xxs),
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(tag),
        )
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> Translations.get("theme_dark")
    ThemeMode.LIGHT -> Translations.get("theme_light")
    ThemeMode.SYSTEM -> Translations.get("theme_system")
    ThemeMode.AUTO -> Translations.get("theme_auto")
}

private fun languageLabel(language: Language): String = when (language) {
    Language.SPANISH -> Translations.get("lang_spanish")
    Language.ENGLISH -> Translations.get("lang_english")
    Language.CATALAN -> Translations.get("lang_catalan")
    Language.JAPANESE -> Translations.get("lang_japanese")
}

private fun repeatLabel(mode: String): String = when (mode) {
    RepeatMode.OFF -> Translations.get("repeat_off")
    RepeatMode.ONE -> Translations.get("repeat_one")
    else -> Translations.get("repeat_all")
}
