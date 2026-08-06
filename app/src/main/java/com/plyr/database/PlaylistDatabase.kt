package com.plyr.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * PlaylistDatabase - Configuración principal de Room Database
 * 
 * Define la base de datos para playlists y tracks con:
 * - Entidades: PlaylistEntity y TrackEntity
 * - Versión: 1 (primera versión)
 * - DAOs: PlaylistDao y TrackDao
 * 
 * Implementa patrón Singleton thread-safe para garantizar una sola instancia.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        TrackEntity::class,
        SearchHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PlaylistDatabase : RoomDatabase() {
    
    /**
     * Acceso al DAO de playlists.
     */
    abstract fun playlistDao(): PlaylistDao
    
    /**
     * Acceso al DAO de tracks.
     */
    abstract fun trackDao(): TrackDao

    /**
     * Acceso al DAO del historial de búsquedas.
     */
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        /** Instancia volátil para thread-safety */
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null

        /**
         * Migración 5→6: elimina las tablas del feature local/descargas
         * (downloaded_tracks, local_playlists y local_playlist_tracks).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS downloaded_tracks")
                db.execSQL("DROP TABLE IF EXISTS local_playlists")
                db.execSQL("DROP TABLE IF EXISTS local_playlist_tracks")
            }
        }
        
        /**
         * Obtiene la instancia única de la base de datos.
         * Implementa patrón Singleton con double-checked locking.
         * 
         * @param context Contexto de aplicación
         * @return Instancia única de PlaylistDatabase
         */
        fun getDatabase(context: Context): PlaylistDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDatabase::class.java,
                    "playlist_database"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
