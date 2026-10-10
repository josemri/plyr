package com.plyr.database

import android.util.Log
import com.plyr.utils.TombstoneConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Alta, edición y borrado de listas (incluidas las de YouTube), y su restauración
 * desde una exportación.
 */
class PlaylistRepository internal constructor(private val store: PlaylistStore) {

    private val playlistDao = store.playlistDao
    private val trackDao = store.trackDao
    private val appContext = store.appContext

    private val likedSongsId get() = PlaylistLocalRepository.LIKED_SONGS_ID

    suspend fun saveYouTubePlaylist(
        playlistId: String,
        title: String,
        uploader: String,
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
                lastSyncTime = System.currentTimeMillis(),
                source = PlaylistSource.YOUTUBE,
                sourceId = playlistId
            ),
            tracks
        )
    }

    suspend fun saveCreatedYouTubePlaylist(
        created: CreatedPlaylist,
        tracks: List<TrackEntity>,
        source: PlaylistSource = PlaylistSource.LOCAL
    ): Boolean {
        val localPlaylistId = "youtube_${created.playlistId}"
        return saveYouTubePlaylistWithTracks(
            PlaylistEntity(
                remoteId = localPlaylistId,
                name = created.title,
                description = created.description,
                trackCount = tracks.size,
                imageUrl = created.imageUrl,
                lastSyncTime = System.currentTimeMillis(),
                source = source,
                sourceId = created.playlistId
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
            // Guardar es la forma de re-añadir una lista: se deshace el tomb
            // para que vuelva a poder sincronizarse.
            TombstoneConfig.removeDeletedPlaylistId(appContext, playlist.remoteId)
            store.markDirty()

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
        rememberDeletion(localPlaylistId)
        store.markDirty()
        Log.d(TAG, "YouTube playlist eliminada: $localPlaylistId")
    }

    /**
     * Borra la lista cuyo `remoteId` es exactamente [localPlaylistId].
     *
     * A diferencia de [deleteYouTubePlaylist], no antepone `youtube_`: sirve para
     * listas que no llevan ese prefijo, que antes no se podían borrar (la UI
     * hacía `removePrefix` y la función lo volvía a poner, un no-op silencioso).
     */
    suspend fun deletePlaylist(localPlaylistId: String) = withContext(Dispatchers.IO) {
        trackDao.deleteTracksByPlaylist(localPlaylistId)
        playlistDao.deletePlaylistById(localPlaylistId)
        rememberDeletion(localPlaylistId)
        store.markDirty()
        Log.d(TAG, "Playlist eliminada: $localPlaylistId")
    }

    /**
     * Registra el borrado de [localPlaylistId] para que la sincronización no
     * devuelva la lista: borrar es un cambio que también tiene que viajar.
     * `liked_songs` nunca se borra, así que tampoco puede tener tomb.
     */
    private fun rememberDeletion(localPlaylistId: String) {
        if (localPlaylistId != likedSongsId) {
            TombstoneConfig.addDeletedPlaylistId(appContext, localPlaylistId)
        }
    }

    /**
     * Restaura una lista tal cual venía de una exportación (ver `DataImporter`).
     * A diferencia de [saveYouTubePlaylist], no antepone ningún prefijo: el
     * `remoteId` viaja literal en el manifiesto. Si la lista ya existe, se
     * reemplaza junto con sus pistas.
     */
    suspend fun restorePlaylist(playlist: PlaylistEntity, tracks: List<TrackEntity>): Boolean =
        saveYouTubePlaylistWithTracks(playlist, tracks)

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
            store.markDirty()
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
            store.markDirty()
            Log.d(TAG, "Playlist local actualizada: $localPlaylistId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando playlist local: ${e.message}", e)
            false
        }
    }

    private companion object {
        const val TAG = "PlaylistRepo"
    }
}
