package com.plyr.desktop.media

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.russhwolf.settings.MapSettings
import org.freedesktop.dbus.annotations.DBusProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.awt.GraphicsEnvironment
import java.awt.TrayIcon
import java.awt.event.ActionEvent

/**
 * Tests puros de la integración de escritorio (M13): metadatos MPRIS, entradas
 * de la bandeja y anotaciones de las interfaces D-Bus.
 *
 * No abren el bus de sesión; las comprobaciones contra un `dbus-daemon` real
 * viven en [MprisMediaIntegrationTest].
 */
class DesktopMediaIntegrationTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        DesktopConfig.language = Language.ENGLISH
    }

    @Test
    fun toMprisMetadata_mapeaTodosLosCampos() {
        val metadata = MediaInfo(
            title = "Canción",
            artist = "Artista",
            album = "Álbum",
            durationMs = 1_500L,
            trackId = "/org/mpris/MediaPlayer2/Track/abc",
            artUri = "file:///cover.png",
        ).toMprisMetadata()

        assertEquals("/org/mpris/MediaPlayer2/Track/abc", metadata["mpris:trackid"]?.value?.toString())
        assertEquals("Canción", metadata["xesam:title"]?.value)
        assertEquals(listOf("Artista"), metadata["xesam:artist"]?.value)
        assertEquals("Álbum", metadata["xesam:album"]?.value)
        // MPRIS expresa la duración en microsegundos.
        assertEquals(1_500_000L, metadata["mpris:length"]?.value)
        assertEquals("file:///cover.png", metadata["mpris:artUrl"]?.value)
    }

    @Test
    fun toMprisMetadata_omiteAlbumDuracionYPortada() {
        val metadata = MediaInfo(title = "Solo título", artist = "Artista").toMprisMetadata()

        assertNull(metadata["xesam:album"])
        assertNull(metadata["mpris:length"])
        assertNull(metadata["mpris:artUrl"])
        // Los campos básicos siguen presentes.
        assertEquals("Solo título", metadata["xesam:title"]?.value)
        assertTrue("mpris:trackid" in metadata)
    }

    @Test
    fun trayActions_etiquetaYTieneCallbacks() {
        var playPause = 0
        var next = 0
        var previous = 0
        var quit = 0
        val actions = trayActions(
            onPlayPause = { playPause++ },
            onNext = { next++ },
            onPrevious = { previous++ },
            onQuit = { quit++ },
        )

        assertEquals(listOf("play/pause", "next", "previous", "Exit"), actions.map { it.label })

        actions.forEach { it.onClick() }
        assertEquals(1, playPause)
        assertEquals(1, next)
        assertEquals(1, previous)
        assertEquals(1, quit)
    }

    @Test
    fun createTrayImage_dibujaElTamanoPedido() {
        val image = createTrayImage(32)

        assertEquals(32, image.getWidth(null))
        assertEquals(32, image.getHeight(null))
    }

    @Test
    fun noopMediaIntegration_noEstaActivaYNoRompe() {
        val integration = NoopMediaIntegration("unsupported")

        assertEquals("unsupported", integration.backendName)
        assertFalse(integration.isActive)
        // Ninguna de estas llamadas debe lanzar.
        integration.attach(
            object : PlaybackControls {
                override fun play() = Unit
                override fun pause() = Unit
                override fun togglePlayPause() = Unit
                override fun next() = Unit
                override fun previous() = Unit
                override fun stop() = Unit
                override fun seekTo(positionMs: Long) = Unit
                override fun quit() = Unit
            },
        )
        integration.updateNowPlaying(MediaInfo("t", "a"), isPlaying = true, positionMs = 10L)
        integration.close()
    }

    @Test
    fun createMediaIntegration_enLinuxUsaMpris() {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val integration = createMediaIntegration()

        try {
            if (os.contains("linux")) {
                assertEquals("mpris", integration.backendName)
            } else {
                assertEquals("unsupported", integration.backendName)
            }
        } finally {
            integration.close()
        }
    }

    @Test
    fun interfacesDbus_declaranLasPropiedadesParaIntrospeccion() {
        // dbus-java deriva el XML de introspección de `@DBusProperty`; los
        // valores los sirve `MprisPlayer` a mano. Si estas anotaciones
        // desaparecen, `busctl introspect` deja de ver las propiedades.
        val player = MediaPlayer2Player::class.java.getAnnotationsByType(DBusProperty::class.java)
            .associateBy { it.name }
        assertEquals(String::class.java, player.getValue("PlaybackStatus").type.java)
        assertEquals(Boolean::class.java, player.getValue("CanControl").type.java)

        // `Metadata` debe anunciarse como `a{sv}` (no `a{vv}`) mediante su TypeRef.
        assertEquals(MprisMetadata::class.java, player.getValue("Metadata").type.java)

        val root = MediaPlayer2::class.java.getAnnotationsByType(DBusProperty::class.java)
            .associateBy { it.name }
        assertEquals(String::class.java, root.getValue("Identity").type.java)
        assertEquals(MprisStringList::class.java, root.getValue("SupportedUriSchemes").type.java)
    }

    @Test
    fun install_sinBandejaNoHaceNada() {
        var added = 0
        val tray = DesktopTray(actions = emptyList(), isSupported = { false }, addIcon = { added++ })

        assertFalse(tray.install())
        assertFalse(tray.isInstalled)
        assertEquals(0, added)
    }

    @Test
    fun install_agregaYQuitaElIconoUnaVez() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val added = mutableListOf<TrayIcon>()
        val removed = mutableListOf<TrayIcon>()
        val tray = DesktopTray(
            actions = listOf(TrayAction("Ping") {}),
            isSupported = { true },
            addIcon = { added += it },
            removeIcon = { removed += it },
        )

        assertTrue(tray.install())
        assertTrue(tray.isInstalled)
        assertEquals(1, added.size)
        // Instalar de nuevo no duplica el icono.
        assertTrue(tray.install())
        assertEquals(1, added.size)

        tray.remove()
        assertFalse(tray.isInstalled)
        assertEquals(1, removed.size)
    }

    @Test
    fun install_conectaCadaEntradaDelMenu() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        var clicks = 0
        val added = mutableListOf<TrayIcon>()
        val tray = DesktopTray(
            actions = listOf(TrayAction("Ping") { clicks++ }),
            isSupported = { true },
            addIcon = { added += it },
        )

        assertTrue(tray.install())

        val menu = added.single().popupMenu
        assertEquals(1, menu.itemCount)
        val item = menu.getItem(0)
        item.actionListeners.single().actionPerformed(
            ActionEvent(item, ActionEvent.ACTION_PERFORMED, "ping"),
        )
        assertEquals(1, clicks)
    }
}
