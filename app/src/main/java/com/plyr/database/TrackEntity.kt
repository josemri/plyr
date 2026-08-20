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
            parentColumns = ["remoteId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val playlistId: String,
    val remoteTrackId: String,
    val name: String,
    val artists: String,
    val youtubeVideoId: String? = null,
    val audioUrl: String? = null,
    val position: Int,
    val lastSyncTime: Long = System.currentTimeMillis()
)
