package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SearchHistoryEntity - Entidad para el historial de búsquedas
 *
 * Guarda las búsquedas realizadas por el usuario con información sobre
 * el motor de búsqueda utilizado (YouTube o Spotify)
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Texto de la búsqueda
     */
    val query: String,

    /**
     * Motor de búsqueda utilizado: "youtube" o "spotify"
     */
    val searchEngine: String,

    /**
     * Timestamp de cuando se realizó la búsqueda
     */
    val timestamp: Long = System.currentTimeMillis()
)

