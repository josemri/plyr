package com.plyr.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * PlaylistDao - Data Access Object para operaciones sobre playlists
 * 
 * Define todas las operaciones de base de datos para playlists,
 * incluyendo consultas síncronas, asíncronas y operaciones CRUD.
 */
@Dao
interface PlaylistDao {
    
    // === CONSULTAS DE OBSERVACIÓN (FLOW/LIVEDATA) ===
    
    /**
     * Obtiene todas las playlists ordenadas por nombre como Flow observable.
     * Los cambios en la base de datos se notifican automáticamente.
     */
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    // === CONSULTAS SÍNCRONAS ===
    
    /**
     * Obtiene todas las playlists ordenadas por nombre de forma síncrona.
     * Útil para operaciones que no requieren observación.
     */
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>
    
    /**
     * Busca una playlist específica por su ID de Spotify.
     */
    @Query("SELECT * FROM playlists WHERE spotifyId = :spotifyId")
    suspend fun getPlaylistById(spotifyId: String): PlaylistEntity?
    
    /**
     * Obtiene el timestamp de la última sincronización de una playlist.
     */
    @Query("SELECT lastSyncTime FROM playlists WHERE spotifyId = :spotifyId")
    suspend fun getLastSyncTime(spotifyId: String): Long?
    
    // === OPERACIONES DE ESCRITURA ===
    
    /**
     * Inserta o actualiza una playlist individual.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    
    /**
     * Inserta o actualiza múltiples playlists de forma eficiente.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)
    
    /**
     * Actualiza una playlist existente.
     */
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    
    // === OPERACIONES DE ELIMINACIÓN ===
    
    /**
     * Elimina una playlist específica.
     */
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    /**
     * Elimina una playlist por su ID de Spotify.
     */
    @Query("DELETE FROM playlists WHERE spotifyId = :spotifyId")
    suspend fun deletePlaylistById(spotifyId: String)
    
    /**
     * Elimina todas las playlists (limpieza completa).
     */
    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()
}
