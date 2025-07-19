package com.plyr.service

import android.content.Context
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.network.AudioRepository
import com.plyr.utils.Config
import kotlinx.coroutines.*

class YouTubeSearchManager(private val context: Context) {
    
    private val localRepository = PlaylistLocalRepository(context)
    private var searchJob: Job? = null
    
    companion object {
        private const val TAG = "YouTubeSearchManager"
        private const val SEARCH_DELAY = 2000L // 2 segundos entre búsquedas para evitar rate limits
    }
    
    /**
     * Buscar IDs de YouTube para todos los tracks de una playlist que no los tengan
     */
    fun searchYouTubeIdsForPlaylist(playlistId: String) {
        searchJob?.cancel()
        
        searchJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Iniciando búsqueda de YouTube IDs para playlist: $playlistId")
                
                val tracks = localRepository.getTracksWithAutoSync(playlistId)
                val tracksWithoutYouTubeId = tracks.filter { it.youtubeVideoId == null }
                
                Log.d(TAG, "Encontrados ${tracksWithoutYouTubeId.size} tracks sin YouTube ID")
                
                for ((index, track) in tracksWithoutYouTubeId.withIndex()) {
                    if (!isActive) break // Verificar si la corrutina fue cancelada
                    
                    Log.d(TAG, "Buscando YouTube ID para: ${track.name} - ${track.artists} (${index + 1}/${tracksWithoutYouTubeId.size})")
                    
                    val youtubeId = searchYouTubeId(track)
                    if (youtubeId != null) {
                        Log.d(TAG, "YouTube ID encontrado: $youtubeId")
                        localRepository.updateTrackYoutubeId(track.id, youtubeId)
                    } else {
                        Log.w(TAG, "No se encontró YouTube ID para: ${track.name} - ${track.artists}")
                    }
                    
                    // Esperar antes de la siguiente búsqueda
                    delay(SEARCH_DELAY)
                }
                
                Log.d(TAG, "Búsqueda de YouTube IDs completada para playlist: $playlistId")
            } catch (e: Exception) {
                Log.e(TAG, "Error en búsqueda de YouTube IDs", e)
            }
        }
    }
    
    /**
     * Buscar ID de YouTube para un track específico
     */
    private suspend fun searchYouTubeId(track: TrackEntity): String? = withContext(Dispatchers.IO) {
        try {
            // Construir query de búsqueda
            val searchQuery = "${track.name} ${track.artists}".trim()
            Log.d(TAG, "Query de búsqueda: $searchQuery")
            
            // Obtener configuración del backend (si está disponible)
            val baseUrl = Config.getNgrokUrl(context)
            val apiKey = Config.getApiToken(context)
            
            if (baseUrl.isNotEmpty() && apiKey.isNotEmpty()) {
                return@withContext searchUsingBackend(searchQuery, baseUrl, apiKey)
            } else {
                Log.w(TAG, "Backend no configurado, no se puede buscar YouTube ID")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando YouTube ID para track: ${track.name}", e)
            return@withContext null
        }
    }
    
    /**
     * Buscar usando el backend configurado
     */
    private suspend fun searchUsingBackend(query: String, baseUrl: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        return@withContext suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { 
                Log.d(TAG, "Búsqueda cancelada para: $query") 
            }
            
            AudioRepository.searchAudios(query, baseUrl, apiKey) { results, error ->
                if (error != null) {
                    Log.e(TAG, "Error en búsqueda del backend: $error")
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(null))
                    }
                } else if (results != null && results.isNotEmpty()) {
                    // Tomar el primer resultado y extraer el ID del video
                    val firstResult = results[0]
                    val videoId = extractVideoIdFromUrl(firstResult.url)
                    Log.d(TAG, "Video ID extraído: $videoId")
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(videoId))
                    }
                } else {
                    Log.w(TAG, "No se encontraron resultados en el backend")
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(null))
                    }
                }
            }
        }
    }
    
    /**
     * Extraer ID de video de una URL de YouTube
     */
    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("v") ?: run {
                // Intentar otros formatos de URL
                val segments = uri.pathSegments
                if (segments.isNotEmpty() && segments.last().length == 11) {
                    segments.last()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo video ID de URL: $url", e)
            null
        }
    }
    
    /**
     * Cancelar búsquedas en curso
     */
    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        Log.d(TAG, "Búsqueda de YouTube IDs cancelada")
    }
    
    /**
     * Verificar si hay una búsqueda en curso
     */
    fun isSearching(): Boolean {
        return searchJob?.isActive == true
    }
    
    /**
     * Limpiar recursos
     */
    fun cleanup() {
        cancelSearch()
    }
}
