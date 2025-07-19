package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val spotifyId: String,
    val name: String,
    val description: String?,
    val trackCount: Int,
    val imageUrl: String?,
    val lastSyncTime: Long = System.currentTimeMillis()
)
