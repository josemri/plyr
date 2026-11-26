package com.plyr.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
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
import android.util.Log

/**
 * Servicio de biblioteca multimedia para Android Auto.
 */
class PlyrMediaLibraryService : MediaLibraryService() {

    private var player: androidx.media3.exoplayer.ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("UnsafeCallOnNullableType")
    override fun onCreate() {
        super.onCreate()
        ensureForeground()
        player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        mediaLibrarySession = MediaLibrarySession.Builder(this, player!!, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun ensureForeground() {
        val channelId = "plyr_auto"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(NotificationChannel(channelId, "plyr", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("plyr")
            .setContentText("Android Auto")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
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
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        @Suppress("LongMethod")
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
                    Log.d(TAG, "onGetChildren parentId=$parentId page=$page size=$pageSize")
                    val all = when (parentId) {
                        ROOT_ID -> listOf(
                            MediaItem.Builder()
                                .setMediaId(PLAYLISTS_NODE)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle("Listas")
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
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
                                            .setSubtitle("${pl.trackCount} pistas")
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
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
                                    val artwork = tr.youtubeVideoId?.let {
                                        android.net.Uri.parse("https://img.youtube.com/vi/$it/mqdefault.jpg")
                                    }
                                    MediaItem.Builder()
                                        .setMediaId("tr_${tr.id}")
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setIsBrowsable(false)
                                                .setIsPlayable(true)
                                                .setTitle(tr.name)
                                                .setArtist(tr.artists)
                                                .setArtworkUri(artwork)
                                                .build()
                                        )
                                        .build()
                                }
                            } else emptyList()
                        }
                    }
                    val start = (page * pageSize).coerceAtMost(all.size)
                    val end = (start + pageSize).coerceAtMost(all.size)
                    val pageItems = all.subList(start, end)
                    future.set(LibraryResult.ofItemList(pageItems, params))
                } catch (t: Throwable) {
                    Log.e(TAG, "onGetChildren error parentId=$parentId", t)
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        @Suppress("LongMethod")
        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                try {
                    val item = when {
                        mediaId == ROOT_ID -> MediaItem.Builder()
                            .setMediaId(ROOT_ID)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setTitle("plyr")
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                    .build()
                            )
                            .build()
                        mediaId == PLAYLISTS_NODE -> MediaItem.Builder()
                            .setMediaId(PLAYLISTS_NODE)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setIsBrowsable(true)
                                    .setIsPlayable(false)
                                    .setTitle("Listas")
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                                    .build()
                            )
                            .build()
                        mediaId.startsWith("pl_") -> {
                            val plId = mediaId.removePrefix("pl_")
                            val repo = PlaylistLocalRepository(this@PlyrMediaLibraryService)
                            val pl = repo.getAllPlaylists().firstOrNull { it.remoteId == plId }
                            if (pl != null) MediaItem.Builder()
                                .setMediaId(mediaId)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setTitle(pl.name)
                                        .setSubtitle("${pl.trackCount} pistas")
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build()
                                )
                                .build()
                            else null
                        }
                        mediaId.startsWith("tr_") -> {
                            val trId = mediaId.removePrefix("tr_")
                            val track = findTrack(trId)
                            if (track != null) {
                                val artwork = track.youtubeVideoId?.let {
                                    android.net.Uri.parse("https://img.youtube.com/vi/$it/mqdefault.jpg")
                                }
                                MediaItem.Builder()
                                    .setMediaId(mediaId)
                                    .setMediaMetadata(
                                        MediaMetadata.Builder()
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setTitle(track.name)
                                            .setArtist(track.artists)
                                            .setArtworkUri(artwork)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                            .build()
                                    )
                                    .build()
                            } else null
                        }
                        else -> null
                    }
                    if (item != null) {
                        future.set(LibraryResult.ofItem(item, null))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                } catch (t: Throwable) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
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