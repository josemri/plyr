package com.plyr.desktop.media

/**
 * Acciones que la integración nativa del escritorio (MPRIS o bandeja) puede
 * pedirle al reproductor.
 *
 * Es el punto de unión entre el sistema operativo y [com.plyr.desktop.player.PlayerController]:
 * la integración no conoce la cola ni el motor, solo estas órdenes.
 */
interface PlaybackControls {
    fun play()
    fun pause()
    fun togglePlayPause()
    fun next()
    fun previous()
    fun stop()
    fun seekTo(positionMs: Long)
    fun quit()
}

/** Metadatos de la pista actual que se publican en el escritorio (MPRIS). */
data class MediaInfo(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val trackId: String = DEFAULT_TRACK_ID,
    val artUri: String? = null,
) {
    companion object {
        const val DEFAULT_TRACK_ID = "/org/mpris/MediaPlayer2/Track/1"
    }
}

/**
 * Integración con el escritorio (M13): teclas multimedia, metadatos y bandeja.
 *
 * Linux se implementa con MPRIS sobre D-Bus ([MprisMediaIntegration]); Windows
 * (`SmtcIntegration`) y macOS (`NowPlayingIntegration`) son fases posteriores y
 * por ahora usan [NoopMediaIntegration]. La interfaz es intencionadamente
 * pequeña para que la UI solo tenga que llamar a [updateNowPlaying] cuando cambie
 * el reproductor.
 */
interface DesktopMediaIntegration {
    /** Nombre del backend activo (`mpris`, `none`...). */
    val backendName: String

    /** `true` si la integración está realmente escuchando en el bus. */
    val isActive: Boolean

    /** Registra los controles que atenderán las peticiones del sistema. */
    fun attach(controls: PlaybackControls)

    /** Publica la pista actual (o `null` si no hay nada) y el estado de reproducción. */
    fun updateNowPlaying(info: MediaInfo?, isPlaying: Boolean, positionMs: Long)

    /** Libera el bus y deja de atender peticiones. */
    fun close()
}

/** Implementación nula: no hay integración nativa todavía en esta plataforma. */
class NoopMediaIntegration(
    override val backendName: String = "none",
) : DesktopMediaIntegration {
    override val isActive: Boolean = false

    override fun attach(controls: PlaybackControls) = Unit

    override fun updateNowPlaying(info: MediaInfo?, isPlaying: Boolean, positionMs: Long) = Unit

    override fun close() = Unit
}

/** Crea la integración nativa propia del sistema operativo actual. */
fun createMediaIntegration(): DesktopMediaIntegration {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return if (os.contains("linux")) MprisMediaIntegration() else NoopMediaIntegration("unsupported")
}
