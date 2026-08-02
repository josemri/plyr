package com.plyr.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * SearchHistoryDao - DAO para acceder al historial de búsquedas
 */
@Dao
interface SearchHistoryDao {

    /**
     * Inserta una nueva búsqueda en el historial
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    /**
     * Obtiene todas las búsquedas ordenadas por timestamp descendente (más recientes primero)
     * Limita a las últimas 50 búsquedas
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllSearches(): Flow<List<SearchHistoryEntity>>

    /**
     * Elimina todas las búsquedas del historial
     */
    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    /**
     * Elimina una búsqueda específica por ID
     */
    @Query("DELETE FROM search_history WHERE id = :searchId")
    suspend fun deleteSearch(searchId: Long)

    /**
     * Elimina todas las instancias de una búsqueda con el mismo texto y motor
     * (para que al repetir una búsqueda no queden duplicados en el historial)
     */
    @Query("DELETE FROM search_history WHERE query = :query AND searchEngine = :engine")
    suspend fun deleteSearchByQuery(query: String, engine: String)
}
