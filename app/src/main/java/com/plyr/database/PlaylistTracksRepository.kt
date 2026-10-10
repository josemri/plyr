package com.plyr.database

import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Operaciones sobre las pistas de una lista: actualizar su id de YouTube,
 * añadirlas, quitarlas y reordenarlas.
 */
class PlaylistTracksRepository internal constructor(private val store: PlaylistStore) {

    private val playlistDao = store.playlistDao
    private val trackDao = store.trackDao
    private val database = store.database

    suspend fun updateTrackYoutubeId(trackId: String, youtubeVideoId: String) {
        trackDao.updateYoutubeVideoId(trackId, youtubeVideoId)
        store.markDirty()
    }

    suspend fun addTrackToYouTubePlaylist(
        localPlaylistId: String,
        track: TrackEntity
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = trackDao.getTracksByPlaylistSync(localPlaylistId)

            // B13: no duplicar la misma canción (mismo criterio de identidad que
            // mergeLikedSongsTracks): si ya está, es un éxito silencioso.
            val incomingKey = track.youtubeVideoId ?: track.remoteTrackId
            if (incomingKey.isNotBlank() &&
                existing.any { (it.youtubeVideoId ?: it.remoteTrackId) == incomingKey }
            ) {
                return@withContext true
            }

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
            store.markDirty()
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
            store.markDirty()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando track de playlist de YouTube: ${e.message}", e)
            false
        }
    }

    /**
     * Persiste el nuevo orden de una lista local. [orderedTrackIds] son los
     * `remoteTrackId` en el orden deseado; las pistas que no aparezcan conservan
     * su posición relativa (no se tocan). Se reescriben las `position` de 0 a n-1
     * en una transacción, de modo que un fallo a mitad no deja índices repetidos.
     */
    suspend fun reorderTracks(
        localPlaylistId: String,
        orderedTrackIds: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val byRemoteId = trackDao.getTracksByPlaylistSync(localPlaylistId).associateBy { it.remoteTrackId }
            database.withTransaction {
                orderedTrackIds.forEachIndexed { index, remoteId ->
                    val track = byRemoteId[remoteId] ?: return@forEachIndexed
                    if (track.position != index) {
                        trackDao.updateTrack(track.copy(position = index))
                    }
                }
            }
            Log.d(TAG, "Orden de lista actualizado: $localPlaylistId")
            store.markDirty()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error reordenando lista: ${e.message}", e)
            false
        }
    }

    private companion object {
        const val TAG = "PlaylistTracksRepo"
    }
}
