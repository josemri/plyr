package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    override val remoteId: String,
    override val name: String,
    val description: String?,
    override val trackCount: Int,
    val imageUrl: String?,
    val lastSyncTime: Long = System.currentTimeMillis(),
    val source: PlaylistSource = PlaylistSource.UNKNOWN,
    val sourceId: String? = null
) : PlaylistRow
