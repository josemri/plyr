package com.plyr.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

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
    entities = [PlaylistEntity::class, TrackEntity::class],
    version = 2,
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
    
    companion object {
        /** Instancia volátil para thread-safety */
        @Volatile
        private var INSTANCE: PlaylistDatabase? = null
        
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
                    .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
