package com.plyr.network

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Gestor unificado de YouTube - Maneja búsqueda y extracción de audio
 */
object YouTubeManager {
    private var isInitialized = false

    private fun ensureInitialized() {
        if (isInitialized) return
        try {
            NewPipe.init(SimpleDownloader.getInstance(), Localization("es", "ES"))
            isInitialized = true
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Busca un video en YouTube y devuelve su ID
     */
    fun searchVideoId(query: String): String? {
        return try {
            ensureInitialized()

            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()

            searchExtractor.initialPage.items
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .firstOrNull()?.url
                ?.substringAfterLast("=")
                ?.substringBefore("&")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extrae la URL de audio de un video de YouTube
     */
    fun getAudioUrl(videoId: String): String? {
        android.util.Log.d("YouTubeManager", "🎵 Iniciando extracción de audio para video ID: $videoId")
        return try {
            ensureInitialized()
            android.util.Log.d("YouTubeManager", "✅ NewPipe inicializado correctamente")

            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            android.util.Log.d("YouTubeManager", "🔗 URL del video: $videoUrl")

            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            android.util.Log.d("YouTubeManager", "📡 StreamExtractor creado, fetching page...")

            extractor.fetchPage()
            android.util.Log.d("YouTubeManager", "✅ Página fetched exitosamente")

            // Log detallado de los streams de audio disponibles
            val audioStreams = extractor.audioStreams
            android.util.Log.d("YouTubeManager", "🎧 Número de audio streams encontrados: ${audioStreams.size}")

            if (audioStreams.isEmpty()) {
                android.util.Log.e("YouTubeManager", "❌ ERROR: No se encontraron audio streams")
                android.util.Log.e("YouTubeManager", "📊 Video info - Nombre: ${extractor.name}")
                android.util.Log.e("YouTubeManager", "📊 Video info - Duración: ${extractor.length}")
                android.util.Log.e("YouTubeManager", "📊 Video info - Edad restringida: ${extractor.ageLimit}")
                return null
            }

            // Log de cada stream disponible
            audioStreams.forEachIndexed { index, stream ->
                android.util.Log.d("YouTubeManager", "🎵 Stream #$index:")
                android.util.Log.d("YouTubeManager", "   - Format: ${stream.format}")
                android.util.Log.d("YouTubeManager", "   - Bitrate: ${stream.averageBitrate}")
                android.util.Log.d("YouTubeManager", "   - URL disponible: ${stream.content != null}")
            }

            val firstStream = audioStreams.firstOrNull()
            if (firstStream == null) {
                android.util.Log.e("YouTubeManager", "❌ ERROR: No se pudo obtener el primer stream")
                return null
            }

            val audioUrl = firstStream.content
            if (audioUrl.isNullOrEmpty()) {
                android.util.Log.e("YouTubeManager", "❌ ERROR: URL de audio está vacía o es null")
                android.util.Log.e("YouTubeManager", "🔍 Intentando con URL property deprecated...")
                @Suppress("DEPRECATION")
                val deprecatedUrl = firstStream.url
                if (deprecatedUrl != null) {
                    android.util.Log.w("YouTubeManager", "⚠️ Usando URL deprecated: $deprecatedUrl")
                    return deprecatedUrl
                }
                return null
            }

            android.util.Log.d("YouTubeManager", "✅ ¡URL de audio extraída exitosamente!")
            android.util.Log.d("YouTubeManager", "🔗 Audio URL: $audioUrl")
            android.util.Log.d("YouTubeManager", "📏 Longitud URL: ${audioUrl.length} caracteres")

            audioUrl

        } catch (e: Exception) {
            android.util.Log.e("YouTubeManager", "❌ EXCEPCIÓN capturada durante extracción de audio", e)
            android.util.Log.e("YouTubeManager", "❌ Tipo de excepción: ${e.javaClass.simpleName}")
            android.util.Log.e("YouTubeManager", "❌ Mensaje: ${e.message}")
            android.util.Log.e("YouTubeManager", "❌ Stack trace:", e)
            null
        }
    }
}
