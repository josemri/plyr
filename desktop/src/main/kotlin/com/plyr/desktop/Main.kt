package com.plyr.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.ui.AudioListScreen
import com.plyr.desktop.ui.theme.PlyrTheme

/**
 * Punto de entrada de la app de escritorio.
 *
 * Aplica el tema terminal según [DesktopConfig] (que en el esqueleto de M5 vive
 * en memoria y se cambia desde la pantalla de ajustes) y arranca la navegación
 * de [AudioListScreen].
 *
 * La prueba de sonido del motor (M4) sigue disponible aparte, por línea de
 * comandos: `./gradlew :desktop:soundCheck --args="artista canción"`. La
 * reproducción integrada en esta UI llega en M7.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "plyr") {
        val dark = DesktopConfig.isDark(isSystemInDarkTheme())
        PlyrTheme(darkTheme = dark) {
            AudioListScreen()
        }
    }
}
