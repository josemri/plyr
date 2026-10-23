package com.plyr.desktop.player

import com.plyr.player.AudioPlayer
import com.plyr.player.AudioPlayerListener
import com.plyr.player.AudioTrack
import com.plyr.player.PlaybackState
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.io.File

/**
 * `AudioPlayer` de escritorio sobre VLCJ (`EmbeddedMediaPlayer`, solo audio).
 *
 * Es el equivalente multiplataforma de `ExoAudioPlayer`: mantiene una *ventana*
 * de pistas resuelta por la app y avanza solo de una a la siguiente, igual que
 * haría ExoPlayer, de modo que la lógica de cola (`QueueEngine`) no note la
 * diferencia. VLCJ no tiene un "media item list" equivalente al de media3, así
 * que la lista se gestiona aquí y cada salto recarga la URL en VLC.
 *
 * Requiere `libvlc` instalado. Si no está, el constructor lanza
 * [VlcEnvironment.LibVlcMissingException] con instrucciones (M4 pide detectarlo
 * y avisar con claridad).
 */
class VlcAudioPlayer(
    initialListener: AudioPlayerListener? = null,
    libraryDir: File? = VlcEnvironment.findLibraryDir(),
) : AudioPlayer {

    internal companion object {
        /**
         * Opciones de instancia para [MediaPlayerFactory]: ruido.
         *
         * **No poner aquí opciones de red.** La familia `ipv4`/`ipv6` está
         * eliminada en VLC 3: pasarlas solo deja un «Warning: option --ipv6 no
         * longer exists» y no cambia nada. Y `libvlc` no lanza excepción ante
         * una opción muerta —avisa por stderr y sigue—, así que un test que
         * solo compruebe «no lanzó» las daría por buenas. Ver
         * [VlcMediaOptionsTest], que valida esto capturando stderr.
         */
        val FACTORY_OPTIONS: List<String> = listOf(
            // Los fallos de red son esperados y ruidosos: se cuentan y se
            // reintentan, pero no inundan la consola.
            "--quiet",
        )

        /**
         * Tope de espera a una dirección IPv6 antes de caer a IPv4, como
         * opción **de media**.
         *
         * `libvlc` consulta esta opción al abrir cada entrada, así que desde
         * aquí sí influye aunque su nombre esté en el grupo `ipv4`. Es la vía
         * que queda: la opción de instancia equivalente está eliminada.
         */
        const val IPV4_TIMEOUT_MS = 2_000

        /**
         * Opciones de red.
         *
         * El DNS puede anunciar direcciones IPv6 (`AAAA`) aunque la red no
         * tenga ninguna ruta IPv6 —habitual en conexiones solo IPv4—. Sin un
         * tope, `libvlc` puede esperar a la dirección IPv6 antes de probar la
         * IPv4. Con 2 s se comporta como el *Happy Eyeballs* de un navegador y
         * cae a IPv4 enseguida.
         *
         * El búfer de 1 s acorta la vida útil de la URL: las de `googlevideo`
         * llevan `expire` y caducan al resolverlas, así que cuanto menos se
         * guarde, menos pistas fallan por caducidad.
         */
        val NETWORK_OPTIONS: Array<String> = arrayOf(
            ":ipv4-timeout=$IPV4_TIMEOUT_MS",
            ":network-caching=1000",
        )
        /**
         * Pista de audio descargada (fichero local).
         *
         * El MP4 fragmentado (DASH) que se guarda al descargar no es un `.m4a`
         * contenedor clásico: su primer fragmento puede no empezar en el número
         * de secuencia 0 y el `mp4 demux` propio de VLC lo acusa («Fragment
         * sequence discontinuity detected», «mjpeg demux error: cannot peek»).
         * `avformat` (libavformat) tolera MP4 fragmentado mucho mejor.
         *
         * `:no-video` se queda: hay casos en los que VLC elige pese a todo una
         * pista de imagen y llena la consola de errores de demux.
         */
        val LOCAL_OPTIONS: Array<String> = arrayOf(":no-video", ":demux=avformat")

        /**
         * Audio por red.
         *
         * `googlevideo` también sirve MP4 fragmentado, así que lleva el mismo
         * `:demux=avformat` que los ficheros locales, más los ajustes de red.
         */
        val STREAM_OPTIONS: Array<String> = arrayOf(":no-video", ":demux=avformat") + NETWORK_OPTIONS

        /** Incremento de avance/retroceso, alineado con el de Android. */
        const val SKIP_MS = 10_000L

        /**
         * Opciones de instancia para [MediaPlayerFactory]. Se exponen para
         * poder validarlas contra `libvlc` en los tests.
         */
        val instanceOptions: List<String> get() = FACTORY_OPTIONS

        /** Opciones por media para [uri] (`file:` = local; el resto, red). */
        fun mediaOptionsFor(uri: String): Array<String> =
            if (uri.startsWith("file:")) LOCAL_OPTIONS else STREAM_OPTIONS
    }

    /**
     * Opciones de `libvlc` para [uri]: las de fichero local y las de red no son
     * las mismas, así que se eligen por el esquema.
     */
    private fun mediaOptions(uri: String): Array<String> = mediaOptionsFor(uri)

    private val stateMachine = VlcPlaybackStateMachine(initialListener)
    private val factory: MediaPlayerFactory
    private val player: EmbeddedMediaPlayer

    private val items = mutableListOf<AudioTrack>()
    private var currentIndex = 0
    private var repeatOne = false

    /** `mediaId` cargado ahora mismo en VLC, para no recargar de más. */
    private var loadedMediaId: String? = null

    /** Posición a aplicar cuando el item arranque (tras un `seekTo(index, pos)`). */
    private var pendingPosition: Long? = null

    private val listener: AudioPlayerListener? get() = stateMachine.listener

    // --- Eventos de VLCJ ---

    private val events = object : MediaPlayerEventAdapter() {
        override fun opening(mediaPlayer: MediaPlayer) = stateMachine.onOpening()

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = stateMachine.onBuffering()

        override fun playing(mediaPlayer: MediaPlayer) {
            stateMachine.onPlaying()
            applyPendingPosition()
        }

        override fun paused(mediaPlayer: MediaPlayer) = stateMachine.onPaused()

        override fun stopped(mediaPlayer: MediaPlayer) = stateMachine.onStopped()

        override fun finished(mediaPlayer: MediaPlayer) = onFinished()

        override fun error(mediaPlayer: MediaPlayer) {
            stateMachine.onError()
            listener?.onPlayerError(IllegalStateException("VLC no pudo reproducir la pista actual"))
        }
    }

    init {
        val dir = libraryDir
            ?: throw VlcEnvironment.LibVlcMissingException(VlcEnvironment.missingMessage())

        // JNA necesita saber dónde está libvlc. Sus plugins no se le indican:
        // `libvlc` los busca en su ruta compilada, que es la misma que la de
        // esta instalación. Antes se le pasaba `--plugin-path`, opción que
        // VLC ≥ 3 eliminó y que solo dejaba un «Warning: option --plugin-path
        // no longer exists» por cada carga de pista.
        System.setProperty("jna.library.path", VlcEnvironment.librarySearchPath(dir))

        factory = try {
            MediaPlayerFactory(FACTORY_OPTIONS)
        } catch (t: Throwable) {
            // La causa se propaga en el mensaje: sin ella, cualquier fallo de
            // inicialización (faltan plugins, conflicto de JNA, versión
            // incompatible) aparecía como «No se encontró libvlc», que es
            // mentira y había llevado a instalar VLC sin resultado.
            throw VlcEnvironment.LibVlcMissingException(
                VlcEnvironment.failureMessage(dir) +
                    "\nDirectorio probado: ${dir.absolutePath}" +
                    "\nCausa: ${t.javaClass.name}: ${t.message}",
                t,
            )
        }
        player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        player.events().addMediaPlayerEventListener(events)
    }

    // --- AudioPlayer ---

    override val mediaItemCount: Int get() = items.size

    override val currentMediaItemIndex: Int get() = currentIndex

    override val currentPosition: Long
        get() = player.status().time().coerceAtLeast(0L)

    override val duration: Long
        get() = player.status().length().coerceAtLeast(0L)

    override val isPlaying: Boolean get() = stateMachine.isPlaying

    override val playbackState: PlaybackState get() = stateMachine.state

    override fun setListener(listener: AudioPlayerListener) {
        stateMachine.listener = listener
    }

    @Synchronized
    override fun setMediaItems(items: List<AudioTrack>, startIndex: Int) {
        this.items.clear()
        this.items.addAll(items)
        currentIndex = if (this.items.isEmpty()) 0 else startIndex.coerceIn(0, this.items.size - 1)
        loadedMediaId = null
        pendingPosition = null
    }

    @Synchronized
    override fun addMediaItems(items: List<AudioTrack>) {
        this.items.addAll(items)
    }

    @Synchronized
    override fun removeMediaItems(fromIndex: Int, toExclusive: Int) {
        if (fromIndex < 0 || toExclusive > items.size || fromIndex >= toExclusive) return
        val removedBeforeCurrent = (toExclusive.coerceAtMost(currentIndex)) - fromIndex
        items.subList(fromIndex, toExclusive).clear()
        if (removedBeforeCurrent > 0) currentIndex -= removedBeforeCurrent
        currentIndex = currentIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    @Synchronized
    override fun prepare() {
        val track = items.getOrNull(currentIndex) ?: return
        if (loadedMediaId == track.mediaId) return
        player.media().prepare(track.uri, *mediaOptions(track.uri))
        loadedMediaId = track.mediaId
    }

    @Synchronized
    override fun play() {
        val track = items.getOrNull(currentIndex) ?: return
        if (loadedMediaId == track.mediaId) {
            player.controls().play()
        } else {
            player.media().play(track.uri, *mediaOptions(track.uri))
            loadedMediaId = track.mediaId
        }
    }

    override fun pause() {
        player.controls().setPause(true)
    }

    @Synchronized
    override fun stop() {
        player.controls().stop()
        loadedMediaId = null
    }

    @Synchronized
    override fun clearMediaItems() {
        player.controls().stop()
        items.clear()
        currentIndex = 0
        loadedMediaId = null
        pendingPosition = null
    }

    @Synchronized
    override fun seekTo(index: Int, positionMs: Long) {
        if (index in items.indices) currentIndex = index
        // La ventana de VLCJ es una sola URL cargada: el salto recarga el item.
        loadedMediaId = null
        pendingPosition = positionMs.takeIf { it != AudioPlayer.TIME_UNSET }
    }

    override fun seekTo(positionMs: Long) {
        player.controls().setTime(positionMs.coerceAtLeast(0L))
    }

    override fun seekForward() {
        player.controls().skipTime(SKIP_MS)
    }

    override fun seekBack() {
        player.controls().skipTime(-SKIP_MS)
    }

    override fun setRepeatOne(enabled: Boolean) {
        repeatOne = enabled
    }

    override fun release() {
        player.events().removeMediaPlayerEventListener(events)
        player.release()
        factory.release()
    }

    // --- Eventos de VLCJ ---

    /**
     * Al terminar una pista, decide qué hacer, igual que la ventana de ExoPlayer:
     * repetir la actual, saltar a la siguiente de la ventana o marcar el final.
     */
    @Synchronized
    private fun onFinished() {
        val index = currentIndex
        when {
            repeatOne -> restartCurrent()
            index + 1 < items.size -> {
                currentIndex = index + 1
                loadedMediaId = null
                play()
                listener?.onMediaItemTransition(true)
            }
            else -> stateMachine.onFinished()
        }
    }

    @Synchronized
    private fun restartCurrent() {
        val track = items.getOrNull(currentIndex) ?: return
        player.media().play(track.uri, *mediaOptions(track.uri))
        loadedMediaId = track.mediaId
    }

    @Synchronized
    private fun applyPendingPosition() {
        val position = pendingPosition ?: return
        pendingPosition = null
        player.controls().setTime(position)
    }
}
