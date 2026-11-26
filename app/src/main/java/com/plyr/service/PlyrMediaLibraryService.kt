package com.plyr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.plyr.database.PlaylistLocalRepository
import com.plyr.network.YouTubeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Servicio de biblioteca multimedia para Android Auto.
 *
 * Se enlaza al [MusicService] (dueño del reproductor) y expone un árbol
 * navegable con las listas y pistas locales. La reproducción se delega al
 * reproductor compartido. Las consultas a BD/red se hacen en un scope de IO
 * para no bloquear el hilo del binder.
 */
class PlyrMediaLibraryService : MediaLibraryService() {

    private var musicService: MusicService? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    private val sessionLatch = CountDownLatch(1)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            val service = (binder as? MusicService.MusicBinder)?.getService() ?: run { Log.w(TAG, "binder invalido"); return }
            musicService = service
            val player = service.audioPlayer?.let { (it as? com.plyr.player.ExoAudioPlayer)?.player } ?: run { Log.w(TAG, "player null"); return }
            if (mediaLibrarySession == null) {
                mediaLibrarySession = MediaLibrarySession.Builder(this@PlyrMediaLibraryService, player, LibraryCallback())
                    .setId("plyr")
                    .build()
                Log.d(TAG, "session creada")
            }
            sessionLatch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureForeground()
        Intent(this, MusicService::class.java).also {
            ContextCompat.startForegroundService(this@PlyrMediaLibraryService, it)
            bindService(it, connection, BIND_AUTO_CREATE)
        }
    }

    private fun ensureForeground() {
        val channelId = "plyr_auto"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(NotificationChannel(channelId, "plyr", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("plyr")
            .setContentText("Android Auto")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        if (mediaLibrarySession == null) {
            try { sessionLatch.await(2, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
        }
        Log.d(TAG, "onGetSession session=${mediaLibrarySession != null}")
        return mediaLibrarySession
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try { unbindService(connection) } catch (_: Exception) { }
        musicService = null
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
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
                        .setTitle("plyr")
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
            val future = SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
            serviceScope.launch {
                try {
                    Log.d(TAG, "onGetChildren parentId=$parentId")
                    val items = when (parentId) {
                    ROOT_ID -> listOf(
                        MediaItem.Builder()
                            .setMediaId(PLAYLISTS_NODE)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setTitle("Listas")
                                    .build()
                            )
                            .build()
                    )
                    PLAYLISTS_NODE -> {
                        val repo = PlaylistLocalRepository(this@PlyrMediaLibraryService)
                        repo.getAllPlaylists().map { pl ->
                            MediaItem.Builder()
                                .setMediaId("pl_${pl.remoteId}")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle(pl.name)
                                        .build()
                                )
                                .build()
                        }
                    }
                    else -> {
                        if (parentId.startsWith("pl_")) {
                            val playlistId = parentId.removePrefix("pl_")
                            val repo = PlaylistLocalRepository(this@PlyrMediaLibraryService)
                            repo.getTracksByPlaylistSync(playlistId).map { tr ->
                                MediaItem.Builder()
                                    .setMediaId("tr_${tr.id}")
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setTitle(tr.name)
                                            .setArtist(tr.artists)
                                            .build()
                                    )
                                    .build()
                            }
                        } else emptyList()
                    }
                }
                    future.set(LibraryResult.ofItemList(items, params))
                } catch (t: Throwable) {
                    Log.e(TAG, "onGetChildren error parentId=$parentId", t)
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
        }

        private suspend fun findTrack(trackId: String): com.plyr.database.TrackEntity? {
            val repo = PlaylistLocalRepository(this@PlyrMediaLibraryService)
            for (pl in repo.getAllPlaylists()) {
                val found = repo.getTracksByPlaylistSync(pl.remoteId).firstOrNull { it.id == trackId }
                if (found != null) return found
            }
            return null
        }

        @OptIn(UnstableApi::class)
        @SuppressLint("UnsafeOptInUsageError")
        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    Log.d(TAG, "onSetMediaItems count=${mediaItems.size}")
                    val resolved = mediaItems.map { item ->
                    if (item.mediaId.startsWith("tr_")) {
                        val trackId = item.mediaId.removePrefix("tr_")
                        val track = findTrack(trackId)
                        val videoId = track?.youtubeVideoId
                        val url = videoId?.let { YouTubeManager.getAudioUrl(it) }
                        if (url != null) {
                            item.buildUpon().setUri(url).build()
                        } else {
                            item
                        }
                    } else {
                        item
                    }
                }
                    future.set(MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs))
                } catch (t: Throwable) {
                    Log.e(TAG, "onSetMediaItems error", t)
                    future.setException(t)
                }
            }
            return future
        }
    }

    companion object {
        const val ROOT_ID = "root"
        const val PLAYLISTS_NODE = "playlists"
        private const val TAG = "PlyrMediaLib"
    }
}