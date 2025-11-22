package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * LocalPlaylistEntity - Entidad para representar playlists locales creadas por el usuario
 *
 * Estas playlists son independientes de Spotify y contienen canciones importadas localmente.
 */
@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    /** ID único de la playlist local */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Nombre de la playlist */
    val name: String,

    /** Descripción opcional de la playlist */
    val description: String?,

    /** URL de la imagen de portada (puede ser null) */
    val imageUrl: String?,

    /** Timestamp de creación */
    val createdTime: Long = System.currentTimeMillis(),

    /** Timestamp de última modificación */
    val lastModifiedTime: Long = System.currentTimeMillis()
)

