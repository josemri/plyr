package com.plyr.desktop.media

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.TypeRef
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.annotations.DBusProperty.Access
import org.freedesktop.dbus.annotations.PropertiesEmitsChangedSignal.EmitChangeSignal
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.atomic.AtomicReference

/** Constantes del protocolo MPRIS. */
internal object Mpris {
    const val BUS_NAME = "org.mpris.MediaPlayer2.plyr"
    const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
    const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
    const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
    const val IDENTITY = "plyr"
    const val DESKTOP_ENTRY = "plyr"
    const val PLAYING = "Playing"
    const val PAUSED = "Paused"
    const val STOPPED = "Stopped"
}

/**
 * Fuerza el tipo MPRIS `a{sv}` para la propiedad `Metadata`.
 *
 * Sin este `TypeRef`, dbus-java anunciaría `a{vv}` (claves y valores variantes)
 * y los clientes MPRIS esperan `a{sv}`.
 */
internal interface MprisMetadata : TypeRef<Map<String, Variant<*>>>

/** Fuerza el tipo MPRIS `as` para las listas de cadenas. */
internal interface MprisStringList : TypeRef<List<String>>

/**
 * `org.mpris.MediaPlayer2`: identidad y ciclo de vida del reproductor.
 *
 * Las propiedades se declaran con `@DBusProperty` (solo introspección) y se
 * sirven a mano desde [MprisPlayer]: con `@DBusBoundProperty` dbus-java
 * responde a `Properties.Get` con el tipo crudo en vez de un `Variant`, lo que
 * incumple la especificación y rompe clientes como `playerctl`.
 */
