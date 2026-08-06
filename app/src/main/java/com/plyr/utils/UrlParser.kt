package com.plyr.utils

import com.plyr.model.ScanResult

/**
 * Parsing centralizado de URLs de YouTube/Spotify y construcción de thumbnails.
 * Pura (sin dependencias Android) para poder testearla en JVM.
 */
object UrlParser {

    /**
     * Extrae el ID de video de una URL de YouTube.
     * Soporta watch?v=, youtu.be/, /watch/, /shorts/ y un fallback de segmento de 11 caracteres.
     */
    fun extractYoutubeVideoId(url: String): String? = runCatching {
        when {
            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> {
                val candidate = url.substringAfterLast("/").substringBefore("?").substringBefore("&")
                if (candidate.length == 11) candidate else null
            }
        }
    }.getOrNull()

    /**
     * Extrae el ID de playlist de una URL de YouTube (list= o /playlist/).
     */
    fun extractYoutubePlaylistId(url: String): String? = runCatching {
        when {
            url.contains("list=") -> url.substringAfter("list=").substringBefore("&")
            url.contains("/playlist/") -> url.substringAfterLast("/playlist/").substringBefore("?")
            else -> null
        }
    }.getOrNull()

    /**
     * Extrae el ID de Spotify del último segmento de la URL.
     */
    fun extractSpotifyId(url: String): String? = runCatching {
        url.substringAfterLast("/").substringBefore("?").takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Parsea un texto (URL de Spotify/YouTube o formato legacy "plyr_source:type:id")
     * a un ScanResult.
     */
    fun parseScanText(text: String): ScanResult? = runCatching {
        when {
            text.contains("open.spotify.com/") || text.contains("spotify.com/") -> {
                val path = text.substringAfter("spotify.com/").substringBefore("?").substringBefore("#")
                val segments = path.split("/").filter { it.isNotBlank() }
                if (segments.size >= 2) ScanResult("spotify", segments[0], segments[1]) else null
            }
            text.contains("youtube.com") || text.contains("youtu.be") -> {
                extractYoutubeVideoId(text)?.let { ScanResult("youtube", "track", it) }
            }
            text.startsWith("plyr_spotify:") || text.startsWith("plyr_youtube:") -> {
                val parts = text.split(":")
                if (parts.size >= 3) ScanResult(parts[0].removePrefix("plyr_"), parts[1], parts[2]) else null
            }
            else -> null
        }
    }.getOrNull()

    enum class UrlType {
        YOUTUBE,
        SPOTIFY,
        UNKNOWN
    }

    /**
     * Identifica el tipo de URL (YouTube/Spotify/desconocida).
     */
    fun getUrlType(url: String): UrlType {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") -> UrlType.YOUTUBE
            lowerUrl.contains("spotify.com") || lowerUrl.contains("open.spotify.com") -> UrlType.SPOTIFY
            else -> UrlType.UNKNOWN
        }
    }

    /**
     * True si la URL es de YouTube/Spotify o un enlace http(s) genérico.
     */
    fun isPlayableUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be") ||
            lowerUrl.contains("spotify.com") || lowerUrl.contains("open.spotify.com") ||
            lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")
    }

    /**
     * Construye la miniatura mqdefault (16:9) de un video de YouTube.
     */
    fun youtubeThumbnailUrl(videoId: String?): String? =
        videoId?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" }

    /**
     * Normaliza cualquier miniatura de YouTube a mqdefault.jpg (16:9) cuando es posible.
     */
    fun normalizeYoutubeThumb(url: String?): String? {
        if (url == null) return null
        val regex = Regex("""(https?://(img\.youtube\.com|i\.ytimg\.com)/vi/[^/]+/)[^/]+\.jpg""")
        return regex.replace(url) { "${it.groupValues[1]}mqdefault.jpg" }
    }
}
