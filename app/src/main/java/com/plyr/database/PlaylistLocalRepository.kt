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

/**
 * PlaylistLocalRepository - Repositorio para gestión local de playlists y tracks
 * 
 * Esta clase maneja:
 * - Operaciones CRUD sobre playlists y tracks en base de datos local
 * - Sincronización automática con Spotify
 * - Caché inteligente para evitar consultas innecesarias
 * - Observación de cambios mediante LiveData/Flow
 * - Gestión de YouTube IDs para tracks
 * 
 * Proporciona una capa de abstracción entre la UI y la base de datos,
 * asegurando que los datos estén siempre actualizados y sincronizados.
 */
class PlaylistLocalRepository(context: Context) {

    // === PROPIEDADES ===

    /** Base de datos local para playlists y tracks */
    private val database = PlaylistDatabase.getDatabase(context)

    /** DAO para operaciones sobre playlists */
    private val playlistDao = database.playlistDao()

    /** DAO para operaciones sobre tracks */
    private val trackDao = database.trackDao()

    /** Contexto de aplicación para operaciones persistentes */
    private val appContext = context.applicationContext

    // === CONSTANTES ===

    companion object {
        private const val TAG = "PlaylistLocalRepo"

        /** Intervalo de sincronización automática (24 horas) */
        private const val SYNC_INTERVAL = 24 * 60 * 60 * 1000L
    }

    // === MÉTODOS PÚBLICOS - OBSERVACIÓN DE DATOS ===

    /**
     * Observa todas las playlists locales mediante LiveData.
     * Los cambios se notifican automáticamente a los observadores.
     *
     * @return LiveData que emite la lista de playlists cuando cambian
     */
    fun getAllPlaylistsLiveData(): LiveData<List<PlaylistEntity>> {
        return playlistDao.getAllPlaylists().asLiveData()
    }

    /**
     * Observa los tracks de una playlist específica.
     *
     * @param playlistId ID de la playlist a observar
     * @return LiveData que emite los tracks cuando cambian
     */
    fun getTracksByPlaylistLiveData(playlistId: String): LiveData<List<TrackEntity>> {
        return trackDao.getTracksByPlaylist(playlistId).asLiveData()
    }

    // === MÉTODOS PÚBLICOS - SINCRONIZACIÓN AUTOMÁTICA ===

    /**
     * Obtiene todas las playlists con sincronización automática.
     * Verifica si es necesario sincronizar con Spotify y lo hace automáticamente.
     *
     * @return Lista de playlists actualizadas
     */
    suspend fun getPlaylistsWithAutoSync(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obteniendo playlists con sincronización automática")

        val localPlaylists = playlistDao.getAllPlaylistsSync()

        // Verificar si necesita sincronización
        val shouldSync = shouldSyncPlaylists(localPlaylists)

        return@withContext if (shouldSync) {
            Log.d(TAG, "Iniciando sincronización de playlists")
            syncPlaylistsFromSpotify()
            playlistDao.getAllPlaylistsSync()
        } else {
            Log.d(TAG, "Las playlists están actualizadas")
            localPlaylists
        }
    }

    /**
     * Obtiene los tracks de una playlist con sincronización automática.
     * Verifica si es necesario sincronizar con Spotify y lo hace automáticamente.
     *
     * @param playlistId ID de la playlist
     * @return Lista de tracks actualizados
     */
    suspend fun getTracksWithAutoSync(playlistId: String): List<TrackEntity> =
        withContext(Dispatchers.IO) {
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

    // === MÉTODOS PÚBLICOS - SINCRONIZACIÓN MANUAL ===

    /**
     * Sincroniza todas las playlists desde Spotify de forma manual.
     *
     * @return true si la sincronización fue exitosa, false en caso contrario
     */
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
                        Log.d(
                            TAG,
                            "Guardadas ${playlistEntities.size} playlists en base de datos local"
                        )
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

    /**
     * Sincroniza los tracks de una playlist específica desde Spotify.
     *
     * @param playlistId ID de la playlist a sincronizar
     * @return true si la sincronización fue exitosa, false en caso contrario
     */
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
                    Log.d(
                        TAG,
                        "Recibidos ${tracks.size} tracks de Spotify para playlist $playlistId"
                    )

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

    /**
     * Actualiza el YouTube video ID para un track específico.
     * Permite asociar un video de YouTube con un track de Spotify.
     *
     * @param trackId ID único del track
     * @param youtubeVideoId ID del video de YouTube
     */
    suspend fun updateTrackYoutubeId(trackId: String, youtubeVideoId: String) =
        withContext(Dispatchers.IO) {
            try {
                trackDao.updateYoutubeVideoId(trackId, youtubeVideoId)
                Log.d(TAG, "YouTube ID actualizado para track $trackId: $youtubeVideoId")
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando YouTube ID", e)
            }
        }

    // === MÉTODOS PRIVADOS - VERIFICACIÓN DE SINCRONIZACIÓN ===

    /**
     * Verifica si las playlists necesitan sincronización con Spotify.
     *
     * @param localPlaylists Lista de playlists locales
     * @return true si necesita sincronización, false en caso contrario
     */
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

    /**
     * Verifica si los tracks de una playlist necesitan sincronización.
     *
     * @param playlist Entidad de la playlist a verificar
     * @return true si necesita sincronización, false en caso contrario
     */
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

    /**
     * Obtiene un token de acceso válido para Spotify.
     * Intenta usar el token actual o refrescarlo automáticamente.
     *
     * @return Token de acceso válido o null si no se pudo obtener
     */
    private suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val accessToken = Config.getSpotifyAccessToken(appContext)
        if (accessToken != null) {
            return@withContext accessToken
        }

        val refreshToken = Config.getSpotifyRefreshToken(appContext)
        if (refreshToken != null) {
            var newToken: String? = null
            SpotifyRepository.refreshAccessToken(appContext, refreshToken) { token, error ->
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

    // === MÉTODOS PÚBLICOS - OPERACIONES ESPECIALES ===

    /**
     * Fuerza una sincronización completa de todas las playlists y tracks.
     * Útil para refrescar completamente los datos locales.
     *
     * @return true si toda la sincronización fue exitosa, false en caso contrario
     */
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

    /**
     * Limpia todos los datos locales de playlists y tracks.
     * Útil para reset completo o troubleshooting.
     */
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
