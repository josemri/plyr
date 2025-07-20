package com.plyr.service

import android.content.Context
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.network.SimpleDownloader
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Gestor de búsquedas de YouTube usando NewPipe Extractor
 * 
 * Proporciona funcionalidades para:
 * - Búsqueda transparente de IDs de YouTube (con cache automático)
 * - Búsqueda de videos con información detallada
 * - Extracción de IDs desde URLs
 * - Gestión automática de inicialización de NewPipe
 * 
 * @param context Contexto de la aplicación Android
 */
class YouTubeSearchManager(private val context: Context) {
    
    // === DEPENDENCIES ===
    private val localRepository = PlaylistLocalRepository(context)
    
    // === STATE ===
    private var searchJob: Job? = null
    private var isInitialized = false
    
    // === CONSTANTS ===
    companion object {
        private const val TAG = "YouTubeSearchManager"
        private const val SEARCH_DELAY = 2000L // Delay entre búsquedas para evitar rate limits
        private const val MAX_RESULTS_DEFAULT = 5
    }
    
    // === INITIALIZATION ===
    
    /**
     * Inicializa NewPipe Extractor de forma lazy
     * Se ejecuta automáticamente en el primer uso
     * 
     * @throws Exception Si falla la inicialización de NewPipe
     */
    private fun initialize() {
        if (isInitialized) return
        
        try {
            val downloader = SimpleDownloader()
            val localization = org.schabi.newpipe.extractor.localization.Localization("en", "US")
            org.schabi.newpipe.extractor.NewPipe.init(downloader, localization)
            isInitialized = true
            Log.d(TAG, "✅ NewPipe inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al inicializar NewPipe", e)
            throw e
        }
    }
    
    // === CORE FUNCTIONALITY ===
    
