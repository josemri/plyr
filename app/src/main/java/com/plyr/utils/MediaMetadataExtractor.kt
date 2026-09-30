package com.plyr.utils

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

    suspend fun extractMetadata(url: String): MediaMetadata = withContext(Dispatchers.IO) {
        when {
            isYouTubeUrl(url) -> extractYouTubeMetadata(url)
            else -> MediaMetadata(url, null, null, MediaType.UNKNOWN)
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com", ignoreCase = true) ||
            url.contains("youtu.be", ignoreCase = true)
    }

    /**
     * Los ids de video de YouTube tienen exactamente 11 caracteres; los de
     * playlist (PL/RD/UU/FL/OLAK…) son más largos. Un `v=PL…` solo es una
     * playlist malformada si el valor NO tiene 11 caracteres: un video normal
     * puede empezar por "PL" sin ser playlist (B29).
     */
    private fun isPlaylistId(id: String): Boolean {
        if (id.length == 11) return false
        return id.startsWith("PL") || id.startsWith("UU") || id.startsWith("FL") || id.startsWith("RD")
    }

    private suspend fun extractYouTubeMetadata(url: String): MediaMetadata = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()

            val lower = url.lowercase()
            val isPlaylistUrl = lower.contains("list=") || lower.contains("/playlist")
            val vParam = Regex("[?&]v=([^&#]+)").find(url)?.groupValues?.get(1)
            val isPlaylistViaV = vParam?.let { isPlaylistId(it) } == true

            if (isPlaylistUrl || isPlaylistViaV) {
                // Extraer el ID de la playlist
                val playlistId = when {
                    isPlaylistUrl ->
                        Regex("[?&]list=([^&#]+)").find(url)?.groupValues?.get(1)
                            ?: url.substringAfterLast("/").substringBefore("?")
                    else -> vParam!!
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
