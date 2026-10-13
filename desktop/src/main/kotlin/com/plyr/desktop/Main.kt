package com.plyr.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.ui.AudioListScreen
import com.plyr.desktop.ui.theme.PlyrTheme

/**
 * Punto de entrada de la app de escritorio.
 *
 * Crea las carpetas de datos/caché del SO ([AppPaths], M9), aplica el tema
 * terminal según [DesktopConfig] (persistente desde M9: backend
 * `multiplatform-settings` sobre `java.util.prefs`) y arranca la navegación de
 * [AudioListScreen].
 *
 * La prueba de sonido del motor (M4) sigue disponible aparte, por línea de
 * comandos: `./gradlew :desktop:soundCheck --args="artista canción"`.
 */
fun main() = application {
    AppPaths.ensureDirectories()
    Window(onCloseRequest = ::exitApplication, title = "plyr") {
        val dark = DesktopConfig.isDark(isSystemInDarkTheme())
        PlyrTheme(darkTheme = dark) {
            AudioListScreen()
        }
    }
}
