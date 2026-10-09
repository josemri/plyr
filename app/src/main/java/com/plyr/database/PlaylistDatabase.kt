package com.plyr.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.TypeConverters

@TypeConverters(PlaylistConverters::class)
@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        SearchHistoryEntity::class
    ],
    autoMigrations = [
    ],
    
    version = 8,
    exportSchema = false
)
abstract class PlaylistDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun trackDao(): TrackDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS downloaded_tracks")
                db.execSQL("DROP TABLE IF EXISTS local_playlists")
                db.execSQL("DROP TABLE IF EXISTS local_playlist_tracks")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists RENAME COLUMN spotifyId TO remoteId")
                db.execSQL("ALTER TABLE tracks RENAME COLUMN spotifyTrackId TO remoteTrackId")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN source TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE playlists ADD COLUMN sourceId TEXT")
                // Backfill heurístico basado en lógica actual de PlaylistShare
                // LOCAL: id empieza con youtube_yt_
                db.execSQL("""
                    UPDATE playlists
                    SET source = 'LOCAL'
                    WHERE source = 'UNKNOWN'
                      AND remoteId LIKE 'youtube_yt_%'
                """.trimIndent())
                // SPOTIFY: marcado en description y id de 22 chars tras youtube_
                db.execSQL("""
                    UPDATE playlists
                    SET source = 'SPOTIFY',
                        sourceId = SUBSTR(remoteId, LENGTH('youtube_') + 1)
                    WHERE source = 'UNKNOWN'
                      AND LOWER(description) = LOWER('Imported from Spotify')
                      AND LENGTH(SUBSTR(remoteId, LENGTH('youtube_') + 1)) = 22
                      AND SUBSTR(remoteId, LENGTH('youtube_') + 1) GLOB '[A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9][A-Za-z0-9]'
                """.trimIndent())
                // YOUTUBE: prefijos PL/UU/FL/RD (incluye prefijo youtube_ o no)
                db.execSQL("""
                    UPDATE playlists
                    SET source = 'YOUTUBE'
                    WHERE source = 'UNKNOWN'
                      AND (remoteId LIKE 'PL%' OR remoteId LIKE 'UU%' OR remoteId LIKE 'FL%' OR remoteId LIKE 'RD%'
                           OR remoteId LIKE 'youtube_PL%' OR remoteId LIKE 'youtube_UU%' OR remoteId LIKE 'youtube_FL%' OR remoteId LIKE 'youtube_RD%')
                """.trimIndent())
                // LIKED: no compartible
                db.execSQL("""
                    UPDATE playlists
                    SET source = 'UNKNOWN'
                    WHERE remoteId = 'liked_songs'
                """.trimIndent())
            }
        }


        fun getDatabase(context: Context): PlaylistDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlist_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
