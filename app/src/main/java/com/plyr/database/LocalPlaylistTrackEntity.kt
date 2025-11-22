package com.plyr.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * LocalPlaylistTrackEntity - Relación entre playlists locales y tracks descargados
 *
 * Permite que una misma canción esté en múltiples playlists locales.
 */
@Entity(
    tableName = "local_playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DownloadedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class LocalPlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID de la playlist local */
    val playlistId: Long,

    /** ID del track descargado */
    val trackId: String,

    /** Posición del track en la playlist */
    val position: Int,

    /** Timestamp de cuando se añadió el track a la playlist */
    val addedTime: Long = System.currentTimeMillis()
)

