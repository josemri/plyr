package com.plyr.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    
    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getTracksByPlaylist(playlistId: String): Flow<List<TrackEntity>>
    
    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracksByPlaylistSync(playlistId: String): List<TrackEntity>
    
    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)
    
    @Update
    suspend fun updateTrack(track: TrackEntity)
    
    @Query("UPDATE tracks SET youtubeVideoId = :youtubeVideoId WHERE id = :trackId")
    suspend fun updateYoutubeVideoId(trackId: String, youtubeVideoId: String)
    
    @Delete
    suspend fun deleteTrack(track: TrackEntity)
    
    @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksByPlaylist(playlistId: String)
    
    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)
    
    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
    
    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId AND youtubeVideoId IS NOT NULL")
    suspend fun getTracksWithYoutubeIdCount(playlistId: String): Int
    
    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId")
    suspend fun getTotalTracksCount(playlistId: String): Int
}
