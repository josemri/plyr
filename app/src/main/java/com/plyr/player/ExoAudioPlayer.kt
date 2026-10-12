package com.plyr.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * `AudioPlayer` de Android sobre ExoPlayer.
 *
 * Es el **único** sitio de la lógica de cola que menciona ExoPlayer: traduce
 * `MediaItem`/`Player.STATE_*`/`PlaybackException` a los tipos neutrales de
 * `:core` (`AudioTrack`, [PlaybackState], `Throwable`), de modo que
 * `PlayerViewModel` y la lógica de ventana puedan testearse en JVM y reutilizarse
 * con VLCJ en escritorio.
 *
 * Expone el `ExoPlayer` subyacente para lo que es intrínsecamente Android: la
 * `MediaSession` de la notificación y la sesión de audio del sistema.
 */
class ExoAudioPlayer(
    context: Context,
    repeatOne: Boolean,
) : AudioPlayer {

    @OptIn(UnstableApi::class)
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .build()
        .apply {
            // El salto entre canciones lo decide la cola de la app, no ExoPlayer.
            // Solo "repetir uno" se delega: así el repeat es continuo y sin cortes.
            repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }

    private var listener: AudioPlayerListener? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                listener?.onPlaybackStateChanged(playbackState.toNeutral())
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                listener?.onMediaItemTransition(
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onPlayerError(error)
            }
        })
    }

    override val mediaItemCount: Int get() = player.mediaItemCount
    override val currentMediaItemIndex: Int get() = player.currentMediaItemIndex
    override val currentPosition: Long get() = player.currentPosition
    override val duration: Long get() = player.duration
    override val isPlaying: Boolean get() = player.isPlaying
    override val playbackState: PlaybackState get() = player.playbackState.toNeutral()

    override fun setListener(listener: AudioPlayerListener) {
        this.listener = listener
    }

    override fun setMediaItems(items: List<AudioTrack>, startIndex: Int) {
        player.setMediaItems(items.map(::toMediaItem), startIndex, C.TIME_UNSET)
    }

    override fun addMediaItems(items: List<AudioTrack>) {
        player.addMediaItems(items.map(::toMediaItem))
    }

    override fun removeMediaItems(fromIndex: Int, toExclusive: Int) {
        player.removeMediaItems(fromIndex, toExclusive)
    }

    override fun prepare() = player.prepare()

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    override fun clearMediaItems() {
        player.clearMediaItems()
    }

    override fun seekTo(index: Int, positionMs: Long) {
        player.seekTo(index, if (positionMs == AudioPlayer.TIME_UNSET) C.TIME_UNSET else positionMs)
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun seekForward() = player.seekForward()

    override fun seekBack() = player.seekBack()

    override fun setRepeatOne(enabled: Boolean) {
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun release() {
        player.release()
    }

    private fun toMediaItem(track: AudioTrack): MediaItem =
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.mediaId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .build()
            )
            .build()

    private fun Int.toNeutral(): PlaybackState = when (this) {
        Player.STATE_IDLE -> PlaybackState.IDLE
        Player.STATE_BUFFERING -> PlaybackState.BUFFERING
        Player.STATE_READY -> PlaybackState.READY
        Player.STATE_ENDED -> PlaybackState.ENDED
        else -> PlaybackState.IDLE
    }

    private companion object {
        /** Incremento de los botones de avance/retroceso (10 s). */
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
