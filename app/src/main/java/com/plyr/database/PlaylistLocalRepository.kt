package com.plyr.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.plyr.utils.Config
import com.plyr.utils.DataSync
import com.plyr.utils.ImportManifest
import com.plyr.utils.LikedSongsMerge

class PlaylistLocalRepository(context: Context) {

    private val database = PlaylistDatabase.getDatabase(context)
    private val playlistDao = database.playlistDao()
    private val trackDao = database.trackDao()
    private val appContext = context.applicationContext

    companion object {
        private const val TAG = "PlaylistLocalRepo"
        const val LIKED_SONGS_ID = "liked_songs"

        /**
         * Fila de *liked* que corresponde a una canción, o `null` si no está.
         *
         * La identidad es el `youtubeVideoId`; si la canción llega sin id, se
         * recurre a nombre+artista con [ImportManifest.fallbackDedupeKey], que
         * es la única clave disponible y la misma que usa `mergeLikedSongsTracks`.
         * Sin ese recurso, un `""` no encontraba nunca la fila guardada y cada
         * intento de quitar un favorito acababa **añadiendo** otra fila (B1).
         */
        fun likedTrackOf(
            tracks: List<TrackEntity>,
            name: String,
            artists: String,
            remoteTrackId: String,
            youtubeVideoId: String?
        ): TrackEntity? {
            if (!youtubeVideoId.isNullOrBlank()) {
                tracks.firstOrNull { it.youtubeVideoId == youtubeVideoId }?.let { return it }
            }
            val key = ImportManifest.fallbackDedupeKey(
                TrackEntity(
                    id = "",
                    playlistId = LIKED_SONGS_ID,
                    remoteTrackId = remoteTrackId,
                    name = name,
                    artists = artists,
                    youtubeVideoId = youtubeVideoId,
                    audioUrl = null,
                    position = 0
                )
            )
            return tracks.firstOrNull { ImportManifest.fallbackDedupeKey(it) == key }
        }

        /**
         * Listas que se muestran en el carrusel del Home y en la rejilla de
         * listas, ya filtradas y ordenadas.
         *
         * Dos exclusiones:
         * - `album_*`: los álbumes de Spotify no son listas del usuario.
         * - *liked* vacía: la fila se crea siempre al arrancar
         *   (`ensureLikedSongsPlaylist`), así que sin este filtro el primer
         *   elemento del Home era un corazón rojo sin nada detrás (F1).
         *
         * **Es un filtro, no un borrado**: `toggleLikeTrack`,
         * `mergeLikedSongsTracks` e `ImportManifest.plan` dan por hecho que la
         * fila de *liked* existe, y borrarla "si está vacía" rompería las tres.
         * `trackCount` lo mantienen las tres rutas que la modifican, así que
         * basta con mirarlo — y como `getAllPlaylists()` es un `Flow` sobre la
         * tabla, aparece y desaparece sola.
         */
        fun visiblePlaylists(playlists: List<PlaylistEntity>): List<PlaylistEntity> =
            playlists
                .filter { !it.remoteId.startsWith("album_") }
                .filter { it.remoteId != LIKED_SONGS_ID || it.trackCount > 0 }
                .sortedBy { if (it.remoteId == LIKED_SONGS_ID) "" else it.name }
    }

    /**
     * Avisa a la copia de seguridad de que los datos cambiaron.
     *
     * No hace nada por su cuenta: [DataSync] solo anota que hay cambios y
     * programa el volcado con retardo, así que es barato llamarlo desde
     * cualquier mutador. Si el usuario no ha configurado carpeta, se descarta
     * en la propia llamada.
     */
    private fun markDirty() = DataSync.markDirty(appContext)

