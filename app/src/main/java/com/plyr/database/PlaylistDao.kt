package com.plyr.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>
    
    @Query("SELECT * FROM playlists WHERE spotifyId = :spotifyId")
    suspend fun getPlaylistById(spotifyId: String): PlaylistEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)
    
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("DELETE FROM playlists WHERE spotifyId = :spotifyId")
    suspend fun deletePlaylistById(spotifyId: String)
    
    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
    
    @Query("SELECT lastSyncTime FROM playlists WHERE spotifyId = :spotifyId")
    suspend fun getLastSyncTime(spotifyId: String): Long?
}
