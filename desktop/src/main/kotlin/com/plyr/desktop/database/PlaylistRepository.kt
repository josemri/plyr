package com.plyr.desktop.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistFilters
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.utils.LikedSongsMerge
import com.plyr.utils.TrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repositorio de listas y pistas del escritorio (SQLDelight).
 *
 * Reutiliza la lógica portable de `:core` —[PlaylistFilters], [TrackIdentity],
 * [LikedSongsMerge]— que antes vivía dispersa en `PlaylistLocalRepository` y sus
 * repositorios especializados de Android (`LikedSongsRepository`,
 * `PlaylistRepository`, `PlaylistTracksRepository`). El filtro de listas visibles
 * y la identidad de las pistas son, literalmente, el mismo código que en Android.
 *
 * Las escrituras avisan por [onChanged] (la copia de seguridad de M10 se engancha
 * ahí; en M6 es un no-op).
 */
class PlaylistRepository(
    private val database: DesktopDatabase,
    private val tombstones: TombstoneStore = InMemoryTombstoneStore(),
) {

    private val playlistQueries get() = database.db.playlistQueries
    private val trackQueries get() = database.db.trackQueries
    private val db get() = database.db

    /** Aviso de que los datos cambiaron (copia de seguridad, M10). */
    var onChanged: (() -> Unit)? = null

    // === LECTURAS ===

    /** Todas las listas, ordenadas por nombre. */
    fun allPlaylists(): List<PlaylistRecord> =
        playlistQueries.selectAll().executeAsList().map { it.toRecord() }

    /** Listas que se muestran (filtro/orden compartidos con Android). */
    fun visiblePlaylists(): List<PlaylistRecord> =
        PlaylistFilters.visiblePlaylists(allPlaylists(), LikedSongs.ID)

    fun playlistById(remoteId: String): PlaylistRecord? =
        playlistQueries.selectById(remoteId).executeAsOneOrNull()?.toRecord()

    fun tracksByPlaylist(playlistId: String): List<TrackRecord> =
        trackQueries.selectByPlaylist(playlistId).executeAsList().map { it.toRecord() }

    fun trackById(trackId: String): TrackRecord? =
        trackQueries.selectById(trackId).executeAsOneOrNull()?.toRecord()

    fun observePlaylists(): Flow<List<PlaylistRecord>> =
        playlistQueries.selectAll().asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toRecord() } }

    fun observeTracks(playlistId: String): Flow<List<TrackRecord>> =
        trackQueries.selectByPlaylist(playlistId).asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toRecord() } }

    // === LISTAS ===

    /**
     * Alta/edición de una lista con sus pistas: reemplaza las pistas anteriores.
     * Guardar es la forma de re-añadir una lista, así que se levanta su tomb.
     */
    fun savePlaylist(playlist: PlaylistRecord, tracks: List<TrackRecord>): Boolean = try {
        db.transaction {
            playlistQueries.insert(
                remoteId = playlist.remoteId,
                name = playlist.name,
                description = playlist.description,
                trackCount = tracks.size.toLong(),
                imageUrl = playlist.imageUrl,
                lastSyncTime = playlist.lastSyncTime,
                source = playlist.source.name,
                sourceId = playlist.sourceId,
            )
            trackQueries.deleteByPlaylist(playlist.remoteId)
            tracks.forEach { insertTrack(it.copy(playlistId = playlist.remoteId)) }
        }
        tombstones.removeDeletedPlaylistId(playlist.remoteId)
        changed()
        true
    } catch (_: Exception) {
        false
    }

    /** Borra la lista y sus pistas, y recuerda el borrado (`liked_songs` nunca se tomba). */
    fun deletePlaylist(remoteId: String) {
        db.transaction {
            trackQueries.deleteByPlaylist(remoteId)
            playlistQueries.deleteById(remoteId)
        }
        if (remoteId != LikedSongs.ID) {
            tombstones.addDeletedPlaylistId(remoteId)
        }
        changed()
    }

    fun updatePlaylistDetails(remoteId: String, newTitle: String?, newDesc: String?): Boolean {
        val current = playlistById(remoteId) ?: return false
        writePlaylist(
            current.copy(
                name = newTitle ?: current.name,
                description = newDesc ?: current.description,
                lastSyncTime = System.currentTimeMillis(),
            )
        )
        changed()
        return true
    }

    fun updatePlaylistImage(remoteId: String, imageUrl: String): Boolean {
        val current = playlistById(remoteId) ?: return false
        writePlaylist(current.copy(imageUrl = imageUrl, lastSyncTime = System.currentTimeMillis()))
        changed()
        return true
    }

    // === PISTAS ===

    fun updateTrackYoutubeId(trackId: String, youtubeVideoId: String) {
        trackQueries.updateYoutubeVideoId(youtubeVideoId, trackId)
        changed()
    }

    /**
     * Añade [track] al final de [playlistId]. No duplica la misma canción (mismo
     * criterio de identidad que la fusión de favoritos, B13): si ya está, es un
     * éxito silencioso.
     */
    fun addTrack(playlistId: String, track: TrackRecord): Boolean {
        val existing = tracksByPlaylist(playlistId)
        val incomingKey = track.youtubeVideoId ?: track.remoteTrackId
        if (incomingKey.isNotBlank() &&
            existing.any { (it.youtubeVideoId ?: it.remoteTrackId) == incomingKey }
        ) {
            return true
        }

        val nextPosition = if (existing.isNotEmpty()) existing.maxOf { it.position } + 1 else 0
        val newTrack = track.copy(
            id = "${playlistId}_${track.remoteTrackId}_$nextPosition",
            playlistId = playlistId,
            position = nextPosition,
        )
        db.transaction {
            insertTrack(newTrack)
            setTrackCount(playlistId, existing.size + 1)
        }
        changed()
        return true
    }

    /** Quita la pista con ese `remoteTrackId` y renumera el resto. */
    fun removeTrack(playlistId: String, remoteTrackId: String): Boolean {
        val existing = tracksByPlaylist(playlistId)
        val track = existing.find { it.remoteTrackId == remoteTrackId } ?: return false
        val remaining = existing.filter { it.id != track.id }.sortedBy { it.position }

        db.transaction {
            trackQueries.deleteById(track.id)
            remaining.forEachIndexed { index, record ->
                if (record.position != index) writeTrack(record.copy(position = index))
            }
            setTrackCount(playlistId, remaining.size)
        }
        changed()
        return true
    }

    /**
     * Persiste el nuevo orden de una lista. [orderedRemoteTrackIds] son los
     * `remoteTrackId` en el orden deseado; las pistas que no aparezcan conservan
     * su posición relativa (no se tocan).
     */
    fun reorderTracks(playlistId: String, orderedRemoteTrackIds: List<String>): Boolean = try {
        val byRemoteId = tracksByPlaylist(playlistId).associateBy { it.remoteTrackId }
        db.transaction {
            orderedRemoteTrackIds.forEachIndexed { index, remoteId ->
                val track = byRemoteId[remoteId] ?: return@forEachIndexed
                if (track.position != index) writeTrack(track.copy(position = index))
            }
        }
        changed()
        true
    } catch (_: Exception) {
        false
    }

    // === FAVORITOS (`liked_songs`) ===

    /** Crea la fila especial `liked_songs` si aún no existe (se hace al arrancar). */
    fun ensureLikedSongsPlaylist() {
        if (playlistById(LikedSongs.ID) != null) return
        playlistQueries.insert(
            remoteId = LikedSongs.ID,
            name = "liked",
            description = null,
            trackCount = 0,
            imageUrl = null,
            lastSyncTime = System.currentTimeMillis(),
            source = PlaylistSource.UNKNOWN.name,
            sourceId = null,
        )
        changed()
    }

    fun isTrackLiked(youtubeVideoId: String): Boolean =
        tracksByPlaylist(LikedSongs.ID).any { it.youtubeVideoId == youtubeVideoId }

    fun isTrackLikedByKey(
        name: String,
        artists: String,
        remoteTrackId: String,
        youtubeVideoId: String?,
    ): Boolean =
        TrackIdentity.likedTrackOf(
            tracksByPlaylist(LikedSongs.ID), name, artists, remoteTrackId, youtubeVideoId
        ) != null

    /**
     * Alterna el favorito de una canción. Quitar deja un tomb (B51) para que la
     * siguiente fusión no la resucite; volver a marcarla lo levanta.
     */
    fun toggleLikeTrack(
        youtubeVideoId: String,
        name: String,
        artists: String,
        remoteTrackId: String,
    ): Boolean {
        val liked = tracksByPlaylist(LikedSongs.ID)
        val existing = TrackIdentity.likedTrackOf(liked, name, artists, remoteTrackId, youtubeVideoId)

        if (existing != null) {
            val remaining = liked.filter { it.id != existing.id }.sortedBy { it.position }
            db.transaction {
                trackQueries.deleteById(existing.id)
                remaining.forEachIndexed { index, record ->
                    if (record.position != index) writeTrack(record.copy(position = index))
                }
                setTrackCount(LikedSongs.ID, remaining.size)
            }
            tombstones.addRemovedLikedTrackKey(TrackIdentity.likedTrackKey(existing))
            changed()
            return false
        }

        tombstones.removeRemovedLikedTrackKey(TrackIdentity.likedTrackKey(youtubeVideoId, name, artists))
        val nextPosition = if (liked.isNotEmpty()) liked.maxOf { it.position } + 1 else 0
        val newTrack = TrackRecord(
            id = "${LikedSongs.ID}_${remoteTrackId}_$nextPosition",
            playlistId = LikedSongs.ID,
            remoteTrackId = remoteTrackId,
            name = name,
            artists = artists,
            youtubeVideoId = youtubeVideoId,
            audioUrl = null,
            position = nextPosition,
            lastSyncTime = System.currentTimeMillis(),
        )
        db.transaction {
            insertTrack(newTrack)
            setTrackCount(LikedSongs.ID, liked.size + 1)
        }
        changed()
        return true
    }

    /**
     * Fusiona [tracks] en `liked_songs` sin duplicar. La política (tomb > duplicada
     * > se añade) vive en [LikedSongsMerge]; aquí solo se calculan las claves y se
     * escriben las pistas elegidas. Devuelve cuántas se añadieron.
     */
    fun mergeLikedSongsTracks(
        tracks: List<TrackRecord>,
        removedKeys: Set<String> = emptySet(),
    ): Int {
        if (tracks.isEmpty()) return 0
        ensureLikedSongsPlaylist()

        val existing = tracksByPlaylist(LikedSongs.ID)
        val fresh = LikedSongsMerge.selectFreshIndexes(
            existingKeys = existing.map { TrackIdentity.likedTrackKey(it) },
            incomingKeys = tracks.map { TrackIdentity.likedTrackKey(it) },
            removedKeys = removedKeys,
        ).map { tracks[it] }
        if (fresh.isEmpty()) return 0

        var nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val newTracks = fresh.map { track ->
            val position = nextPosition++
            track.copy(
                id = "${LikedSongs.ID}_${track.remoteTrackId}_$position",
                playlistId = LikedSongs.ID,
                position = position,
            )
        }
        db.transaction {
            newTracks.forEach { insertTrack(it) }
            setTrackCount(LikedSongs.ID, existing.size + newTracks.size)
        }
        changed()
        return newTracks.size
    }

    // === HELPERS ===

    private fun insertTrack(track: TrackRecord) {
        trackQueries.insert(
            id = track.id,
            playlistId = track.playlistId,
            remoteTrackId = track.remoteTrackId,
            name = track.name,
            artists = track.artists,
            youtubeVideoId = track.youtubeVideoId,
            audioUrl = track.audioUrl,
            position = track.position.toLong(),
            lastSyncTime = track.lastSyncTime,
        )
    }

    private fun writeTrack(track: TrackRecord) {
        trackQueries.update(
            playlistId = track.playlistId,
            remoteTrackId = track.remoteTrackId,
            name = track.name,
            artists = track.artists,
            youtubeVideoId = track.youtubeVideoId,
            audioUrl = track.audioUrl,
            position = track.position.toLong(),
            lastSyncTime = track.lastSyncTime,
            id = track.id,
        )
    }

    private fun writePlaylist(playlist: PlaylistRecord) {
        playlistQueries.update(
            name = playlist.name,
            description = playlist.description,
            trackCount = playlist.trackCount.toLong(),
            imageUrl = playlist.imageUrl,
            lastSyncTime = playlist.lastSyncTime,
            source = playlist.source.name,
            sourceId = playlist.sourceId,
            remoteId = playlist.remoteId,
        )
    }

    /** Reajusta el `trackCount` de la lista tras añadir/quitar pistas. */
    private fun setTrackCount(playlistId: String, count: Int) {
        val playlist = playlistById(playlistId) ?: return
        writePlaylist(playlist.copy(trackCount = count))
    }

    private fun changed() {
        onChanged?.invoke()
    }

    private fun Playlists.toRecord() = PlaylistRecord(
        remoteId = remoteId,
        name = name,
        description = description,
        trackCount = trackCount.toInt(),
        imageUrl = imageUrl,
        lastSyncTime = lastSyncTime,
        source = PlaylistSource.fromStorage(source) ?: PlaylistSource.UNKNOWN,
        sourceId = sourceId,
    )

    private fun Tracks.toRecord() = TrackRecord(
        id = id,
        playlistId = playlistId,
        remoteTrackId = remoteTrackId,
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = audioUrl,
        position = position.toInt(),
        lastSyncTime = lastSyncTime,
    )
}
