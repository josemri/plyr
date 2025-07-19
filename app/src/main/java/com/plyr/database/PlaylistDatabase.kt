package com.plyr.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [PlaylistEntity::class, TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PlaylistDatabase : RoomDatabase() {
    
    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    
    companion object {
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null
        
        fun getDatabase(context: Context): PlaylistDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlist_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
