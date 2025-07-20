package com.plyr.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * TrackDao - Data Access Object para operaciones sobre tracks
 * 
 * Define todas las operaciones de base de datos para tracks de playlists,
 * incluyendo consultas, actualización de YouTube IDs y estadísticas.
 */
@Dao
interface TrackDao {
    
    // === CONSULTAS DE OBSERVACIÓN (FLOW/LIVEDATA) ===
    
    /**
     * Obtiene todos los tracks de una playlist como Flow observable.
     * Los cambios se notifican automáticamente y están ordenados por posición.
     */
    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getTracksByPlaylist(playlistId: String): Flow<List<TrackEntity>>
    
    // === CONSULTAS SÍNCRONAS ===
    
    /**
     * Obtiene todos los tracks de una playlist de forma síncrona.
     */
    @Query("SELECT * FROM tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getTracksByPlaylistSync(playlistId: String): List<TrackEntity>
    
    /**
     * Busca un track específico por su ID único.
     */
    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?
    
    // === CONSULTAS DE ESTADÍSTICAS ===
    
    /**
     * Cuenta cuántos tracks de una playlist tienen YouTube ID asignado.
     */
    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId AND youtubeVideoId IS NOT NULL")
    suspend fun getTracksWithYoutubeIdCount(playlistId: String): Int
    
    /**
     * Cuenta el total de tracks en una playlist.
     */
    @Query("SELECT COUNT(*) FROM tracks WHERE playlistId = :playlistId")
    suspend fun getTotalTracksCount(playlistId: String): Int
    
    // === OPERACIONES DE ESCRITURA ===
    
    /**
     * Inserta o actualiza un track individual.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)
    
    /**
     * Inserta o actualiza múltiples tracks de forma eficiente.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)
    
    /**
     * Actualiza un track existente.
     */
    @Update
    suspend fun updateTrack(track: TrackEntity)
    
    /**
     * Actualiza solo el YouTube ID de un track específico.
     * Útil para asignar videos de YouTube a tracks de Spotify.
     */
    @Query("UPDATE tracks SET youtubeVideoId = :youtubeVideoId WHERE id = :trackId")
    suspend fun updateYoutubeVideoId(trackId: String, youtubeVideoId: String)
    
    // === OPERACIONES DE ELIMINACIÓN ===
    
    /**
     * Elimina un track específico.
     */
    @Delete
    suspend fun deleteTrack(track: TrackEntity)
    
    /**
     * Elimina todos los tracks de una playlist específica.
     */
    @Query("DELETE FROM tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracksByPlaylist(playlistId: String)
    
    /**
     * Elimina un track por su ID único.
     */
    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)
    
    /**
     * Elimina todos los tracks (limpieza completa).
     */
    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}
