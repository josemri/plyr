package com.plyr.desktop.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Valida las opciones de `libvlc` que usa [VlcAudioPlayer].
 *
 * Hay dos clases de error que solo se ven al ejecutar:
 *
 * 1. **Opción muerta.** `libvlc` es engañosamente permisivo: ante una opción que
 *    ya no existe **no lanza excepción**, solo escribe «Warning: option --ipv6
 *    no longer exists» y sigue. Y hay una trampa peor: ese aviso lo emite la
 *    biblioteca nativa **directamente en el descriptor 2 del proceso**, así que
 *    redirigir `System.err` desde Java **no lo captura**. Por eso aquí se lanza
 *    un JVM hijo y se lee su `stderr` real; un test que solo compruebe «no
 *    lanzó excepción» da por buenas las opciones muertas — pasó de verdad:
 *    `--ipv6=0` y `--no-ipv6` se colaron y dejaban el aviso en cada canción.
 * 2. **Elección de opciones por esquema.** Un fichero descargado y un stream no
 *    llevan las mismas opciones (`:demux=avformat` en ambos; los ajustes de red
 *    solo en el stream).
 *
 * El primero necesita `libvlc`; el segundo es puro y corre siempre.
 */
class VlcMediaOptionsTest {

    /**
     * Crea una instancia de `libvlc` con [option] **en un proceso hijo** y
     * devuelve el `stderr` real de ese proceso. `null` si `libvlc` no está
     * disponible y no se pudo ejecutar.
     */
    private fun libVlcStderr(option: String): String? {
        val dir = VlcEnvironment.findLibraryDir() ?: return null
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val process = ProcessBuilder(
            java,
            "-Djna.library.path=${VlcEnvironment.librarySearchPath(dir)}",
            "-cp", System.getProperty("java.class.path"),
            LibVlcProbe::class.java.name,
            option,
        ).start()

        val stderr = process.errorStream.bufferedReader().readText()
        process.waitFor()
        return stderr
    }

    private fun requireVlc(): File {
        val dir = VlcEnvironment.findLibraryDir()
        assumeTrue("libvlc no disponible: se omite la validación real", dir != null)
        return dir!!
    }

    @Test
    fun lasOpcionesDeInstanciaNoGeneranNingunAvisoDeLibVlc() {
        requireVlc()

        VlcAudioPlayer.instanceOptions.forEach { option ->
            val stderr = libVlcStderr(option) ?: return@forEach
            assertTrue(
                "libvlc se queja de '$option': $stderr",
                stderr.isBlank(),
            )
        }
    }

    @Test
    fun unaOpcionMuertaSeDetectaSoloPorElStderrDelProceso() {
        requireVlc()

        // Documenta por qué el test no puede limitarse a "no lanzó excepción":
        // VLC avisa por el fd 2 nativo y sigue, sin excepción de Java.
        val stderr = libVlcStderr("--no-ipv6")!!
        assertTrue(
            "--no-ipv6 debería avisar de que ya no existe (si esto falla, " +
                "VLC la volvió a soportar y el test necesita revisión): $stderr",
            stderr.contains("no longer exists"),
        )
    }

    @Test
    fun elFicheroLocalYElStreamLlevanOpcionesDistintas() {
        val local = VlcAudioPlayer.mediaOptionsFor("file:///home/x/audio/abc.m4a")
        val stream = VlcAudioPlayer.mediaOptionsFor("https://rr1---sn-x.googlevideo.com/videoplayback?x=1")

        // Ambos son MP4 fragmentado: el demux tolerante va en los dos.
        assertTrue(local.contains(":demux=avformat"))
        assertTrue(stream.contains(":demux=avformat"))

        // Los ajustes de red no tienen sentido para un fichero local.
        assertTrue(stream.any { it.startsWith(":ipv4-timeout=") })
        assertFalse(local.any { it.startsWith(":ipv4-timeout=") })
        assertFalse(local.contains(":network-caching=1000"))
    }

    @Test
    fun ningunEsquemaLocalRecibeAjustesDeRed() {
        listOf("file:///tmp/a.m4a", "file:/tmp/a.m4a").forEach { uri ->
            val options = VlcAudioPlayer.mediaOptionsFor(uri)
            assertTrue(
                "un fichero local no debe llevar ajustes de red: $uri",
                options.none { it.startsWith(":network-caching") || it.startsWith(":ipv4-timeout") },
            )
        }
    }
}

/**
 * Arranca `libvlc` con una única opción y termina. Se ejecuta como proceso
 * aparte ([VlcMediaOptionsTest.libVlcStderr]) porque los avisos de opciones
 * deprecadas los escribe la biblioteca nativa en el descriptor 2 del proceso,
 * fuera del alcance de `System.err`.
 */
internal object LibVlcProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        uk.co.caprica.vlcj.factory.MediaPlayerFactory(listOf(args[0])).release()
    }
}