    suspend fun ensureLikedSongsPlaylist() = withContext(Dispatchers.IO) {
        val existing = playlistDao.getPlaylistById(LIKED_SONGS_ID)
        if (existing == null) {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    remoteId = LIKED_SONGS_ID,
                    name = "liked",
                    description = null,
                    trackCount = 0,
                    imageUrl = null
                )
            )
            Log.d(TAG, "Liked songs playlist created")
            markDirty()
        }
    }

    suspend fun isTrackLiked(youtubeVideoId: String): Boolean = withContext(Dispatchers.IO) {
        val tracks = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
        tracks.any { it.youtubeVideoId == youtubeVideoId }
    }

    suspend fun isTrackLikedByKey(name: String, artists: String, remoteTrackId: String, youtubeVideoId: String?): Boolean = withContext(Dispatchers.IO) {
        val tracks = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
        likedTrackOf(tracks, name, artists, remoteTrackId, youtubeVideoId) != null
    }

    suspend fun toggleLikeTrack(
        youtubeVideoId: String,
        name: String,
        artists: String,
        remoteTrackId: String
    ): Boolean = database.withTransaction {
        val tracks = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
        val existing = likedTrackOf(tracks, name, artists, remoteTrackId, youtubeVideoId)

        if (existing != null) {
            trackDao.deleteTrackById(existing.id)
            // El borrado tiene que viajar: `liked_songs` se fusiona en cada
            // sincronización y, sin esta marca, la pista que el usuario acaba de
            // quitar volvía en la siguiente (B51).
            Config.addRemovedLikedTrackKey(
                appContext,
                ImportManifest.likedTrackKey(existing),
            )
            val remaining = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
            remaining.sortedBy { it.position }.forEachIndexed { index, t ->
                trackDao.updateTrack(t.copy(position = index))
            }
            val playlist = playlistDao.getPlaylistById(LIKED_SONGS_ID)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(trackCount = remaining.size))
            }
            Log.d(TAG, "Track removed from liked: $name")
            markDirty()
            false
        } else {
            // Marcar de nuevo es decir "vuelve a estar en favoritos", así que el
            // tomb anterior se levanta: si se quedara, la fusión la saltaría.
            Config.removeRemovedLikedTrackKey(
                appContext,
                ImportManifest.likedTrackKey(youtubeVideoId, name, artists),
            )
            val nextPosition = if (tracks.isNotEmpty()) tracks.maxOf { it.position } + 1 else 0
            val newTrack = TrackEntity(
                id = "${LIKED_SONGS_ID}_${remoteTrackId}_$nextPosition",
                playlistId = LIKED_SONGS_ID,
                remoteTrackId = remoteTrackId,
                name = name,
                artists = artists,
                youtubeVideoId = youtubeVideoId,
                audioUrl = null,
                position = nextPosition
            )
            trackDao.insertTrack(newTrack)
            val playlist = playlistDao.getPlaylistById(LIKED_SONGS_ID)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(trackCount = tracks.size + 1))
            }
            Log.d(TAG, "Track added to liked: $name")
            markDirty()
            true
        }
    }

    // === OBSERVATION ===

    fun getAllPlaylistsLiveData(): LiveData<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists().asLiveData()
    }

    fun getTracksByPlaylistLiveData(playlistId: String): LiveData<List<TrackEntity>> {
        return trackDao.getTracksByPlaylist(playlistId).asLiveData()
    }

    /** Lectura puntual de todas las listas (ordenadas por nombre), para la exportación. */
    suspend fun getAllPlaylists(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        playlistDao.getAllPlaylistsSync()
    }

    /** Lectura puntual de las pistas de una lista (ordenadas por posición), para la exportación. */
    suspend fun getTracksByPlaylistSync(playlistId: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        trackDao.getTracksByPlaylistSync(playlistId)
    }

    // === TRACK MANAGEMENT ===

    suspend fun updateTrackYoutubeId(trackId: String, youtubeVideoId: String) {
        trackDao.updateYoutubeVideoId(trackId, youtubeVideoId)
        markDirty()
    }

    // === YOUTUBE PLAYLISTS ===

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
            // Guardar es la forma de re-añadir una lista: se deshace el tomb
            // para que vuelva a poder sincronizarse.
            Config.removeDeletedPlaylistId(appContext, playlist.remoteId)
            markDirty()

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
        markDirty()
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
        markDirty()
        Log.d(TAG, "Playlist eliminada: $localPlaylistId")
    }

    /**
     * Registra el borrado de [localPlaylistId] para que la sincronización no
     * devuelva la lista: borrar es un cambio que también tiene que viajar.
     * `liked_songs` nunca se borra, así que tampoco puede tener tomb.
     */
    private fun rememberDeletion(localPlaylistId: String) {
        if (localPlaylistId != LIKED_SONGS_ID) {
            Config.addDeletedPlaylistId(appContext, localPlaylistId)
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

        val existing = trackDao.getTracksByPlaylistSync(LIKED_SONGS_ID)
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
                id = "${LIKED_SONGS_ID}_${track.remoteTrackId}_$position",
                playlistId = LIKED_SONGS_ID,
                position = position
            )
        }
        trackDao.insertTracks(newTracks)

        val playlist = playlistDao.getPlaylistById(LIKED_SONGS_ID)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(trackCount = existing.size + newTracks.size))
        }
        Log.d(TAG, "Importación de favoritos: ${newTracks.size} añadidas de ${fresh.size}")
        markDirty()
        newTracks.size
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
            markDirty()
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
            markDirty()
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
            markDirty()
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
            markDirty()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando track de playlist de YouTube: ${e.message}", e)
            false
        }
    }
}
