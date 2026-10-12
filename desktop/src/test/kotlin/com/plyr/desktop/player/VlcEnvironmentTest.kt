package com.plyr.desktop.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests puros de [VlcEnvironment].
 *
 * No necesitan `libvlc` instalado: verifican la detección, los argumentos que
 * se le pasan a VLCJ y el mensaje de error cuando falta el motor nativo.
 */
class VlcEnvironmentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun containsLibVlc_detectaLaBibliotecaNativa() {
        val dir = tmp.newFolder()
        assertFalse(VlcEnvironment.containsLibVlc(dir))

        File(dir, "libvlc.so.5").createNewFile()
        assertTrue(VlcEnvironment.containsLibVlc(dir))
    }

    @Test
    fun containsLibVlc_ignoraDirectoriosInexistentes() {
        assertFalse(VlcEnvironment.containsLibVlc(File(tmp.root, "no-existe")))
    }

    @Test
    fun factoryArgs_apuntaALosPluginsSoloSiExisten() {
        val dir = tmp.newFolder()
        assertTrue(VlcEnvironment.factoryArgs(dir).isEmpty())

        File(dir, "vlc/plugins").mkdirs()
        val args = VlcEnvironment.factoryArgs(dir)

        assertTrue(args.single().startsWith("--plugin-path="))
    }

    @Test
    fun librarySearchPath_incluyeElDirectorioYSuSubcarpetaVlc() {
        val dir = tmp.newFolder()
        val path = VlcEnvironment.librarySearchPath(dir)

        assertTrue(path.contains(dir.absolutePath))
        assertTrue(path.contains(File(dir, "vlc").absolutePath))
    }

    @Test
    fun missingMessage_explicaComoInstalarLibVlc() {
        val message = VlcEnvironment.missingMessage()

        assertTrue(message.contains("libvlc"))
        assertTrue(message.contains("apt-get install vlc"))
        assertTrue(message.contains(VlcEnvironment.DIR_ENV))
    }
}
