package com.plyr.desktop.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Una búsqueda del historial (equivalente a `SearchHistoryEntity` de Room). */
data class SearchHistoryEntry(
    val id: Long,
    val query: String,
    val searchEngine: String,
    val timestamp: Long,
)

/**
 * Historial de búsquedas del escritorio (SQLDelight), con la misma política que
 * `SearchViewModel` en Android: se guardan como mucho las 50 más recientes y
 * repetir una búsqueda no deja duplicados.
 */
class SearchHistoryRepository(private val database: DesktopDatabase) {

    private val queries get() = database.db.searchHistoryQueries

    /**
     * Registra [query] para [searchEngine], quitando antes las repeticiones con el
     * mismo texto y motor para que no queden duplicados.
     */
    fun addSearch(
        query: String,
        searchEngine: String,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        database.db.transaction {
            queries.deleteByQuery(query, searchEngine)
            queries.insert(query = query, searchEngine = searchEngine, timestamp = timestamp)
        }
    }

    /** Las últimas 50 búsquedas, de más reciente a más antigua. */
    fun recentSearches(): List<SearchHistoryEntry> =
        queries.selectRecent().executeAsList().map { it.toEntry() }

    fun observeRecentSearches(): Flow<List<SearchHistoryEntry>> =
        queries.selectRecent().asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toEntry() } }

    fun clear() {
        queries.clear()
    }

    fun deleteById(id: Long) {
        queries.deleteById(id)
    }

    fun deleteByQuery(query: String, searchEngine: String) {
        queries.deleteByQuery(query, searchEngine)
    }

    private fun Search_history.toEntry() = SearchHistoryEntry(
        id = id,
        query = query,
        searchEngine = searchEngine,
        timestamp = timestamp,
    )
}