    /**
     * FUNCIONALIDAD PRINCIPAL: Obtención transparente de YouTube IDs
     * 
     * Obtiene el YouTube ID de forma transparente para el usuario.
     * Si el track ya tiene ID lo devuelve inmediatamente desde cache,
     * si no lo busca automáticamente y lo guarda para uso futuro.
     * 
     * Esta función hace que la obtención de IDs sea completamente invisible 
     * al usuario - no importa si el ID ya existe o hay que buscarlo.
     * 
     * @param track El track para el cual obtener el YouTube ID
     * @return El YouTube ID o null si no se encuentra
     */
    suspend fun getYouTubeIdTransparently(track: TrackEntity): String? = withContext(Dispatchers.IO) {
        try {
            // Cache hit: Si ya tiene YouTube ID, devolverlo directamente
            if (!track.youtubeVideoId.isNullOrBlank()) {
                Log.d(TAG, "🎯 Cache hit: ${track.name} → ${track.youtubeVideoId}")
                return@withContext track.youtubeVideoId
            }
            
            // Cache miss: Buscar y guardar automáticamente
            Log.d(TAG, "🔍 Cache miss, buscando: ${track.name} - ${track.artists}")
            
            val searchQuery = buildSearchQuery(track)
            val videoId = searchSingleVideoId(searchQuery)
            
            return@withContext if (videoId != null) {
                // Guardar en cache para uso futuro
                localRepository.updateTrackYoutubeId(track.id, videoId)
                Log.d(TAG, "💾 ID encontrado y guardado: $videoId para ${track.name}")
                videoId
            } else {
                Log.w(TAG, "❌ No se encontró YouTube ID para: ${track.name} - ${track.artists}")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo YouTube ID para: ${track.name}", e)
            null
        }
    }
    
    // === SEARCH FUNCTIONALITY ===
    
    /**
     * Busca videos de YouTube usando NewPipe Extractor
     * 
     * @param query Cadena de búsqueda
     * @param maxResults Número máximo de resultados (default: 5)
     * @return Lista de IDs de video encontrados
     */
    suspend fun searchYouTubeVideos(
        query: String, 
        maxResults: Int = MAX_RESULTS_DEFAULT
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            initialize()
            Log.d(TAG, "🔍 Buscando: '$query' (máx $maxResults resultados)")
            
            val videoIds = performSearch(query, maxResults)
            
            Log.d(TAG, "🎯 Extraídos ${videoIds.size} IDs válidos")
            videoIds
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en búsqueda: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Busca videos con información detallada
     * 
     * @param query Cadena de búsqueda
     * @param maxResults Número máximo de resultados
     * @return Lista de objetos con información detallada
     */
    suspend fun searchYouTubeVideosDetailed(
        query: String, 
        maxResults: Int = MAX_RESULTS_DEFAULT
    ): List<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        try {
            initialize()
            Log.d(TAG, "🔍 Búsqueda detallada: '$query'")
            
            val videoInfoList = performDetailedSearch(query, maxResults)
            
            Log.d(TAG, "🎯 Procesados ${videoInfoList.size} videos")
            videoInfoList
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en búsqueda detallada: ${e.message}", e)
            emptyList()
        }
    }
    
    // === CONVENIENCE METHODS ===
    
    /**
     * Busca solo un ID de video (método de conveniencia)
     * 
     * @param query Cadena de búsqueda
     * @return El ID del primer video encontrado o null
     */
    suspend fun searchSingleVideoId(query: String): String? {
        return searchYouTubeVideos(query, 1).firstOrNull()
    }
    
    /**
     * Busca múltiples videos (método de conveniencia)
     * 
     * @param searchQuery Cadena de búsqueda  
     * @param count Número de videos a buscar
     * @return Lista de IDs de video
     */
    suspend fun searchMultipleVideos(searchQuery: String, count: Int): List<String> {
        return searchYouTubeVideos(searchQuery, count)
    }
    
    /**
     * Busca el mejor resultado (método de conveniencia)
     * 
     * @param searchQuery Cadena de búsqueda
     * @return El ID del mejor resultado o null
     */
    suspend fun findBestMatch(searchQuery: String): String? {
        return searchSingleVideoId(searchQuery)
    }
    
    // === PRIVATE HELPER METHODS ===
    
    /**
     * Buscar IDs de YouTube para todos los tracks de una playlist que no los tengan
     * @deprecated Este método es opcional ya que los IDs se obtienen bajo demanda cuando el usuario hace click en una canción.
     * Usar getYouTubeIdTransparently() para obtener IDs de forma invisible al usuario.
     */
    @Deprecated("Use getYouTubeIdTransparently() for on-demand ID fetching", ReplaceWith("getYouTubeIdTransparently(track)"))
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
     * Buscar ID de YouTube para un track específico usando solo NewPipe
     */
    private suspend fun searchYouTubeId(track: TrackEntity): String? = withContext(Dispatchers.IO) {
        try {
            // Construir query de búsqueda
            val searchQuery = "${track.name} ${track.artists}".trim()
            Log.d(TAG, "Query de búsqueda: $searchQuery")
            
            // Usar NewPipe para buscar
            val videoId = searchSingleVideoId(searchQuery)
            if (videoId != null) {
                Log.d(TAG, "Video ID encontrado con NewPipe: $videoId")
                return@withContext videoId
            }
            
            Log.w(TAG, "No se encontró YouTube ID para: $searchQuery")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando YouTube ID para track: ${track.name}", e)
            return@withContext null
        }
    }
    

    
    /**
     * Información detallada de un video de YouTube
     */
    data class YouTubeVideoInfo(
        val videoId: String,
        val title: String,
        val uploader: String,
        val duration: Long, // en segundos
        val viewCount: Long,
        val thumbnailUrl: String?
    ) {
        /**
         * Duración formateada en formato MM:SS o HH:MM:SS
         */
        fun getFormattedDuration(): String {
            if (duration <= 0) return "En vivo"
            
            val hours = duration / 3600
            val minutes = (duration % 3600) / 60
            val seconds = duration % 60
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
        
        /**
         * Número de vistas formateado (ej: 1.2M, 15K)
         */
        fun getFormattedViewCount(): String {
            return when {
                viewCount >= 1_000_000 -> "${(viewCount / 1_000_000.0).format(1)}M"
                viewCount >= 1_000 -> "${(viewCount / 1_000.0).format(1)}K"
                else -> viewCount.toString()
            }
        }
        
        private fun Double.format(digits: Int) = "%.${digits}f".format(this).removeSuffix("0").removeSuffix(".")
    }
    

    
    /**
     * Extraer ID de video de una URL de YouTube
     */
    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            when {
                url.contains("watch?v=") -> {
                    url.substringAfter("watch?v=").substringBefore("&")
                }
                url.contains("youtu.be/") -> {
                    url.substringAfter("youtu.be/").substringBefore("?")
                }
                url.contains("/watch/") -> {
                    url.substringAfter("/watch/").substringBefore("?")
                }
                else -> {
                    // Fallback: usar el método anterior
                    val uri = android.net.Uri.parse(url)
                    uri.getQueryParameter("v") ?: run {
                        val segments = uri.pathSegments
                        if (segments.isNotEmpty() && segments.last().length == 11) {
                            segments.last()
                        } else {
                            Log.w(TAG, "Formato de URL no reconocido: $url")
                            null
                        }
                    }
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
    
    // === MÉTODOS UTILITARIOS PRIVADOS ===
    
    /**
     * Realiza la búsqueda actual usando NewPipe Extractor
     * 
     * @param query Cadena de búsqueda
     * @param maxResults Número máximo de resultados
     * @return Lista de IDs de video
     */
    private suspend fun performSearch(query: String, maxResults: Int): List<String> {
        return try {
            Log.d(TAG, "🔍 Ejecutando búsqueda NewPipe: '$query'")
            
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val videoIds = mutableListOf<String>()
            val items = searchExtractor.initialPage.items
            
            for (item in items.take(maxResults)) {
                if (item is StreamInfoItem) {
                    val videoId = extractVideoIdFromUrl(item.url)
                    if (videoId != null && videoId.length == 11) {
                        videoIds.add(videoId)
                    }
                }
            }
            
            Log.d(TAG, "✅ NewPipe encontró ${videoIds.size} IDs válidos")
            videoIds
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en búsqueda NewPipe: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Realiza búsqueda detallada usando NewPipe Extractor
     * 
     * @param query Cadena de búsqueda
     * @param maxResults Número máximo de resultados
     * @return Lista de información detallada de videos
     */
    private suspend fun performDetailedSearch(query: String, maxResults: Int): List<YouTubeVideoInfo> {
        return try {
            Log.d(TAG, "🔍 Ejecutando búsqueda detallada NewPipe: '$query'")
            
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val videoInfoList = mutableListOf<YouTubeVideoInfo>()
            val items = searchExtractor.initialPage.items
            
            for (item in items.take(maxResults)) {
                if (item is StreamInfoItem) {
                    val videoId = extractVideoIdFromUrl(item.url)
                    if (videoId != null && videoId.length == 11) {
                        val videoInfo = YouTubeVideoInfo(
                            videoId = videoId,
                            title = item.name ?: "Sin título",
                            uploader = item.uploaderName ?: "Desconocido",
                            duration = item.duration,
                            viewCount = item.viewCount,
                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                        )
                        videoInfoList.add(videoInfo)
                    }
                }
            }
            
            Log.d(TAG, "✅ NewPipe procesó ${videoInfoList.size} videos detallados")
            videoInfoList
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en búsqueda detallada NewPipe: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Construye una query de búsqueda optimizada para un track.
     * Combina el nombre del track con los artistas de manera eficiente.
     * 
     * @param track El track para el cual construir la query
     * @return Query de búsqueda optimizada
     */
    private fun buildSearchQuery(track: TrackEntity): String {
        return "${track.name} ${track.artists}".trim()
    }
}
