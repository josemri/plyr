package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * TrackEntity - Entidad para representar tracks de playlists en la base de datos local
 * 
 * Establece una relación con PlaylistEntity mediante foreign key y mantiene
 * sincronización entre Spotify y YouTube para reproducción.
 */
@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["spotifyId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class TrackEntity(
    /** ID único del track (combinación de playlistId + posición o track ID) */
    @PrimaryKey
    val id: String,
    
    /** ID de la playlist a la que pertenece este track */
    val playlistId: String,
    
    /** ID único del track en Spotify */
    val spotifyTrackId: String,
    
    /** Nombre/título del track */
    val name: String,
    
    /** Lista de artistas separados por coma */
    val artists: String,
    
    /** ID del video de YouTube correspondiente (para reproducción) */
    val youtubeVideoId: String? = null,
    
    /** Posición del track dentro de la playlist */
    val position: Int,
    
    /** Timestamp de la última sincronización con Spotify */
    val lastSyncTime: Long = System.currentTimeMillis()
)
