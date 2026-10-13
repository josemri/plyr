package com.plyr.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.media.DesktopTray
import com.plyr.desktop.media.createMediaIntegration
import com.plyr.desktop.media.trayActions
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.player.PlayerController
import com.plyr.desktop.ui.AudioListScreen
import com.plyr.desktop.ui.theme.PlyrTheme
import com.plyr.desktop.utils.UpdateChecker

/**
 * Punto de entrada de la app de escritorio.
 *
 * Crea las carpetas de datos/caché del SO ([AppPaths], M9), aplica el tema
 * terminal según [DesktopConfig] (persistente desde M9: backend
 * `multiplatform-settings` sobre `java.util.prefs`) y arranca la navegación de
 * [AudioListScreen].
 *
 * Desde M13 también monta la **integración nativa**: MPRIS (Linux) para las
 * teclas multimedia y los metadatos, y un icono de bandeja con play/pausa,
 * siguiente, anterior y salir. La bandeja vive fuera de la ventana, así que el
 * controlador del reproductor creado por la UI se recoge aquí con
 * `onPlayerControllerReady`.
 *
 * La prueba de sonido del motor (M4) sigue disponible aparte, por línea de
 * comandos: `./gradlew :desktop:soundCheck --args="artista canción"`.
 */
fun main() = application {
    AppPaths.ensureDirectories()

    val mediaIntegration = remember { createMediaIntegration() }

    var playerController by remember { mutableStateOf<PlayerController?>(null) }
    val tray = remember {
        DesktopTray(
            actions = trayActions(
                onPlayPause = { playerController?.togglePlayPause() },
                onNext = { playerController?.next() },
                onPrevious = { playerController?.previous() },
                onQuit = ::exitApplication,
            ),
        )
    }
    DisposableEffect(Unit) {
        tray.install()
        onDispose {
            tray.remove()
            mediaIntegration.close()
        }
    }

    Window(onCloseRequest = ::exitApplication, title = "plyr") {
        val dark = DesktopConfig.isDark(isSystemInDarkTheme())
        PlyrTheme(darkTheme = dark) {
            AudioListScreen(
                mediaIntegration = mediaIntegration,
                checkForUpdate = { UpdateChecker.checkForUpdate() },
                onPlayerControllerReady = { playerController = it },
                onQuit = ::exitApplication,
            )
        }
    }
}
