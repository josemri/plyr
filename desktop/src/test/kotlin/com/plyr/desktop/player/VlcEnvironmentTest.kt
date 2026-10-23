package com.plyr.desktop.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests puros de [VlcEnvironment].
 *
 * No necesitan `libvlc` instalado: verifican la detección, la localización de
 * los plugins nativos y el mensaje de error cuando falta el motor.
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
    fun pluginDirOrNull_devuelveNullCuandoNoHayPlugins() {
        val dir = tmp.newFolder()
        assertNull(VlcEnvironment.pluginDirOrNull(dir))

        // Un directorio de plugins vacío no sirve: significa que los módulos
        // no están instalados (Debian reparte `libvlc5` y `vlc-plugin-base`).
        File(dir, "vlc/plugins").mkdirs()
        assertNull(VlcEnvironment.pluginDirOrNull(dir))

        File(File(dir, "vlc/plugins"), "libmp4_demux_plugin.so").createNewFile()
        assertNotNull(VlcEnvironment.pluginDirOrNull(dir))
    }

    @Test
    fun failureMessage_apuntaAVlcPluginBaseCuandoFaltanLosPlugins() {
        val dir = tmp.newFolder()
        val message = VlcEnvironment.failureMessage(dir)

        assertTrue(message.contains("vlc-plugin-base"))
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
        // La vía mínima, sin arrastrar la aplicación gráfica de VLC.
        assertTrue(message.contains("libvlc5 vlc-plugin-base"))
        assertTrue(message.contains(VlcEnvironment.DIR_ENV))
    }
}
