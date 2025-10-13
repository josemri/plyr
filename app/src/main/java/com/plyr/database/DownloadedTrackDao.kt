package com.plyr.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DownloadedTrackDao - Data Access Object para operaciones sobre tracks descargados
 */
@Dao
interface DownloadedTrackDao {

    /**
     * Obtiene todos los tracks descargados como Flow observable.
     */
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadTime DESC")
    fun getAllDownloadedTracks(): Flow<List<DownloadedTrackEntity>>

    /**
     * Obtiene todos los tracks descargados de forma síncrona.
     */
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadTime DESC")
    suspend fun getAllDownloadedTracksSync(): List<DownloadedTrackEntity>

    /**
     * Busca un track descargado por su ID de Spotify.
     */
    @Query("SELECT * FROM downloaded_tracks WHERE spotifyTrackId = :spotifyTrackId")
    suspend fun getDownloadedTrackBySpotifyId(spotifyTrackId: String): DownloadedTrackEntity?

    /**
     * Verifica si un track ya está descargado.
     */
    @Query("SELECT COUNT(*) FROM downloaded_tracks WHERE spotifyTrackId = :spotifyTrackId")
    suspend fun isTrackDownloaded(spotifyTrackId: String): Int

    /**
     * Inserta o actualiza un track descargado.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedTrack(track: DownloadedTrackEntity)

    /**
     * Elimina un track descargado.
     */
    @Delete
    suspend fun deleteDownloadedTrack(track: DownloadedTrackEntity)

    /**
     * Elimina un track descargado por su ID.
     */
    @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
    suspend fun deleteDownloadedTrackById(trackId: String)

    /**
     * Elimina todos los tracks descargados.
     */
    @Query("DELETE FROM downloaded_tracks")
    suspend fun deleteAllDownloadedTracks()
}

