package com.plyr.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.screens.ConfigScreen
import com.plyr.desktop.ui.screens.HomeScreen
import com.plyr.desktop.ui.screens.PlaceholderScreen

/** Altura reservada abajo para que los controles flotantes no tapen el contenido. */
private val CONTROLS_RESERVED_HEIGHT = 96.dp

/**
 * Contenedor de navegación del escritorio.
 *
 * Equivalente a `com.plyr.ui.AudioListScreen` de Android, pero en vez de pagers
 * (que no encajan con ratón/teclado) usa un `Screen` explícito que se cambia
 * desde el menú de inicio. Las pantallas de datos son de relleno hasta M6/M7;
 * aquí lo que importa es que la navegación y el tema/idioma funcionan.
 */
@Composable
fun AudioListScreen(
    modifier: Modifier = Modifier,
    initialScreen: Screen = Screen.HOME,
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    val goHome = { currentScreen = Screen.HOME }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = CONTROLS_RESERVED_HEIGHT),
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(onNavigate = { currentScreen = it })
                    Screen.SEARCH -> PlaceholderScreen(Screen.SEARCH, onBack = goHome)
                    Screen.QUEUE -> PlaceholderScreen(Screen.QUEUE, onBack = goHome)
                    Screen.PLAYLISTS -> PlaceholderScreen(Screen.PLAYLISTS, onBack = goHome)
                    Screen.FEED -> PlaceholderScreen(Screen.FEED, onBack = goHome)
                    Screen.CONFIG -> ConfigScreen(onBack = goHome)
                }
            }

            FloatingMusicControls(
                // Sin reproducción real todavía (M7): se muestra el estado
                // "no disponible" para que el componente portado sea visible.
                state = PlayerUiState(title = Translations.get("player_not_available")),
                onShowQueue = { currentScreen = Screen.QUEUE },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
