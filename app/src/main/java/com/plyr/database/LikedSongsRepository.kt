package com.plyr.database

import android.util.Log
import androidx.room.withTransaction
import com.plyr.utils.ImportManifest
import com.plyr.utils.LikedSongsMerge
import com.plyr.utils.TombstoneConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Operaciones sobre la lista especial `liked_songs`: crearla, consultar si una
 * pista está marcada, alternarla y fusionarla al importar.
 */
class LikedSongsRepository internal constructor(private val store: PlaylistStore) {

    private val playlistDao = store.playlistDao
    private val trackDao = store.trackDao
    private val database = store.database
    private val appContext = store.appContext

    private val likedSongsId get() = PlaylistLocalRepository.LIKED_SONGS_ID

    suspend fun ensureLikedSongsPlaylist() = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(likedSongsId)
        if (existing == null) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    remoteId = likedSongsId,
                    name = "liked",
                    description = null,
                    trackCount = 0,
                    imageUrl = null
                )
            )
            Log.d(TAG, "Liked songs playlist created")
            store.markDirty()
        }
    }

    suspend fun isTrackLiked(youtubeVideoId: String): Boolean = withContext(Dispatchers.IO) {
        val tracks = trackDao.getTracksByPlaylistSync(likedSongsId)
        tracks.any { it.youtubeVideoId == youtubeVideoId }
    }

    suspend fun isTrackLikedByKey(
        name: String,
        artists: String,
        remoteTrackId: String,
        youtubeVideoId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val tracks = trackDao.getTracksByPlaylistSync(likedSongsId)
        PlaylistLocalRepository.likedTrackOf(tracks, name, artists, remoteTrackId, youtubeVideoId) != null
    }

    suspend fun toggleLikeTrack(
        youtubeVideoId: String,
        name: String,
        artists: String,
        remoteTrackId: String
    ): Boolean = database.withTransaction {
        val tracks = trackDao.getTracksByPlaylistSync(likedSongsId)
        val existing = PlaylistLocalRepository.likedTrackOf(tracks, name, artists, remoteTrackId, youtubeVideoId)

        if (existing != null) {
            trackDao.deleteTrackById(existing.id)
            // El borrado tiene que viajar: `liked_songs` se fusiona en cada
            // sincronización y, sin esta marca, la pista que el usuario acaba de
            // quitar volvía en la siguiente (B51).
            TombstoneConfig.addRemovedLikedTrackKey(
                appContext,
                ImportManifest.likedTrackKey(existing),
            )
            val remaining = trackDao.getTracksByPlaylistSync(likedSongsId)
            remaining.sortedBy { it.position }.forEachIndexed { index, t ->
                trackDao.updateTrack(t.copy(position = index))
            }
            val playlist = playlistDao.getPlaylistById(likedSongsId)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(trackCount = remaining.size))
            }
            Log.d(TAG, "Track removed from liked: $name")
            store.markDirty()
            false
        } else {
            // Marcar de nuevo es decir "vuelve a estar en favoritos", así que el
            // tomb anterior se levanta: si se quedara, la fusión la saltaría.
            TombstoneConfig.removeRemovedLikedTrackKey(
                appContext,
                ImportManifest.likedTrackKey(youtubeVideoId, name, artists),
            )
            val nextPosition = if (tracks.isNotEmpty()) tracks.maxOf { it.position } + 1 else 0
            val newTrack = TrackEntity(
                id = "${likedSongsId}_${remoteTrackId}_$nextPosition",
                playlistId = likedSongsId,
                remoteTrackId = remoteTrackId,
                name = name,
                artists = artists,
                youtubeVideoId = youtubeVideoId,
                audioUrl = null,
                position = nextPosition
            )
            trackDao.insertTrack(newTrack)
            val playlist = playlistDao.getPlaylistById(likedSongsId)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(trackCount = tracks.size + 1))
            }
            Log.d(TAG, "Track added to liked: $name")
            store.markDirty()
            true
        }
    }

    /**
     * Fusiona [tracks] en `liked_songs` sin duplicar: se descartan las que ya
     * están, comparando por `youtubeVideoId` (el mismo criterio que
     * [toggleLikeTrack]) y, si no lo tienen, por nombre y artistas.
     *
     * Se usa al importar: los favoritos que el usuario tenga ahora siempre ganan
     * sobre los del archivo. Devuelve cuántas pistas se añadieron.
     *
     * [removedKeys] son los favoritos que el usuario quitó (B51): también ganan
     * sobre los del archivo. Sin esto, `liked_songs` —que nunca se sobrescribe—
     * reinsertaba en cada sincronización lo que el usuario acababa de borrar, y
     * como la fusión marca el estado como sucio, el resurreto se consolidaba
     * además en la copia de la carpeta.
     */
    suspend fun mergeLikedSongsTracks(
        tracks: List<TrackEntity>,
        removedKeys: Set<String> = emptySet(),
    ): Int = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext 0

        val existing = trackDao.getTracksByPlaylistSync(likedSongsId)
        // La política vive en LikedSongsMerge (puro y testeado); aquí solo se
        // calculan las claves y se recuperan las pistas elegidas.
        val fresh = LikedSongsMerge.selectFreshIndexes(
            existingKeys = existing.map { ImportManifest.likedTrackKey(it) },
            incomingKeys = tracks.map { ImportManifest.likedTrackKey(it) },
            removedKeys = removedKeys,
        ).map { tracks[it] }
        if (fresh.isEmpty()) {
            Log.d(TAG, "Importación de favoritos: todo ya estaba en liked_songs")
            return@withContext 0
        }

        var nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val newTracks = fresh.map { track ->
            val position = nextPosition++
            track.copy(
                id = "${likedSongsId}_${track.remoteTrackId}_$position",
                playlistId = likedSongsId,
                position = position
            )
        }
        trackDao.insertTracks(newTracks)

        val playlist = playlistDao.getPlaylistById(likedSongsId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(trackCount = existing.size + newTracks.size))
        }
        Log.d(TAG, "Importación de favoritos: ${newTracks.size} añadidas de ${fresh.size}")
        store.markDirty()
        newTracks.size
    }

    private companion object {
        const val TAG = "LikedSongsRepo"
    }
}
