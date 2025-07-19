package com.plyr.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyTrack
import com.plyr.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class PlaylistLocalRepository(context: Context) {
    
    private val database = PlaylistDatabase.getDatabase(context)
    private val playlistDao = database.playlistDao()
    private val trackDao = database.trackDao()
    private val appContext = context.applicationContext
    
    companion object {
        private const val TAG = "PlaylistLocalRepo"
        private const val SYNC_INTERVAL = 24 * 60 * 60 * 1000L // 24 horas en millisegundos
    }
    
    // Observar playlists locales
    fun getAllPlaylistsLiveData(): LiveData<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists().asLiveData()
    }
    
    // Observar tracks de una playlist
    fun getTracksByPlaylistLiveData(playlistId: String): LiveData<List<TrackEntity>> {
        return trackDao.getTracksByPlaylist(playlistId).asLiveData()
    }
    
    // Obtener playlists sincronizadas (con sincronización automática)
    suspend fun getPlaylistsWithAutoSync(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obteniendo playlists con sincronización automática")
        
        val localPlaylists = playlistDao.getAllPlaylistsSync()
        
        // Verificar si necesita sincronización
        val shouldSync = shouldSyncPlaylists(localPlaylists)
        
        if (shouldSync) {
            Log.d(TAG, "Iniciando sincronización de playlists")
            syncPlaylistsFromSpotify()
            playlistDao.getAllPlaylistsSync()
        } else {
            Log.d(TAG, "Las playlists están actualizadas")
            localPlaylists
        }
    }
    
    // Obtener tracks de una playlist con sincronización automática
    suspend fun getTracksWithAutoSync(playlistId: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obteniendo tracks para playlist $playlistId con sincronización automática")
        
        val localTracks = trackDao.getTracksByPlaylistSync(playlistId)
        val playlist = playlistDao.getPlaylistById(playlistId)
        
        // Verificar si necesita sincronización
        val shouldSync = shouldSyncTracks(playlist)
        
        if (shouldSync) {
            Log.d(TAG, "Iniciando sincronización de tracks para playlist $playlistId")
            syncTracksFromSpotify(playlistId)
            trackDao.getTracksByPlaylistSync(playlistId)
        } else {
            Log.d(TAG, "Los tracks están actualizados")
            localTracks
        }
    }
    
    // Sincronizar playlists desde Spotify
    suspend fun syncPlaylistsFromSpotify(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sincronizando playlists desde Spotify")
            
            val accessToken = getValidAccessToken()
            if (accessToken == null) {
                Log.e(TAG, "No se pudo obtener token de acceso válido")
                return@withContext false
            }
            
            var success = false
            SpotifyRepository.getUserPlaylists(accessToken) { playlists, error ->
                if (error != null) {
                    Log.e(TAG, "Error al obtener playlists: $error")
                } else if (playlists != null) {
                    Log.d(TAG, "Recibidas ${playlists.size} playlists de Spotify")
                    
                    // Convertir y guardar playlists
                    val playlistEntities = playlists.map { playlist ->
                        PlaylistEntity(
                            spotifyId = playlist.id,
                            name = playlist.name,
                            description = playlist.description,
                            trackCount = playlist.tracks?.total ?: 0,
                            imageUrl = playlist.getImageUrl(),
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                    
                    // Usar runBlocking dentro del callback para operaciones suspend
                    kotlinx.coroutines.runBlocking {
                        playlistDao.insertPlaylists(playlistEntities)
                        Log.d(TAG, "Guardadas ${playlistEntities.size} playlists en base de datos local")
                    }
                    success = true
                }
            }
            
            // Esperar a que termine el callback
            var attempts = 0
            while (!success && attempts < 50) { // 5 segundos máximo
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización de playlists", e)
            false
        }
    }
    
    // Sincronizar tracks de una playlist específica
    suspend fun syncTracksFromSpotify(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sincronizando tracks para playlist $playlistId desde Spotify")
            
            val accessToken = getValidAccessToken()
            if (accessToken == null) {
                Log.e(TAG, "No se pudo obtener token de acceso válido")
                return@withContext false
            }
            
            var success = false
            SpotifyRepository.getPlaylistTracks(accessToken, playlistId) { tracks, error ->
                if (error != null) {
                    Log.e(TAG, "Error al obtener tracks: $error")
                } else if (tracks != null) {
                    Log.d(TAG, "Recibidos ${tracks.size} tracks de Spotify para playlist $playlistId")
                    
                    // Convertir y guardar tracks
                    val trackEntities = tracks.mapIndexed { index, track ->
                        TrackEntity(
                            id = "${playlistId}_${track.id}",
                            playlistId = playlistId,
                            spotifyTrackId = track.id,
                            name = track.name,
                            artists = track.getArtistNames(),
                            youtubeVideoId = null, // Se llenará después si es necesario
                            position = index,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }
                    
                    // Usar runBlocking dentro del callback
                    kotlinx.coroutines.runBlocking {
                        // Eliminar tracks antiguos de esta playlist
                        trackDao.deleteTracksByPlaylist(playlistId)
                        // Insertar nuevos tracks
                        trackDao.insertTracks(trackEntities)
                        
                        // Actualizar tiempo de sincronización de la playlist
                        val playlist = playlistDao.getPlaylistById(playlistId)
                        playlist?.let {
                            playlistDao.updatePlaylist(it.copy(lastSyncTime = System.currentTimeMillis()))
                        }
                        
                        Log.d(TAG, "Guardados ${trackEntities.size} tracks en base de datos local")
                    }
                    success = true
                }
            }
            
            // Esperar a que termine el callback
            var attempts = 0
            while (!success && attempts < 50) { // 5 segundos máximo
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización de tracks", e)
            false
        }
    }
    
    // Actualizar YouTube video ID para un track
    suspend fun updateTrackYoutubeId(trackId: String, youtubeVideoId: String) = withContext(Dispatchers.IO) {
        try {
            trackDao.updateYoutubeVideoId(trackId, youtubeVideoId)
            Log.d(TAG, "YouTube ID actualizado para track $trackId: $youtubeVideoId")
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando YouTube ID", e)
        }
    }
    
    // Verificar si las playlists necesitan sincronización
    private suspend fun shouldSyncPlaylists(localPlaylists: List<PlaylistEntity>): Boolean {
        if (localPlaylists.isEmpty()) {
            Log.d(TAG, "No hay playlists locales, necesita sincronización")
            return true
        }
        
        val oldestSync = localPlaylists.minOfOrNull { it.lastSyncTime } ?: 0L
        val needsSync = (System.currentTimeMillis() - oldestSync) > SYNC_INTERVAL
        
        Log.d(TAG, "Verificación de sincronización de playlists: needsSync=$needsSync")
        return needsSync
    }
    
    // Verificar si los tracks de una playlist necesitan sincronización
    private suspend fun shouldSyncTracks(playlist: PlaylistEntity?): Boolean {
        if (playlist == null) {
            Log.d(TAG, "Playlist no encontrada, necesita sincronización")
            return true
        }
        
        val localTracks = trackDao.getTracksByPlaylistSync(playlist.spotifyId)
        if (localTracks.isEmpty()) {
            Log.d(TAG, "No hay tracks locales, necesita sincronización")
            return true
        }
        
        val needsSync = (System.currentTimeMillis() - playlist.lastSyncTime) > SYNC_INTERVAL
        Log.d(TAG, "Verificación de sincronización de tracks: needsSync=$needsSync")
        return needsSync
    }
    
    // Obtener token de acceso válido
    private suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val accessToken = Config.getSpotifyAccessToken(appContext)
        if (accessToken != null) {
            return@withContext accessToken
        }
        
        val refreshToken = Config.getSpotifyRefreshToken(appContext)
        if (refreshToken != null) {
            var newToken: String? = null
            SpotifyRepository.refreshAccessToken(refreshToken) { token, error ->
                if (token != null) {
                    Config.setSpotifyTokens(appContext, token, refreshToken, 3600)
                    newToken = token
                }
            }
            
            // Esperar respuesta
            var attempts = 0
            while (newToken == null && attempts < 30) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            
            return@withContext newToken
        }
        
        return@withContext null
    }
    
    // Forzar sincronización completa
    suspend fun forceSyncAll(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Forzando sincronización completa")
        val playlistsSuccess = syncPlaylistsFromSpotify()
        
        if (playlistsSuccess) {
            val playlists = playlistDao.getAllPlaylistsSync()
            var allTracksSuccess = true
            
            for (playlist in playlists) {
                val tracksSuccess = syncTracksFromSpotify(playlist.spotifyId)
                if (!tracksSuccess) {
                    allTracksSuccess = false
                }
            }
            
            return@withContext allTracksSuccess
        }
        
        return@withContext false
    }
    
    // Limpiar datos locales
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        try {
            trackDao.deleteAllTracks()
            playlistDao.deleteAllPlaylists()
            Log.d(TAG, "Datos locales limpiados")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando datos locales", e)
        }
    }
}
