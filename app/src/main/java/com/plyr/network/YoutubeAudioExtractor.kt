package com.plyr.network

import android.util.Log
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization

object YouTubeAudioExtractor {

    private const val TAG = "YouTubeExtractor"
    private var isInitialized = false

    private fun initialize() {
        if (!isInitialized) {
            try {
                val downloader = SimpleDownloader.getInstance()
                val localization = Localization("en", "US")
                NewPipe.init(downloader, localization)
                isInitialized = true
                Log.d(TAG, "✅ NewPipe inicializado")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al inicializar NewPipe", e)
                throw e
            }
        }
    }

    /**
     * Extrae la URL de audio de un video de YouTube
     *
     * @param videoId ID del video de YouTube (ej: "dQw4w9WgXcQ")
     * @return URL del stream de audio o null si hay error
     */
    fun getAudioUrl(videoId: String): String? {
        return try {
            initialize()

            Log.d(TAG, "🔍 Extrayendo audio para: $videoId")

            val videoUrl = "https://www.youtube.com/watch?v=$videoId"
            val extractor = ServiceList.YouTube.getStreamExtractor(videoUrl)
            extractor.fetchPage()

            Log.d(TAG, "📹 Video: ${extractor.name}")

            val audioStreams = extractor.audioStreams
            if (audioStreams.isNotEmpty()) {
                val audioUrl = audioStreams[0].url
                Log.d(TAG, "✅ URL extraída exitosamente")
                audioUrl
            } else {
                Log.w(TAG, "⚠️ No se encontraron streams de audio")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            null
        }
    }
}