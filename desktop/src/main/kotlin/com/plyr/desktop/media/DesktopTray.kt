package com.plyr.desktop.media

import com.plyr.desktop.i18n.Translations
import java.awt.Color
import java.awt.Image
import java.awt.MenuItem
import java.awt.Polygon
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/** Una entrada de la bandeja: texto visible y acción a ejecutar. */
data class TrayAction(val label: String, val onClick: () -> Unit)

/** Entradas de la bandeja del reproductor (M13), con etiquetas traducidas. */
fun trayActions(
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onQuit: () -> Unit,
): List<TrayAction> = listOf(
    TrayAction(Translations.get("tray_play_pause"), onPlayPause),
    TrayAction(Translations.get("tray_next"), onNext),
    TrayAction(Translations.get("tray_previous"), onPrevious),
    TrayAction(Translations.get("exit"), onQuit),
)

/**
 * Dibuja el icono de la bandeja sin depender de recursos: un triángulo de play
 * sobre un cuadrado verde.
 */
fun createTrayImage(size: Int = 22): Image {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    graphics.color = Color(0x1D, 0xB9, 0x54)
    graphics.fillRoundRect(0, 0, size, size, size / 3, size / 3)
    graphics.color = Color.WHITE
    val play = Polygon()
    play.addPoint(size * 8 / 22, size * 5 / 22)
    play.addPoint(size * 8 / 22, size * 17 / 22)
    play.addPoint(size * 17 / 22, size / 2)
    graphics.fillPolygon(play)
    graphics.dispose()
    return image
}

/**
 * Icono de bandeja del sistema (M13) con play/pausa, siguiente, anterior y salir.
 *
 * Se usa `java.awt.SystemTray` (la API que pide el hito) en lugar de la `Tray`
 * de Compose para poder degradar con elegancia cuando no hay bandeja (Xvfb,
 * sesiones sin panel): [install] devuelve `false` en ese caso, en vez de romper
 * el arranque. Las piezas de AWT se inyectan para poder testear la lógica sin
 * abrir una ventana.
 */
class DesktopTray(
    private val actions: List<TrayAction>,
    private val tooltip: String = "plyr",
    private val isSupported: () -> Boolean = { SystemTray.isSupported() },
    private val imageFactory: () -> Image = { createTrayImage() },
    private val addIcon: (TrayIcon) -> Unit = { SystemTray.getSystemTray().add(it) },
    private val removeIcon: (TrayIcon) -> Unit = { SystemTray.getSystemTray().remove(it) },
) {
    private var icon: TrayIcon? = null

    /** Instala el icono; devuelve `false` si el sistema no soporta bandeja. */
    fun install(): Boolean {
        if (icon != null) return true
        if (!isSupported()) return false
        return try {
            val menu = PopupMenu()
            actions.forEach { action ->
                menu.add(MenuItem(action.label).apply { addActionListener { action.onClick() } })
            }
            val trayIcon = TrayIcon(imageFactory(), tooltip, menu)
            trayIcon.isImageAutoSize = true
            addIcon(trayIcon)
            icon = trayIcon
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Quita el icono de la bandeja si estaba instalado. */
    fun remove() {
        icon?.let { removeIcon(it) }
        icon = null
    }

    /** `true` si el icono está actualmente instalado. */
    val isInstalled: Boolean get() = icon != null
}
