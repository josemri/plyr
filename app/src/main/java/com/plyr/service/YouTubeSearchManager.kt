package com.plyr.service

import android.content.Context
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.network.SimpleDownloader
import kotlinx.coroutines.*

class YouTubeSearchManager(private val context: Context) {
    
    private val localRepository = PlaylistLocalRepository(context)
    private var searchJob: Job? = null
    private var isInitialized = false
    
    companion object {
        private const val TAG = "YouTubeSearchManager"
        private const val SEARCH_DELAY = 2000L // 2 segundos entre búsquedas para evitar rate limits
    }
    
    /**
     * Inicializa NewPipe (se ejecuta automáticamente al primer uso)
     */
    private fun initialize() {
        if (!isInitialized) {
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
    }
    
    /**
     * Obtener YouTube ID de forma transparente para el usuario
     * Si el track ya tiene ID lo devuelve, si no lo busca y lo guarda automáticamente
     * Esta función hace que la obtención de IDs sea invisible al usuario - no importa si el ID 
     * ya existe o hay que buscarlo, la experiencia es la misma.
     * @param track El track para el cual obtener el YouTube ID
     * @return El YouTube ID o null si no se encuentra
     */
    suspend fun getYouTubeIdTransparently(track: TrackEntity): String? = withContext(Dispatchers.IO) {
        try {
            // Si ya tiene YouTube ID, devolverlo directamente
            if (!track.youtubeVideoId.isNullOrBlank()) {
                Log.d(TAG, "🎯 YouTube ID reutilizado desde BD: ${track.name} → ${track.youtubeVideoId}")
                return@withContext track.youtubeVideoId
            }
            
            // Si no tiene ID, buscarlo de forma transparente
            Log.d(TAG, "🔍 Buscando YouTube ID transparentemente para: ${track.name} - ${track.artists}")
            
            val searchQuery = "${track.name} ${track.artists}".trim()
            val videoId = searchSingleVideoId(searchQuery)
            
            if (videoId != null) {
                // Guardar el ID encontrado en la base de datos para uso futuro
                localRepository.updateTrackYoutubeId(track.id, videoId)
                Log.d(TAG, "💾 YouTube ID encontrado y guardado: $videoId para ${track.name}")
                return@withContext videoId
            } else {
                Log.w(TAG, "❌ No se encontró YouTube ID para: ${track.name} - ${track.artists}")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo YouTube ID transparentemente para: ${track.name}", e)
            return@withContext null
        }
    }
    
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
     * Buscar videos de YouTube usando NewPipe Extractor
     * @param query La cadena de búsqueda
     * @param maxResults El número máximo de resultados a devolver (default: 5)
     * @return Lista de IDs de video encontrados
     */
    suspend fun searchYouTubeVideos(query: String, maxResults: Int = 5): List<String> = withContext(Dispatchers.IO) {
        try {
            initialize()
            Log.d(TAG, "🔍 Buscando en YouTube: '$query' (máximo $maxResults resultados)")
            
            val youtube = org.schabi.newpipe.extractor.ServiceList.YouTube
            val searchExtractor = youtube.getSearchExtractor(query)
            
            // Realizar la búsqueda
            searchExtractor.fetchPage()
            
            val videoIds = mutableListOf<String>()
            val items = searchExtractor.initialPage.items
            
            // Filtrar solo videos (no playlists, canales, etc.)
            val videos = items.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            
            Log.d(TAG, "📊 Encontrados ${videos.size} videos en total")
            
            // Extraer los IDs de video hasta el máximo solicitado
            for (video in videos.take(maxResults)) {
                val videoId = extractVideoIdFromUrl(video.url)
                if (videoId != null) {
                    videoIds.add(videoId)
                    Log.d(TAG, "✅ Video encontrado: ${video.name} → ID: $videoId")
                }
            }
            
            Log.d(TAG, "🎯 Extraídos ${videoIds.size} IDs de video válidos")
            return@withContext videoIds
            
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando con NewPipe", e)
            return@withContext emptyList()
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
     * Obtener información detallada de videos de YouTube
     * @param query La cadena de búsqueda
     * @param maxResults El número máximo de resultados a devolver
     * @return Lista de objetos con información detallada de los videos
     */
    suspend fun searchYouTubeVideosDetailed(query: String, maxResults: Int = 5): List<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        try {
            initialize()
            Log.d(TAG, "🔍 Buscando información detallada en YouTube: '$query'")
            
            val youtube = org.schabi.newpipe.extractor.ServiceList.YouTube
            val searchExtractor = youtube.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val videoInfoList = mutableListOf<YouTubeVideoInfo>()
            val videos = searchExtractor.initialPage.items
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .take(maxResults)
            
            for (video in videos) {
                try {
                    val videoId = extractVideoIdFromUrl(video.url)
                    if (videoId != null) {
                        val videoInfo = YouTubeVideoInfo(
                            videoId = videoId,
                            title = video.name ?: "Título desconocido",
                            uploader = try { video.uploaderName ?: "Desconocido" } catch (e: Exception) { "Desconocido" },
                            duration = try { video.duration } catch (e: Exception) { 0L },
                            viewCount = try { video.viewCount } catch (e: Exception) { 0L },
                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                        )
                        videoInfoList.add(videoInfo)
                        Log.d(TAG, "✅ Video detallado: ${videoInfo.title} por ${videoInfo.uploader}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error procesando video: ${e.message}")
                }
            }
            
            Log.d(TAG, "🎯 Total procesados: ${videoInfoList.size} videos")
            
            return@withContext videoInfoList
            
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo información detallada", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Buscar solo un ID de video (método de conveniencia)
     * @param query La cadena de búsqueda
     * @return El ID del primer video encontrado o null
     */
    suspend fun searchSingleVideoId(query: String): String? {
        val results = searchYouTubeVideos(query, 1)
        return results.firstOrNull()
    }
    
    /**
     * Ejemplo de uso: Buscar múltiples videos
     * Uso: val videos = youtubeSearchManager.searchMultipleVideos("Imagine Dragons Thunder", 3)
     */
    suspend fun searchMultipleVideos(searchQuery: String, count: Int): List<String> {
        return searchYouTubeVideos(searchQuery, count)
    }
    
    /**
     * Ejemplo de uso: Buscar con información completa
     * Uso: val videosInfo = youtubeSearchManager.searchWithFullInfo("Ed Sheeran Perfect", 5)
     */
    suspend fun searchWithFullInfo(searchQuery: String, count: Int): List<YouTubeVideoInfo> {
        return searchYouTubeVideosDetailed(searchQuery, count)
    }
    
    /**
     * Ejemplo de uso: Buscar solo el mejor resultado
     * Uso: val videoId = youtubeSearchManager.findBestMatch("Adele Someone Like You")
     */
    suspend fun findBestMatch(searchQuery: String): String? {
        return searchSingleVideoId(searchQuery)
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
}
