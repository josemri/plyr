package com.plyr.desktop.ui.theme

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource

/**
 * Logos ASCII de la Home del escritorio.
 *
 * Son **los mismos PNG** que usa Android (`ascii_1..ascii_50`, en
 * `drawable-nodpi`), copiados a `desktop/src/main/resources/ascii/`. Android
 * recorre `1..50` y elige uno al azar cada vez que se compone la Home (`remember`);
 * aquí se replica ese comportamiento pero con recursos de imagen (antes había un
 * banner de texto fijo como placeholder).
 */
object AsciiArt {

    /** Número de logos disponibles, igual que el `1..50` de Android. */
    const val COUNT = 50

    /** Carpeta dentro de `src/main/resources` con los logos. */
    const val DIR = "ascii"

    /** Índice aleatorio de logo (`1..COUNT`). */
    fun randomIndex(): Int = (1..COUNT).random()

    /** Ruta de clase del logo [index]. */
    fun resourcePath(index: Int): String = "$DIR/ascii_$index.png"

    /**
     * Carga el logo [index] como bitmap. Los ficheros se copian del `drawable-nodpi`
     * de Android, así que siempre existen para `1..COUNT`; si faltara, devuelve
     * `null` en vez de reventar para no tumbar la Home.
     *
     * Usa `useResource`/`loadImageBitmap` (API de recursos por classpath de Compose
     * Desktop, marcadas como deprecadas en favor de `composeResources`, que este
     * proyecto aún no configura): de ahí el `@Suppress`.
     */
    @Suppress("DEPRECATION")
    fun load(index: Int): ImageBitmap? =
        runCatching { useResource(resourcePath(index)) { loadImageBitmap(it) } }.getOrNull()
}
