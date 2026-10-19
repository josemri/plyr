package com.plyr.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.config.ThemeMode
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.qr.QrShareDialog
import com.plyr.desktop.spotify.SpotifyImportController
import com.plyr.desktop.spotify.SpotifyImportState
import com.plyr.desktop.sync.BackupController
import com.plyr.desktop.sync.BackupState
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing
import com.plyr.ui.components.ShareType
import com.plyr.ui.components.ShareableItem

/**
 * Pantalla de ajustes del escritorio (M9).
 *
 * Puerto de `com.plyr.ui.ConfigScreen` con lo que aplica a escritorio: tema e
 * idioma (como tiras `opción / opción / opción`, igual que Android), más las rutas
 * de datos y caché del SO. Desde M10 incluye la copia de seguridad y desde M11 la
 * importación de listas de Spotify (sin botón: se lanza con Intro). El compartir
 * por QR de pistas y listas vive en la vista de detalle; desde M24 esta pantalla
 * añade además la sección «compartir app».
 *
 * Todos los valores se leen y escriben en [DesktopConfig], que los persiste entre
 * reinicios; el tema y el idioma cambian la UI al momento porque viven en estado
 * de Compose.
 */
@Composable
fun ConfigScreen(
    onBack: () -> Unit,
    backupController: BackupController? = null,
    spotifyImportController: SpotifyImportController? = null,
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
        ToggleRow(
            title = Translations.get("theme"),
            options = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.AUTO),
            selected = DesktopConfig.theme,
            label = { themeLabel(it) },
            tag = { "theme_${it.id}" },
            onSelect = { DesktopConfig.theme = it },
        )

        Spacer(Modifier.height(PlyrSpacing.large))

        // === IDIOMA ===
        ToggleRow(
            title = Translations.get("language"),
            options = Language.entries,
            selected = DesktopConfig.language,
            label = { languageLabel(it) },
            tag = { "lang_${it.name.lowercase()}" },
            onSelect = { DesktopConfig.language = it },
        )

        Spacer(Modifier.height(PlyrSpacing.large))

        // === ALMACENAMIENTO (rutas por SO) ===
        PathsSection()

        // === IMPORTAR DE SPOTIFY (M11) ===
        if (spotifyImportController != null) {
            Spacer(Modifier.height(PlyrSpacing.large))
            SpotifyImportSection(spotifyImportController)
        }

        // === COPIA DE SEGURIDAD (M10) ===
        if (backupController != null) {
            Spacer(Modifier.height(PlyrSpacing.large))
            BackupSection(backupController)
        }

        // === COMPARTIR APP (M24) ===
        Spacer(Modifier.height(PlyrSpacing.large))
        ShareAppSection()

        ConfigBackButton(onBack)
    }
}

/** Botón fijo de «volver» al pie de los ajustes. */
@Composable
private fun ConfigBackButton(onBack: () -> Unit) {
    Spacer(Modifier.height(PlyrSpacing.xxl))
    ActionButton(
        data = ActionButtonData(
            text = "< ${Translations.get("back")}",
            onClick = onBack,
        ),
        modifier = Modifier.testTag("back_from_CONFIG"),
    )
}

/**
 * Rutas de datos y caché del SO en las que vive la app (solo informativo).
 */
@Composable
private fun PathsSection() {
    PathRow(Translations.get("data_folder"), AppPaths.directories.dataDir, "path_data")
    PathRow(Translations.get("cache_folder"), AppPaths.directories.cacheDir, "path_cache")
}

/**
 * Sección de copia de seguridad: un botón que elige carpeta o sincroniza y el
 * estado del último intento. La carpeta elegida ya se anuncia en la etiqueta del
 * propio botón (`... w/ <carpeta>`), así que no se repite aparte.
 */
@Composable
private fun BackupSection(controller: BackupController) {
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.refresh() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        ActionButton(
            data = ActionButtonData(
                text = if (state.isSyncing) {
                    Translations.get("sync_working")
                } else {
                    controller.buttonLabel(state)
                },
                onClick = { controller.onSyncPressed() },
            ),
            modifier = Modifier.testTag("sync_button"),
        )
    }
    BackupStatusMessage(state)
}

/** Mensaje del último intento de sincronización, si lo hay. */
@Composable
private fun BackupStatusMessage(state: BackupState) {
    val message = state.statusMessage ?: return
    Spacer(Modifier.height(PlyrSpacing.xxs))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = if (state.statusIsError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().testTag("sync_status"),
    )
}

/**
 * Sección de importación de Spotify: campo de URL/id y estado del último intento.
 * No hay botón: al pulsar Intro (`ImeAction.Go`) se lanza la importación, igual
 * que en Android. La lista importada aparece sola en la pantalla de listas porque
 * el repositorio emite sus cambios (M11).
 */
@Composable
private fun SpotifyImportSection(controller: SpotifyImportController) {
    val state by controller.state.collectAsState()

    OutlinedTextField(
        value = state.url,
        onValueChange = { controller.onUrlChange(it) },
        placeholder = { Text(Translations.get("spotify_import_hint")) },
        singleLine = true,
        enabled = !state.isImporting,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { controller.import() }),
        modifier = Modifier.fillMaxWidth().testTag("spotify_url"),
    )
    SpotifyStatusMessage(state)
}

/** Mensaje del último intento de importación, si lo hay. */
@Composable
private fun SpotifyStatusMessage(state: SpotifyImportState) {
    val message = state.statusMessage ?: return
    Spacer(Modifier.height(PlyrSpacing.xxs))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = if (state.statusIsError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().testTag("spotify_status"),
    )
}

/**
 * Sección «compartir app» (M24): paridad con `ShareAppSection` de Android.
 * Genera el QR y el enlace de descarga de plyr reutilizando [QrShareDialog] con
 * un [ShareableItem] de tipo [ShareType.APP] (la URL la decide
 * `ShareUrlPolicy.APP_DOWNLOAD_URL`).
 */
@Composable
private fun ShareAppSection() {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        ActionButton(
            data = ActionButtonData(
                text = Translations.get("share_me"),
                onClick = { showDialog = true },
            ),
            modifier = Modifier.testTag("share_app_button"),
        )
    }

    if (showDialog) {
        QrShareDialog(
            item = ShareableItem(
                remoteId = null,
                shareUrl = null,
                youtubeId = null,
                title = "plyr",
                artist = "",
                type = ShareType.APP,
            ),
            onDismiss = { showDialog = false },
        )
    }
}

/**
 * Una fila de ajuste con el título a la izquierda y las opciones a la derecha
 * separadas por `/` (como el `MultiToggle` de Android). La opción activa se pinta
 * en color primario y el resto en secundario. Es controlado: el estado vive en
 * quien lo invoca ([DesktopConfig]), no aquí.
 */
@Composable
private fun <T> ToggleRow(
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
            modifier = Modifier.weight(1f),
        )
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary,
                    ),
                    modifier = Modifier.padding(horizontal = PlyrSpacing.small),
                )
            }
            Text(
                text = label(option),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (option == selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                ),
                modifier = Modifier
                    .clickable { onSelect(option) }
                    .padding(PlyrSpacing.small)
                    .testTag(tag(option)),
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
