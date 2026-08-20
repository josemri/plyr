package com.plyr.service

import android.content.Context
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.utils.NewPipeHolder
import com.plyr.utils.UrlParser
import com.plyr.utils.formatDurationSeconds
import kotlinx.coroutines.*
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
    
    // === CONSTANTS ===
    companion object {
        private const val TAG = "YouTubeSearchManager"
        private const val SEARCH_DELAY = 2000L // Delay entre búsquedas para evitar rate limits
        private const val MAX_RESULTS_DEFAULT = 50
    }
    
    // === INITIALIZATION ===
    
    /**
     * Inicializa NewPipe Extractor de forma lazy
     * Se ejecuta automáticamente en el primer uso
     * 
     * @throws Exception Si falla la inicialización de NewPipe
     */
    private fun initialize() {
        NewPipeHolder.ensureInitialized()
        Log.d(TAG, "✅ NewPipe inicializado correctamente")
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
                
                val tracks = com.plyr.database.PlaylistDatabase.getDatabase(context).trackDao().getTracksByPlaylistSync(playlistId)
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
        fun getFormattedDuration(): String = formatDurationSeconds(duration)

    }

    /**
     * Información detallada de una playlist de YouTube
     */
    data class YouTubePlaylistInfo(
        val playlistId: String,
        val title: String,
        val uploader: String,
        val videoCount: Int,
        val thumbnailUrl: String?,
        val description: String?,
        val channel: String? = uploader
    ) {
        /**
         * Número de videos formateado
         */
        fun getFormattedVideoCount(): String {
            return when {
                videoCount == 1 -> "1 video"
                videoCount < 1000 -> "$videoCount videos"
                videoCount >= 1000 -> "${(videoCount / 1000.0).format(1)}K videos"
                else -> "$videoCount videos"
            }
        }

        /**
         * Obtiene la URL de la imagen de la playlist
         */
        fun getImageUrl(): String? {
            return thumbnailUrl
        }

        private fun Double.format(digits: Int) = "%.${digits}f".format(this).removeSuffix("0").removeSuffix(".")
    }

    /**
     * Resultado combinado de búsqueda de YouTube (videos y playlists)
     */
    data class YouTubeSearchAllResult(
        val videos: List<YouTubeVideoInfo>,
        val playlists: List<YouTubePlaylistInfo>
    )

    // === NUEVAS FUNCIONES PARA PLAYLISTS ===

    /**
     * Busca tanto videos como playlists de YouTube
     *
     * @param query Cadena de búsqueda
     * @param maxVideos Número máximo de videos
     * @param maxPlaylists Número máximo de playlists
     * @return Resultado combinado con videos y playlists
     */
    suspend fun searchYouTubeAll(
        query: String,
        maxVideos: Int = 25,
        maxPlaylists: Int = 10
    ): YouTubeSearchAllResult = withContext(Dispatchers.IO) {
        try {
            initialize()
            Log.d(TAG, "🔍 Búsqueda completa YouTube: '$query' (videos: $maxVideos, playlists: $maxPlaylists)")

            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()

            val videos = mutableListOf<YouTubeVideoInfo>()
            val playlists = mutableListOf<YouTubePlaylistInfo>()
            val items = searchExtractor.initialPage.items

            for (item in items) {
                when (item) {
                    is StreamInfoItem -> {
                        if (videos.size < maxVideos) {
                            val videoId = UrlParser.extractYoutubeVideoId(item.url)
                            if (videoId != null && videoId.length == 11) {
                                videos.add(YouTubeVideoInfo(
                                    videoId = videoId,
                                    title = item.name,
                                    uploader = item.uploaderName ?: "Desconocido",
                                    duration = item.duration,
                                    viewCount = item.viewCount,
                                    thumbnailUrl = getThumbnailUrl(videoId)
                                ))
                            }
                        }
                    }
                    is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                        if (playlists.size < maxPlaylists) {
                            val playlistId = UrlParser.extractYoutubePlaylistId(item.url)
                            if (playlistId != null) {
                                playlists.add(YouTubePlaylistInfo(
                                    playlistId = playlistId,
                                    title = item.name,
                                    uploader = item.uploaderName ?: "Desconocido",
                                    videoCount = item.streamCount.toInt(),
                                    thumbnailUrl = getPlaylistThumbnailUrl(),
                                    description = null
                                ))
                            }
                        }
                    }
                }

                // Parar si ya tenemos suficientes resultados
                if (videos.size >= maxVideos && playlists.size >= maxPlaylists) {
                    break
                }
            }

            Log.d(TAG, "✅ YouTube búsqueda completa: ${videos.size} videos, ${playlists.size} playlists")
            YouTubeSearchAllResult(videos, playlists)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en búsqueda completa YouTube: ${e.message}", e)
            YouTubeSearchAllResult(emptyList(), emptyList())
        }
    }

    /**
     * Obtiene los videos de una playlist de YouTube
     *
     * @param playlistId ID de la playlist
     * @param maxVideos Número máximo de videos a obtener
     * @return Lista de información de videos de la playlist
     */
    suspend fun getYouTubePlaylistVideos(
        playlistId: String,
        maxVideos: Int = 100
    ): List<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        try {
            initialize()
            Log.d(TAG, "🔍 Obteniendo videos de playlist: $playlistId")

            val service = ServiceList.YouTube
            val playlistExtractor = service.getPlaylistExtractor("https://www.youtube.com/playlist?list=$playlistId")
            playlistExtractor.fetchPage()

            val videos = mutableListOf<YouTubeVideoInfo>()
            val items = playlistExtractor.initialPage.items

            for (item in items.take(maxVideos)) {
                if (item is StreamInfoItem) {
                    val videoId = UrlParser.extractYoutubeVideoId(item.url)
                    if (videoId != null && videoId.length == 11) {
                        videos.add(YouTubeVideoInfo(
                            videoId = videoId,
                            title = item.name,
                            uploader = item.uploaderName ?: "Desconocido",
                            duration = item.duration,
                            viewCount = item.viewCount,
                            thumbnailUrl = getThumbnailUrl(videoId)
                        ))
                    }
                }
            }

            Log.d(TAG, "✅ Obtenidos ${videos.size} videos de la playlist")
            videos

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo videos de playlist: ${e.message}", e)
            emptyList()
        }
    }

    // === MÉTODOS UTILITARIOS PRIVADOS ADICIONALES ===

    /**
     * Cancelar búsquedas en curso
     */
    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        Log.d(TAG, "Búsqueda de YouTube IDs cancelada")
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
    private fun performSearch(query: String, maxResults: Int): List<String> {
        return try {
            Log.d(TAG, "🔍 Ejecutando búsqueda NewPipe: '$query'")
            
            val service = ServiceList.YouTube
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val videoIds = mutableListOf<String>()
            val items = searchExtractor.initialPage.items
            
            for (item in items.take(maxResults)) {
                if (item is StreamInfoItem) {
                    val videoId = UrlParser.extractYoutubeVideoId(item.url)
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
     * Construye una query de búsqueda optimizada para un track.
     * Combina el nombre del track con los artistas de manera eficiente.
     * 
     * @param track El track para el cual construir la query
     * @return Query de búsqueda optimizada
     */
    private fun buildSearchQuery(track: TrackEntity): String {
        return "${track.name} ${track.artists}".trim()
    }

    /**
     * Genera URL de thumbnail para un video de YouTube
     */
    private fun getThumbnailUrl(videoId: String): String =
        UrlParser.youtubeThumbnailUrl(videoId) ?: ""

    /**
     * Genera URL de thumbnail para una playlist de YouTube
     */
    private fun getPlaylistThumbnailUrl(): String {
        return "https://img.youtube.com/vi/undefined/hqdefault.jpg" // Placeholder
    }
}
