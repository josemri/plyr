package com.plyr.desktop.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource

/**
 * Icono de la app en escritorio (M36).
 *
 * Es el **mismo `ic_launcher` de Android**: `ic_launcher-playstore.png` (512x512,
 * fondo `#1A1A1A`), copiado a `src/main/resources/icon/plyr.png`. Del mismo origen
 * salen los tres formatos de jpackage —`packaging/icon.png`, `.ico` e `.icns`—
 * que se regeneran con `desktop/packaging/make-icons.sh`.
 *
 * Lo dibujan la ventana de la app (barra de tareas y conmutador de aplicaciones)
 * y el botón-logo del panel lateral de listas.
 */
object AppIcon {

    /** Ruta de clase del icono dentro de `src/main/resources`. */
    const val RESOURCE = "icon/plyr.png"

    /**
     * Carga el icono como bitmap, o `null` si el recurso faltara: así quien lo
     * usa puede caer a un texto en su lugar en vez de reventar.
     *
     * Usa `useResource`/`loadImageBitmap` (API de recursos por classpath de
     * Compose Desktop, marcadas como deprecadas en favor de `composeResources`,
     * que este proyecto aún no configura): de ahí el `@Suppress`.
     */
    @Suppress("DEPRECATION")
    fun load(): ImageBitmap? = runCatching { useResource(RESOURCE) { loadImageBitmap(it) } }.getOrNull()

    /** El mismo icono como [Painter], para la ventana de Compose Desktop. */
    fun painter(): Painter? = load()?.let { BitmapPainter(it) }
}
