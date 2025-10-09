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
            NewPipe.init(SimpleDownloader(), Localization("en", "US"))
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
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrae la URL de audio de un video de YouTube
     */
    fun getAudioUrl(videoId: String): String? {
        return try {
            ensureInitialized()

            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            extractor.audioStreams.firstOrNull()?.content
        } catch (e: Exception) {
            null
        }
    }
}