@DBusInterfaceName(Mpris.ROOT_INTERFACE)
@DBusProperty(name = "CanQuit", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanRaise", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "HasTrackList", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "Identity", type = String::class, access = Access.READ)
@DBusProperty(name = "DesktopEntry", type = String::class, access = Access.READ)
@DBusProperty(name = "SupportedUriSchemes", type = MprisStringList::class, access = Access.READ)
@DBusProperty(name = "SupportedMimeTypes", type = MprisStringList::class, access = Access.READ)
internal interface MediaPlayer2 : DBusInterface {
    fun Raise()

    fun Quit()
}

/** `org.mpris.MediaPlayer2.Player`: transporte y metadatos de la pista actual. */
@DBusInterfaceName(Mpris.PLAYER_INTERFACE)
@DBusProperty(name = "PlaybackStatus", type = String::class, access = Access.READ)
@DBusProperty(name = "LoopStatus", type = String::class, access = Access.READ)
@DBusProperty(name = "Rate", type = Double::class, access = Access.READ)
@DBusProperty(name = "Shuffle", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "Metadata", type = MprisMetadata::class, access = Access.READ)
@DBusProperty(name = "Volume", type = Double::class, access = Access.READ)
@DBusProperty(
    name = "Position",
    type = Long::class,
    access = Access.READ,
    emitChangeSignal = EmitChangeSignal.FALSE,
)
@DBusProperty(name = "MinimumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "MaximumRate", type = Double::class, access = Access.READ)
@DBusProperty(name = "CanGoNext", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPlay", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanPause", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanSeek", type = Boolean::class, access = Access.READ)
@DBusProperty(name = "CanControl", type = Boolean::class, access = Access.READ)
internal interface MediaPlayer2Player : DBusInterface {
    fun Next()

    fun Previous()

    fun Pause()

    fun PlayPause()

    fun Stop()

    fun Play()

    fun Seek(offsetMicroseconds: Long)

    fun SetPosition(trackId: DBusPath, positionMicroseconds: Long)

    fun OpenUri(uri: String)
}

/**
 * Objeto exportado en `/org/mpris/MediaPlayer2`.
 *
 * Mantiene el estado que leen los clientes (estado de reproducción, posición y
 * metadatos), traduce las llamadas MPRIS a [PlaybackControls] y sirve
 * `org.freedesktop.DBus.Properties` a mano para que `Get` devuelva un
 * `Variant` como manda la especificación. Es `internal`: solo
 * [MprisMediaIntegration] lo publica en el bus.
 */
internal class MprisPlayer(
    private val controls: () -> PlaybackControls?,
) : MediaPlayer2, MediaPlayer2Player, Properties {

    @Volatile
    private var playbackStatus: String = Mpris.STOPPED

    @Volatile
    private var metadata: Map<String, Variant<*>> = emptyMap()

    @Volatile
    private var positionMicroseconds: Long = 0L

    override fun getObjectPath(): String = Mpris.OBJECT_PATH

    override fun isRemote(): Boolean = false

    /** Actualiza el estado publicado; devuelve `true` si cambió el estado. */
    fun setPlayback(status: String, positionMicroseconds: Long): Boolean {
        val changed = playbackStatus != status
        playbackStatus = status
        this.positionMicroseconds = positionMicroseconds
        return changed
    }

    /** Actualiza los metadatos publicados; devuelve `true` si cambiaron. */
    fun setMetadata(metadata: Map<String, Variant<*>>): Boolean {
        val changed = this.metadata != metadata
        this.metadata = metadata
        return changed
    }

    // org.mpris.MediaPlayer2

    override fun Raise() = Unit

    override fun Quit() {
        controls()?.quit()
    }

    // org.mpris.MediaPlayer2.Player

    override fun Next() {
        controls()?.next()
    }

    override fun Previous() {
        controls()?.previous()
    }

    override fun Pause() {
        controls()?.pause()
    }

    override fun PlayPause() {
        controls()?.togglePlayPause()
    }

    override fun Stop() {
        controls()?.stop()
    }

    override fun Play() {
        controls()?.play()
    }

    override fun Seek(offsetMicroseconds: Long) {
        controls()?.seekTo((positionMicroseconds + offsetMicroseconds) / 1000)
    }

    override fun SetPosition(trackId: DBusPath, positionMicroseconds: Long) {
        controls()?.seekTo(positionMicroseconds / 1000)
    }

    override fun OpenUri(uri: String) = Unit

    // org.freedesktop.DBus.Properties

    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(interfaceName: String, propertyName: String): A =
        (propertyValue(propertyName) ?: throw UnknownProperty(propertyName)) as A

    override fun <A> Set(interfaceName: String, propertyName: String, value: A) {
        // Las propiedades son de solo lectura: volumen, loop y shuffle no se
        // controlan desde MPRIS en este hito.
        throw PropertyReadOnly(propertyName)
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
        val names = when (interfaceName) {
            Mpris.ROOT_INTERFACE -> ROOT_PROPERTIES
            Mpris.PLAYER_INTERFACE -> PLAYER_PROPERTIES
            else -> emptyList()
        }
        return buildMap {
            names.forEach { name -> propertyValue(name)?.let { put(name, it) } }
        }
    }

    /** Valor actual de cada propiedad MPRIS, envuelto en el `Variant` correcto. */
    private fun propertyValue(name: String): Variant<*>? = when (name) {
        "CanQuit" -> Variant(true)
        "CanRaise" -> Variant(false)
        "HasTrackList" -> Variant(false)
        "Identity" -> Variant(Mpris.IDENTITY)
        "DesktopEntry" -> Variant(Mpris.DESKTOP_ENTRY)
        "SupportedUriSchemes" -> Variant(SUPPORTED_URI_SCHEMES, "as")
        "SupportedMimeTypes" -> Variant(SUPPORTED_MIME_TYPES, "as")
        "PlaybackStatus" -> Variant(playbackStatus)
        "LoopStatus" -> Variant("None")
        "Rate" -> Variant(1.0)
        "Shuffle" -> Variant(false)
        "Metadata" -> Variant(metadata, "a{sv}")
        "Volume" -> Variant(1.0)
        "Position" -> Variant(positionMicroseconds)
        "MinimumRate" -> Variant(1.0)
        "MaximumRate" -> Variant(1.0)
        "CanGoNext" -> Variant(true)
        "CanGoPrevious" -> Variant(true)
        "CanPlay" -> Variant(true)
        "CanPause" -> Variant(true)
        "CanSeek" -> Variant(true)
        "CanControl" -> Variant(true)
        else -> null
    }

    private companion object {
        val ROOT_PROPERTIES = listOf(
            "CanQuit",
            "CanRaise",
            "HasTrackList",
            "Identity",
            "DesktopEntry",
            "SupportedUriSchemes",
            "SupportedMimeTypes",
        )

        val PLAYER_PROPERTIES = listOf(
            "PlaybackStatus",
            "LoopStatus",
            "Rate",
            "Shuffle",
            "Metadata",
            "Volume",
            "Position",
            "MinimumRate",
            "MaximumRate",
            "CanGoNext",
            "CanGoPrevious",
            "CanPlay",
            "CanPause",
            "CanSeek",
            "CanControl",
        )

        val SUPPORTED_URI_SCHEMES = listOf("http", "https", "file")

        val SUPPORTED_MIME_TYPES = listOf(
            "audio/mpeg",
            "audio/mp4",
            "audio/ogg",
            "audio/flac",
            "audio/x-wav",
            "audio/webm",
        )
    }
}

/**
 * Integración MPRIS de Linux (M13).
 *
 * Publica `org.mpris.MediaPlayer2.plyr` en el bus de sesión, exporta
 * [MprisPlayer] en `/org/mpris/MediaPlayer2` y emite `PropertiesChanged` cada
 * vez que cambia la pista o el estado. Si no hay bus de sesión (contenedores,
 * Xvfb sin `dbus-daemon`...) falla en silencio y queda inactiva, sin romper la
 * app.
 */
class MprisMediaIntegration : DesktopMediaIntegration {

    override val backendName: String = "mpris"

    private val controlsRef = AtomicReference<PlaybackControls?>(null)
    private var connection: DBusConnection? = null
    private var player: MprisPlayer? = null

    override val isActive: Boolean get() = connection != null

    override fun attach(controls: PlaybackControls) {
        controlsRef.set(controls)
        if (connection != null) return
        try {
            val bus = DBusConnectionBuilder.forSessionBus().build()
            bus.requestBusName(Mpris.BUS_NAME)
            val mprisPlayer = MprisPlayer { controlsRef.get() }
            bus.exportObject(Mpris.OBJECT_PATH, mprisPlayer)
            connection = bus
            player = mprisPlayer
        } catch (e: Exception) {
            connection?.let { runCatching { it.disconnect() } }
            connection = null
            player = null
        }
    }

    override fun updateNowPlaying(info: MediaInfo?, isPlaying: Boolean, positionMs: Long) {
        val mprisPlayer = player ?: return
        val status = when {
            info == null -> Mpris.STOPPED
            isPlaying -> Mpris.PLAYING
            else -> Mpris.PAUSED
        }
        val metadata = info?.toMprisMetadata() ?: emptyMap()
        val positionMicroseconds = positionMs.coerceAtLeast(0L) * 1000

        val statusChanged = mprisPlayer.setPlayback(status, positionMicroseconds)
        val metadataChanged = mprisPlayer.setMetadata(metadata)
        // La posición cambia a cada tick y no se anuncia (lo marca
        // `EmitsChangedSignal=false`); solo se avisa de estado y metadatos.
        if (statusChanged || metadataChanged) {
            emitPropertiesChanged(status, metadata)
        }
    }

    override fun close() {
        player = null
        val bus = connection ?: return
        connection = null
        runCatching { bus.disconnect() }
    }

    /** Emite `PropertiesChanged` para que los clientes refresquen su caché. */
    private fun emitPropertiesChanged(status: String, metadata: Map<String, Variant<*>>) {
        val bus = connection ?: return
        runCatching {
            val changed = mapOf(
                "PlaybackStatus" to Variant(status),
                "Metadata" to Variant(metadata, "a{sv}"),
            )
            bus.sendMessage(
                Properties.PropertiesChanged(
                    Mpris.OBJECT_PATH,
                    Mpris.PLAYER_INTERFACE,
                    changed,
                    emptyList(),
                ),
            )
        }
    }
}

/** Traduce [MediaInfo] al diccionario `Metadata` de MPRIS (`a{sv}`). */
internal fun MediaInfo.toMprisMetadata(): Map<String, Variant<*>> = buildMap {
    put("mpris:trackid", Variant(DBusPath(trackId), "o"))
    put("xesam:title", Variant(title))
    put("xesam:artist", Variant(listOf(artist), "as"))
    album?.takeIf { it.isNotBlank() }?.let { put("xesam:album", Variant(it)) }
    if (durationMs > 0L) {
        put("mpris:length", Variant(durationMs * 1000, "x"))
    }
    artUri?.takeIf { it.isNotBlank() }?.let { put("mpris:artUrl", Variant(it)) }
}
