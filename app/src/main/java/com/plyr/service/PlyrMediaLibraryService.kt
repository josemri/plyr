package com.plyr.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.plyr.player.AudioPlayer

/**
 * Servicio de biblioteca multimedia para Android Auto.
 *
 * No crea su propio reproductor: se enlaza al [MusicService], que es el dueño
 * del `ExoPlayer` (pasos 1 y 2 del refactor). Así Android Auto controla el
 * mismo reproductor que la app y la notificación.
 */
class PlyrMediaLibraryService : MediaLibraryService() {

    private var musicService: MusicService? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? MusicService.MusicBinder)?.getService() ?: return
            musicService = service
            val audioPlayer = service.audioPlayer ?: return
            if (mediaLibrarySession == null) {
                mediaLibrarySession = MediaLibrarySession.Builder(this@PlyrMediaLibraryService, audioPlayerToPlayer(audioPlayer), LibraryCallback()).build()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Arranca y enlaza el servicio dueño del reproductor.
        Intent(this, MusicService::class.java).also {
            startForegroundService(it)
            bindService(it, connection, BIND_AUTO_CREATE)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        try { unbindService(connection) } catch (_: Exception) { }
        musicService = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        super.onDestroy()
    }

    private fun audioPlayerToPlayer(audioPlayer: AudioPlayer): androidx.media3.common.Player {
        // El AudioPlayer de Android expone su ExoPlayer subyacente.
        return (audioPlayer as? com.plyr.player.ExoAudioPlayer)?.player
            ?: throw IllegalStateException("AudioPlayer no es ExoAudioPlayer")
    }

    private class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        }
    }

    companion object {
        const val ROOT_ID = "root"
    }
}