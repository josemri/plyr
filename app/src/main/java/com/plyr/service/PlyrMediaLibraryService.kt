package com.plyr.service

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.annotation.SuppressLint
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? MusicService.MusicBinder)?.getService() ?: return
            musicService = service
            val player = service.audioPlayer?.let { (it as? com.plyr.player.ExoAudioPlayer)?.player } ?: return
            if (mediaLibrarySession == null) {
                mediaLibrarySession = MediaLibrarySession.Builder(this@PlyrMediaLibraryService, player, LibraryCallback()).build()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        Intent(this, MusicService::class.java).also {
            ContextCompat.startForegroundService(this@PlyrMediaLibraryService, it)
            bindService(it, connection, BIND_AUTO_CREATE)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

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
            }
            return future
        }
    }

    companion object {
        const val ROOT_ID = "root"
        const val PLAYLISTS_NODE = "playlists"
    }
}