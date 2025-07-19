package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

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
    @PrimaryKey
    val id: String, // Combinación de playlistId + track position o spotify track id
    val playlistId: String,
    val spotifyTrackId: String,
    val name: String,
    val artists: String, // Lista de artistas separados por coma
    val youtubeVideoId: String? = null, // ID del video de YouTube
    val position: Int, // Posición en la playlist
    val lastSyncTime: Long = System.currentTimeMillis()
)
