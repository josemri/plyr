package com.plyr.desktop.platform

import com.plyr.core.util.Logger
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.net.URI

/** Abre enlaces en el navegador del sistema (M15). */
object Browser {

    private const val TAG = "Browser"

    /** Abre [url] y devuelve si se pudo; en headless o sin soporte, false. */
    fun open(url: String): Boolean {
        if (url.isBlank()) return false
        if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
        return try {
            desktop.browse(URI(url))
            true
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo abrir $url: ${e.message}")
            false
        }
    }
}
