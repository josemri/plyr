package com.plyr.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.plyr.network.SpotifyRepository
import com.plyr.utils.SpotifyTokenManager
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

            // Obtener playlists del usuario con paginación
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
                        // Obtener IDs de playlists de Spotify
                        val spotifyPlaylistIds = playlists.map { it.id }.toSet()

                        // Obtener todas las playlists locales
                        val localPlaylists = playlistDao.getAllPlaylistsSync()

                        // Encontrar playlists que están en local pero no en Spotify
                        val playlistsToDelete = localPlaylists.filter { localPlaylist ->
                            localPlaylist.spotifyId !in spotifyPlaylistIds
                        }

                        // Eliminar playlists que ya no existen en Spotify
                        if (playlistsToDelete.isNotEmpty()) {
                            Log.d(TAG, "Eliminando ${playlistsToDelete.size} playlists que ya no existen en Spotify")
                            playlistsToDelete.forEach { playlist ->
                                Log.d(TAG, "Eliminando playlist local: ${playlist.name} (${playlist.spotifyId})")
                                // Primero eliminar los tracks de esta playlist
                                trackDao.deleteTracksByPlaylist(playlist.spotifyId)
                                // Luego eliminar la playlist
                                playlistDao.deletePlaylistById(playlist.spotifyId)
                            }
                        }

                        // Insertar o actualizar playlists de Spotify
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
                    val trackEntities = tracks.mapIndexedNotNull { index, playlistTrack ->
                        val track = playlistTrack.track
                        if (track != null) {
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
                        } else {
                            null
                        }
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
    private fun shouldSyncPlaylists(localPlaylists: List<PlaylistEntity>): Boolean {
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
     * Usa el nuevo SpotifyTokenManager para renovación automática.
     *
     * @return Token de acceso válido o null si no se pudo obtener
     */
    private suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        return@withContext SpotifyTokenManager.getValidAccessToken(appContext)
    }

    // === MÉTODOS PÚBLICOS - OPERACIONES ESPECIALES ===

    /**
     * Sincroniza las Liked Songs del usuario desde Spotify como una playlist especial.
     * Se guarda con el ID fijo "liked_songs" para fácil identificación.
     *
     * @return true si la sincronización fue exitosa, false en caso contrario
     */
    suspend fun syncLikedSongsFromSpotify(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sincronizando Liked Songs desde Spotify")

            val accessToken = getValidAccessToken()
            if (accessToken == null) {
                Log.e(TAG, "No se pudo obtener token de acceso válido")
                return@withContext false
            }

            var success = false
            SpotifyRepository.getUserSavedTracks(accessToken) { tracks, error ->
                if (error != null) {
                    Log.e(TAG, "Error al obtener Liked Songs: $error")
                } else if (tracks != null) {
                    Log.d(TAG, "Recibidas ${tracks.size} Liked Songs de Spotify")

                    // Crear playlist especial para Liked Songs
                    val likedPlaylist = PlaylistEntity(
                        spotifyId = "liked_songs",
                        name = "Liked Songs",
                        description = "Your favorite tracks on Spotify",
                        trackCount = tracks.size,
                        imageUrl = null,
                        lastSyncTime = System.currentTimeMillis()
                    )

                    // Convertir tracks a entidades
                    val trackEntities = tracks.mapIndexed { index, track ->
                        TrackEntity(
                            id = "liked_songs_${track.id}",
                            playlistId = "liked_songs",
                            spotifyTrackId = track.id,
                            name = track.name,
                            artists = track.getArtistNames(),
                            youtubeVideoId = null,
                            position = index,
                            lastSyncTime = System.currentTimeMillis()
                        )
                    }

                    // Guardar en base de datos
                    kotlinx.coroutines.runBlocking {
                        // Insertar o actualizar la playlist
                        playlistDao.insertPlaylist(likedPlaylist)
                        // Eliminar tracks antiguos
                        trackDao.deleteTracksByPlaylist("liked_songs")
                        // Insertar nuevos tracks
                        trackDao.insertTracks(trackEntities)
                        Log.d(TAG, "Guardadas Liked Songs en base de datos local: ${trackEntities.size} tracks")
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
            Log.e(TAG, "Error en sincronización de Liked Songs", e)
            false
        }
    }

    /**
     * Sincroniza los álbumes guardados del usuario desde Spotify.
     * Cada álbum se guarda como una playlist especial con el prefijo "album_"
     *
     * @return true si la sincronización fue exitosa, false en caso contrario
     */
    suspend fun syncSavedAlbumsFromSpotify(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sincronizando Saved Albums desde Spotify")

            val accessToken = getValidAccessToken()
            if (accessToken == null) {
                Log.e(TAG, "No se pudo obtener token de acceso válido")
                return@withContext false
            }

            var success = false
            SpotifyRepository.getUserSavedAlbums(accessToken) { albums, error ->
                if (error != null) {
                    Log.e(TAG, "Error al obtener Saved Albums: $error")
                } else if (albums != null) {
                    Log.d(TAG, "Recibidos ${albums.size} Saved Albums de Spotify")

                    kotlinx.coroutines.runBlocking {
                        // Obtener IDs de álbumes actuales de Spotify
                        val spotifyAlbumIds = albums.map { "album_${it.id}" }.toSet()

                        // Obtener todos los álbumes locales (playlists que empiezan con "album_")
                        val localPlaylists = playlistDao.getAllPlaylistsSync()
                        val localAlbums = localPlaylists.filter { it.spotifyId.startsWith("album_") }

                        // Encontrar álbumes que están en local pero no en Spotify
                        val albumsToDelete = localAlbums.filter { localAlbum ->
                            localAlbum.spotifyId !in spotifyAlbumIds
                        }

                        // Eliminar álbumes que ya no existen en Spotify
                        if (albumsToDelete.isNotEmpty()) {
                            Log.d(TAG, "Eliminando ${albumsToDelete.size} álbumes que ya no están guardados en Spotify")
                            albumsToDelete.forEach { album ->
                                Log.d(TAG, "Eliminando álbum local: ${album.name} (${album.spotifyId})")
                                trackDao.deleteTracksByPlaylist(album.spotifyId)
                                playlistDao.deletePlaylistById(album.spotifyId)
                            }
                        }

                        // Procesar cada álbum
                        albums.forEach { album ->
                            val albumPlaylistId = "album_${album.id}"

                            // Crear playlist especial para el álbum
                            val albumPlaylist = PlaylistEntity(
                                spotifyId = albumPlaylistId,
                                name = album.name,
                                description = "Album by ${album.getArtistNames()}",
                                trackCount = album.totaltracks ?: 0,
                                imageUrl = album.getImageUrl(),
                                lastSyncTime = System.currentTimeMillis()
                            )

                            playlistDao.insertPlaylist(albumPlaylist)

                            // Obtener tracks del álbum
                            SpotifyRepository.getAlbumTracks(accessToken, album.id) { tracks, trackError ->
                                if (tracks != null) {
                                    kotlinx.coroutines.runBlocking {
                                        val trackEntities = tracks.mapIndexed { index, track ->
                                            TrackEntity(
                                                id = "${albumPlaylistId}_${track.id}",
                                                playlistId = albumPlaylistId,
                                                spotifyTrackId = track.id,
                                                name = track.name,
                                                artists = track.getArtistNames(),
                                                youtubeVideoId = null,
                                                position = index,
                                                lastSyncTime = System.currentTimeMillis()
                                            )
                                        }
                                        trackDao.deleteTracksByPlaylist(albumPlaylistId)
                                        trackDao.insertTracks(trackEntities)
                                        Log.d(TAG, "Guardado álbum ${album.name} con ${trackEntities.size} tracks")
                                    }
                                }
                            }
                        }

                        Log.d(TAG, "Guardados ${albums.size} álbumes en base de datos local")
                    }
                    success = true
                }
            }

            // Esperar a que termine el callback
            var attempts = 0
            while (!success && attempts < 100) { // 10 segundos máximo (más tiempo para álbumes)
                kotlinx.coroutines.delay(100)
                attempts++
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error en sincronización de Saved Albums", e)
            false
        }
    }

    /**
     * Obtiene la playlist de Liked Songs desde la base de datos local.
     *
     * @return PlaylistEntity de Liked Songs o null si no existe
     */
    suspend fun getLikedSongsPlaylist(): PlaylistEntity? = withContext(Dispatchers.IO) {
        return@withContext playlistDao.getPlaylistById("liked_songs")
    }

    /**
     * Obtiene todos los álbumes guardados desde la base de datos local.
     *
     * @return Lista de PlaylistEntity que representan álbumes
     */
    suspend fun getSavedAlbums(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        val allPlaylists = playlistDao.getAllPlaylistsSync()
        return@withContext allPlaylists.filter { it.spotifyId.startsWith("album_") }
    }

    /**
     * Verifica si las Liked Songs necesitan sincronización.
     *
     * @return true si necesitan sincronización, false en caso contrario
     */
    private suspend fun shouldSyncLikedSongs(): Boolean {
        val likedPlaylist = playlistDao.getPlaylistById("liked_songs")
        if (likedPlaylist == null) {
            Log.d(TAG, "No hay Liked Songs locales, necesita sincronización")
            return true
        }

        val needsSync = (System.currentTimeMillis() - likedPlaylist.lastSyncTime) > SYNC_INTERVAL
        Log.d(TAG, "Verificación de sincronización de Liked Songs: needsSync=$needsSync")
        return needsSync
    }

    /**
     * Verifica si los álbumes guardados necesitan sincronización.
     *
     * @return true si necesitan sincronización, false en caso contrario
     */
    private suspend fun shouldSyncSavedAlbums(): Boolean {
        val savedAlbums = getSavedAlbums()
        if (savedAlbums.isEmpty()) {
            Log.d(TAG, "No hay álbumes guardados locales, necesita sincronización")
            return true
        }

        val oldestSync = savedAlbums.minOfOrNull { it.lastSyncTime } ?: 0L
        val needsSync = (System.currentTimeMillis() - oldestSync) > SYNC_INTERVAL

        Log.d(TAG, "Verificación de sincronización de álbumes: needsSync=$needsSync")
        return needsSync
    }

    /**
     * Obtiene Liked Songs con sincronización automática.
     * Verifica si es necesario sincronizar con Spotify y lo hace automáticamente.
     *
     * @return PlaylistEntity de Liked Songs actualizada o null
     */
    suspend fun getLikedSongsWithAutoSync(): PlaylistEntity? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obteniendo Liked Songs con sincronización automática")

        val shouldSync = shouldSyncLikedSongs()

        if (shouldSync) {
            Log.d(TAG, "Iniciando sincronización de Liked Songs")
            syncLikedSongsFromSpotify()
        }

        return@withContext playlistDao.getPlaylistById("liked_songs")
    }

    /**
     * Obtiene álbumes guardados con sincronización automática.
     * Verifica si es necesario sincronizar con Spotify y lo hace automáticamente.
     *
     * @return Lista de álbumes actualizados
     */
    suspend fun getSavedAlbumsWithAutoSync(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Obteniendo Saved Albums con sincronización automática")

        val shouldSync = shouldSyncSavedAlbums()

        if (shouldSync) {
            Log.d(TAG, "Iniciando sincronización de Saved Albums")
            syncSavedAlbumsFromSpotify()
        }

        return@withContext getSavedAlbums()
    }

    /**
     * Fuerza una sincronización completa de todas las playlists y tracks.
     * Útil para refrescar completamente los datos locales.
     *
     * @return true si toda la sincronización fue exitosa, false en caso contrario
     */
    suspend fun forceSyncAll(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Forzando sincronización completa")
        val playlistsSuccess = syncPlaylistsFromSpotify()
        val likedSongsSuccess = syncLikedSongsFromSpotify()
        val savedAlbumsSuccess = syncSavedAlbumsFromSpotify()

        if (playlistsSuccess) {
            val playlists = playlistDao.getAllPlaylistsSync()
            var allTracksSuccess = true

            for (playlist in playlists) {
                // Saltar liked_songs y álbumes ya que se sincronizaron arriba
                if (playlist.spotifyId == "liked_songs" || playlist.spotifyId.startsWith("album_")) continue

                val tracksSuccess = syncTracksFromSpotify(playlist.spotifyId)
                if (!tracksSuccess) {
                    allTracksSuccess = false
                }
            }

            return@withContext allTracksSuccess && likedSongsSuccess && savedAlbumsSuccess
        }

        return@withContext false
    }

}
