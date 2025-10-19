package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PlaylistEntity - Entidad para representar playlists en la base de datos local
 * 
 * Contiene todos los datos necesarios para mostrar una playlist y gestionar
 * su sincronización con Spotify.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    /** ID único de la playlist en Spotify */
    @PrimaryKey
    val spotifyId: String,
    
    /** Nombre de la playlist */
    val name: String,
    
    /** Descripción opcional de la playlist */
    val description: String?,
    
    /** Número total de tracks en la playlist */
    val trackCount: Int,
    
    /** URL de la imagen de portada */
    val imageUrl: String?,
    
    /** Timestamp de la última sincronización con Spotify */
    val lastSyncTime: Long = System.currentTimeMillis()
)
