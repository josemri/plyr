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

    private companion object {
        /** Opciones por media: es un reproductor de audio, no de vídeo. */
        val MEDIA_OPTIONS = arrayOf(":no-video")

        /** Incremento de avance/retroceso, alineado con el de Android. */
        const val SKIP_MS = 10_000L
    }

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

        // JNA necesita saber dónde está libvlc; VLC, dónde están sus plugins.
        System.setProperty("jna.library.path", VlcEnvironment.librarySearchPath(dir))

        factory = try {
            MediaPlayerFactory(VlcEnvironment.factoryArgs(dir))
        } catch (t: Throwable) {
            throw VlcEnvironment.LibVlcMissingException(
                VlcEnvironment.missingMessage() + "\nDirectorio probado: ${dir.absolutePath}",
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
        player.media().prepare(track.uri, *MEDIA_OPTIONS)
        loadedMediaId = track.mediaId
    }

    @Synchronized
    override fun play() {
        val track = items.getOrNull(currentIndex) ?: return
        if (loadedMediaId == track.mediaId) {
            player.controls().play()
        } else {
            player.media().play(track.uri, *MEDIA_OPTIONS)
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
        player.media().play(track.uri, *MEDIA_OPTIONS)
        loadedMediaId = track.mediaId
    }

    @Synchronized
    private fun applyPendingPosition() {
        val position = pendingPosition ?: return
        pendingPosition = null
        player.controls().setTime(position)
    }
}
