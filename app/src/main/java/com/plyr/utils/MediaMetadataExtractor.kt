package com.plyr.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

data class MediaMetadata(
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val type: MediaType
)

enum class MediaType {
    YOUTUBE_VIDEO,
    YOUTUBE_PLAYLIST,
    UNKNOWN
}

object MediaMetadataExtractor {
    private fun ensureInitialized() {
        NewPipeHolder.ensureInitialized()
    }

    suspend fun extractMetadata(url: String, context: Context? = null): MediaMetadata = withContext(Dispatchers.IO) {
        when {
            isYouTubeUrl(url) -> extractYouTubeMetadata(url)
            else -> MediaMetadata(url, null, null, MediaType.UNKNOWN)
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    private suspend fun extractYouTubeMetadata(url: String): MediaMetadata = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            // Detectar si es una playlist
            val isPlaylist = url.contains("list=") ||
                            url.contains("/playlist") ||
                            // Detectar IDs de playlist mal formateados en parámetro v=
                            (url.contains("v=PL") || url.contains("v=UU") || url.contains("v=FL") || url.contains("v=RD"))

            if (isPlaylist) {
                // Extraer el ID de la playlist
                val playlistId = when {
                    url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
                    // Si el ID está en v= y empieza con PL/UU/FL/RD, es una playlist malformada
                    url.contains("v=PL") -> url.substringAfter("v=PL").substringBefore("&").let { "PL$it" }
                    url.contains("v=UU") -> url.substringAfter("v=UU").substringBefore("&").let { "UU$it" }
                    url.contains("v=FL") -> url.substringAfter("v=FL").substringBefore("&").let { "FL$it" }
                    url.contains("v=RD") -> url.substringAfter("v=RD").substringBefore("&").let { "RD$it" }
                    else -> url.substringAfterLast("/").substringBefore("?")
                }

                val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
                val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl)
                extractor.fetchPage()

                MediaMetadata(
                    title = extractor.name,
                    author = extractor.uploaderName,
                    thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url,
                    type = MediaType.YOUTUBE_PLAYLIST
                )
            } else {
                // Es un video
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                extractor.fetchPage()

                MediaMetadata(
                    title = extractor.name,
                    author = extractor.uploaderName,
                    thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url,
                    type = MediaType.YOUTUBE_VIDEO
                )
            }
        } catch (e: Exception) {
            // Si falla, intentar como video antes de dar up
            try {
                ensureInitialized()
                val extractor = ServiceList.YouTube.getStreamExtractor(url)
                extractor.fetchPage()

                MediaMetadata(
                    title = extractor.name,
                    author = extractor.uploaderName,
                    thumbnailUrl = extractor.thumbnails.maxByOrNull { it.height }?.url,
                    type = MediaType.YOUTUBE_VIDEO
                )
            } catch (_: Exception) {
                MediaMetadata(url, null, null, MediaType.UNKNOWN)
            }
        }
    }
}
