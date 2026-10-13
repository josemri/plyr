package com.plyr.desktop.media

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Integración real con el bus de sesión (M13).
 *
 * Publica `org.mpris.MediaPlayer2.plyr`, abre una **segunda** conexión como haría
 * un cliente (playerctl, GNOME...) y comprueba que:
 *  1. los metadatos y el estado salen por `org.freedesktop.DBus.Properties.GetAll`;
 *  2. las teclas multimedia llegan a los controles del reproductor.
 *
 * Se omite si no hay `dbus-daemon` de sesión (CI, contenedores...) o si el
 * nombre ya está tomado (la app corriendo en la misma sesión).
 */
class MprisMediaIntegrationTest {

    private var client: DBusConnection? = null
    private var integration: MprisMediaIntegration? = null

    private val controls = RecordingControls()

    @Before
    fun setUp() {
        val connection = try {
            DBusConnectionBuilder.forSessionBus().build()
        } catch (e: Exception) {
            null
        }
        assumeTrue("sin bus de sesión D-Bus: se omite la integración MPRIS", connection != null)
        client = connection
    }

    @After
    fun tearDown() {
        integration?.close()
        client?.disconnect()
        integration = null
        client = null
    }

    @Test
    fun publicaMetadatosYAtiendeElTransporte() {
        val media = MprisMediaIntegration()
        integration = media
        media.attach(controls)
        assumeTrue("no se pudo registrar org.mpris.MediaPlayer2.plyr", media.isActive)

        media.updateNowPlaying(
            MediaInfo(
                title = "Canción de prueba",
                artist = "Artista",
                album = "Álbum",
                durationMs = 2_000L,
            ),
            isPlaying = true,
            positionMs = 1_000L,
        )

        val bus = client!!
        val properties = bus.getRemoteObject(Mpris.BUS_NAME, Mpris.OBJECT_PATH, Properties::class.java)
        val all = properties.GetAll(Mpris.PLAYER_INTERFACE)

        assertEquals("Playing", all["PlaybackStatus"]?.value)
        assertEquals(1_000_000L, all["Position"]?.value)
        assertEquals(true, all["CanControl"]?.value)

        @Suppress("UNCHECKED_CAST")
        val metadata = all["Metadata"]?.value as Map<String, Variant<*>>
        assertEquals("Canción de prueba", metadata["xesam:title"]?.value)
        assertEquals(listOf("Artista"), metadata["xesam:artist"]?.value)
        assertEquals("Álbum", metadata["xesam:album"]?.value)

        // `playerctl` consulta propiedades sueltas con `Properties.Get`, que
        // debe responder con un `Variant` conforme a la especificación (no con
        // el tipo crudo, como haría `@DBusBoundProperty`). El proxy genérico de
        // dbus-java desenvuelve el variante, así que consultamos con una
        // interfaz que declara el retorno como `Any` para ver el tipo de cable.
        val rawProperties = bus.getRemoteObject(Mpris.BUS_NAME, Mpris.OBJECT_PATH, RawProperties::class.java)
        val status = rawProperties.Get(Mpris.PLAYER_INTERFACE, "PlaybackStatus")
        assertTrue("Properties.Get debe devolver un Variant", status is Variant<*>)
        assertEquals("Playing", (status as Variant<*>).value)

        val position = rawProperties.Get(Mpris.PLAYER_INTERFACE, "Position")
        assertTrue("Properties.Get debe devolver un Variant", position is Variant<*>)
        assertEquals(1_000_000L, (position as Variant<*>).value)

        val metadataValue = rawProperties.Get(Mpris.PLAYER_INTERFACE, "Metadata")
        assertTrue("Properties.Get debe devolver un Variant", metadataValue is Variant<*>)
        @Suppress("UNCHECKED_CAST")
        val metadataFromGet = (metadataValue as Variant<*>).value as Map<String, Variant<*>>
        assertEquals("Canción de prueba", metadataFromGet["xesam:title"]?.value)

        // Identidad de la raíz de MPRIS, que los clientes también consultan.
        val rootAll = properties.GetAll(Mpris.ROOT_INTERFACE)
        assertEquals("plyr", rootAll["Identity"]?.value)
        val identity = rawProperties.Get(Mpris.ROOT_INTERFACE, "Identity")
        assertTrue("Properties.Get debe devolver un Variant", identity is Variant<*>)
        assertEquals("plyr", (identity as Variant<*>).value)

        // El transporte cruza el bus y llega a los controles.
        val player = bus.getRemoteObject(Mpris.BUS_NAME, Mpris.OBJECT_PATH, MediaPlayer2Player::class.java)
        player.PlayPause()
        player.Next()
        player.Previous()

        assertEquals(listOf("toggle", "next", "previous"), controls.calls)
    }

    /**
     * Vista tipada de `org.freedesktop.DBus.Properties`: declara el retorno de
     * `Get` como `Any?` para observar el tipo de cable real (`v`) sin que el
     * proxy genérico de dbus-java desenvuelva el variante.
     */
    @DBusInterfaceName("org.freedesktop.DBus.Properties")
    private interface RawProperties : DBusInterface {
        fun Get(interfaceName: String, propertyName: String): Any?
    }

    /** Controles que solo anotan qué orden recibieron. */
    private class RecordingControls : PlaybackControls {
        val calls = mutableListOf<String>()

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun togglePlayPause() {
            calls += "toggle"
        }

        override fun next() {
            calls += "next"
        }

        override fun previous() {
            calls += "previous"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun seekTo(positionMs: Long) {
            calls += "seek($positionMs)"
        }

        override fun quit() {
            calls += "quit"
        }
    }
}
