package com.plyr.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * LocalPlaylistDao - DAO para gestionar playlists locales
 */
@Dao
interface LocalPlaylistDao {

    @Query("SELECT * FROM local_playlists ORDER BY lastModifiedTime DESC")
    fun getAllLocalPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists WHERE id = :playlistId")
    suspend fun getLocalPlaylistById(playlistId: Long): LocalPlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalPlaylist(playlist: LocalPlaylistEntity): Long

    @Update
    suspend fun updateLocalPlaylist(playlist: LocalPlaylistEntity)

    @Delete
    suspend fun deleteLocalPlaylist(playlist: LocalPlaylistEntity)

    // Tracks de una playlist local
    @Query("""
        SELECT dt.* FROM downloaded_tracks dt
        INNER JOIN local_playlist_tracks lpt ON dt.id = lpt.trackId
        WHERE lpt.playlistId = :playlistId
        ORDER BY lpt.position ASC
    """)
    fun getTracksFromLocalPlaylist(playlistId: Long): Flow<List<DownloadedTrackEntity>>

    @Query("""
        SELECT COUNT(*) FROM local_playlist_tracks
        WHERE playlistId = :playlistId
    """)
    suspend fun getTrackCount(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: LocalPlaylistTrackEntity)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("DELETE FROM local_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun removeAllTracksFromPlaylist(playlistId: Long)

    @Transaction
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        val currentCount = getTrackCount(playlistId)
        insertPlaylistTrack(
            LocalPlaylistTrackEntity(
                playlistId = playlistId,
                trackId = trackId,
                position = currentCount
            )
        )
    }
}

