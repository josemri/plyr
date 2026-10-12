package com.plyr.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.Screen
import com.plyr.desktop.ui.components.ActionButton
import com.plyr.desktop.ui.components.ActionButtonData
import com.plyr.desktop.ui.components.Subtitulo
import com.plyr.desktop.ui.components.Titulo
import com.plyr.desktop.ui.theme.PlyrSpacing

/**
 * Pantalla de relleno para el esqueleto de M5.
 *
 * Las pantallas de verdad (búsqueda, cola, listas y feed) llegan en M6/M7;
 * mientras tanto cada una muestra su título traducido, un mensaje de "aún no
 * hay nada" y un botón para volver al inicio. Así la navegación es navegable de
 * verdad sin arrastrar todavía la lógica de datos ni de reproducción.
 */
@Composable
fun PlaceholderScreen(
    screen: Screen,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large)
            .testTag("screen_${screen.name}"),
    ) {
        Titulo(Translations.get(screen.titleKey))

        Subtitulo(Translations.get("loading_tracks"))

        Text(
            text = Translations.get("no_tracks_loaded"),
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.secondary),
        )

        Spacer(Modifier.weight(1f))

        ActionButton(
            data = ActionButtonData(
                text = "\u2190 ${Translations.get("close")}",
                onClick = onBack,
            ),
            modifier = Modifier.testTag("back_from_${screen.name}"),
        )
    }
}
