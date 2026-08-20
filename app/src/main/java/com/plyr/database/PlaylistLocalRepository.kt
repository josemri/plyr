package com.plyr.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class PlaylistLocalRepository(context: Context) {

    private val database = PlaylistDatabase.getDatabase(context)
    private val playlistDao = database.playlistDao()
    private val trackDao = database.trackDao()
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "PlaylistLocalRepo"
    }

    // === OBSERVATION ===

    fun getAllPlaylistsLiveData(): LiveData<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists().asLiveData()
    }

    fun getTracksByPlaylistLiveData(playlistId: String): LiveData<List<TrackEntity>> {
        return trackDao.getTracksByPlaylist(playlistId).asLiveData()
    }

    // === TRACK MANAGEMENT ===

    suspend fun updateTrackYoutubeId(trackId: String, youtubeVideoId: String) {
        trackDao.updateYoutubeVideoId(trackId, youtubeVideoId)
    }

    // === YOUTUBE PLAYLISTS ===

    suspend fun saveYouTubePlaylist(
        playlistId: String,
        title: String,
        description: String?,
        uploader: String,
        videoCount: Int,
        imageUrl: String?,
        tracks: List<TrackEntity>
    ): Boolean {
        val localPlaylistId = "youtube_$playlistId"
        return saveYouTubePlaylistWithTracks(
            PlaylistEntity(
                remoteId = localPlaylistId,
                name = title,
                description = "YouTube Playlist by $uploader",
                trackCount = tracks.size,
                imageUrl = imageUrl,
                lastSyncTime = System.currentTimeMillis()
            ),
            tracks
        )
    }

    suspend fun saveCreatedYouTubePlaylist(
        playlistId: String,
        title: String,
        description: String?,
        imageUrl: String?,
        tracks: List<TrackEntity>
    ): Boolean {
        val localPlaylistId = "youtube_$playlistId"
        return saveYouTubePlaylistWithTracks(
            PlaylistEntity(
                remoteId = localPlaylistId,
                name = title,
                description = description,
                trackCount = tracks.size,
                imageUrl = imageUrl,
                lastSyncTime = System.currentTimeMillis()
            ),
            tracks
        )
    }

    private suspend fun saveYouTubePlaylistWithTracks(
        playlist: PlaylistEntity,
        tracks: List<TrackEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            playlistDao.insertPlaylist(playlist)
            trackDao.deleteTracksByPlaylist(playlist.remoteId)
            trackDao.insertTracks(tracks)

            Log.d(TAG, "YouTube playlist guardada: ${playlist.name} (${tracks.size} tracks)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando YouTube playlist: ${e.message}", e)
            false
        }
    }

    suspend fun isYouTubePlaylistSaved(youtubePlaylistId: String): Boolean = withContext(Dispatchers.IO) {
        val localPlaylistId = "youtube_$youtubePlaylistId"
        val existing = playlistDao.getPlaylistById(localPlaylistId)
        existing != null
    }

    suspend fun deleteYouTubePlaylist(youtubePlaylistId: String) = withContext(Dispatchers.IO) {
        val localPlaylistId = "youtube_$youtubePlaylistId"
        trackDao.deleteTracksByPlaylist(localPlaylistId)
        playlistDao.deletePlaylistById(localPlaylistId)
        Log.d(TAG, "YouTube playlist eliminada: $localPlaylistId")
    }

    suspend fun updatePlaylistImage(
        localPlaylistId: String,
        imageUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlist = playlistDao.getPlaylistById(localPlaylistId)
                ?: return@withContext false
            playlistDao.updatePlaylist(
                playlist.copy(
                    imageUrl = imageUrl,
                    lastSyncTime = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Portada actualizada para playlist local: $localPlaylistId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando portada de playlist local: ${e.message}", e)
            false
        }
    }

    suspend fun getYouTubePlaylists(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        val allPlaylists = playlistDao.getAllPlaylistsSync()
        allPlaylists.filter { it.remoteId.startsWith("youtube_") }
    }

    suspend fun updatePlaylistDetails(
        localPlaylistId: String,
        newTitle: String?,
        newDesc: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlist = playlistDao.getPlaylistById(localPlaylistId)
                ?: return@withContext false
            playlistDao.updatePlaylist(
                playlist.copy(
                    name = newTitle ?: playlist.name,
                    description = newDesc ?: playlist.description,
                    lastSyncTime = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Playlist local actualizada: $localPlaylistId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando playlist local: ${e.message}", e)
            false
        }
    }

    suspend fun addTrackToYouTubePlaylist(
        localPlaylistId: String,
        track: TrackEntity
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = trackDao.getTracksByPlaylistSync(localPlaylistId)
            val nextPosition = if (existing.isNotEmpty()) existing.maxOf { it.position } + 1 else 0

            val newTrack = track.copy(
                id = "${localPlaylistId}_${track.remoteTrackId}_$nextPosition",
                playlistId = localPlaylistId,
                position = nextPosition
            )

            trackDao.insertTrack(newTrack)

            val playlist = playlistDao.getPlaylistById(localPlaylistId)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(trackCount = existing.size + 1))
            }

            Log.d(TAG, "Track anadido a playlist de YouTube: $localPlaylistId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error anadiendo track a playlist de YouTube: ${e.message}", e)
            false
        }
    }

    suspend fun removeTrackFromYouTubePlaylist(
        localPlaylistId: String,
        remoteTrackId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = trackDao.getTracksByPlaylistSync(localPlaylistId)
            val track = existing.find { it.remoteTrackId == remoteTrackId }
                ?: return@withContext false

            trackDao.deleteTrackById(track.id)

            val remaining = trackDao.getTracksByPlaylistSync(localPlaylistId)
            remaining.sortedBy { it.position }.forEachIndexed { index, t ->
                trackDao.updateTrack(t.copy(position = index))
            }

            val playlist = playlistDao.getPlaylistById(localPlaylistId)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(trackCount = remaining.size))
            }

            Log.d(TAG, "Track eliminado de playlist de YouTube: $localPlaylistId ($remoteTrackId)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando track de playlist de YouTube: ${e.message}", e)
            false
        }
    }
}
