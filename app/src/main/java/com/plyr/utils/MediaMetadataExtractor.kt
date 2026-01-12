package com.plyr.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import com.plyr.network.SpotifyRepository
import kotlin.coroutines.resume

data class MediaMetadata(
    val title: String,
    val author: String? = null,
    val thumbnailUrl: String? = null,
    val type: MediaType
)

enum class MediaType {
    YOUTUBE_VIDEO,
    YOUTUBE_PLAYLIST,
    SPOTIFY_TRACK,
    SPOTIFY_PLAYLIST,
    SPOTIFY_ALBUM,
    SPOTIFY_ARTIST,
    UNKNOWN
}

object MediaMetadataExtractor {
    private var isInitialized = false

    private fun ensureInitialized() {
        if (isInitialized) return
        try {
            NewPipe.init(com.plyr.network.SimpleDownloader(), Localization("en", "US"))
            isInitialized = true
        } catch (_: Exception) {
            // Already initialized
        }
    }

    suspend fun extractMetadata(url: String, context: Context? = null): MediaMetadata = withContext(Dispatchers.IO) {
        when {
            isYouTubeUrl(url) -> extractYouTubeMetadata(url)
            isSpotifyUrl(url) -> {
                if (context != null && Config.isSpotifyConnected(context)) {
                    extractSpotifyMetadata(url, context)
                } else {
                    extractSpotifyMetadataFallback(url)
                }
            }
            else -> MediaMetadata(url, null, null, MediaType.UNKNOWN)
        }
    }

    private fun isYouTubeUrl(url: String): Boolean {
        return url.contains("youtube.com") || url.contains("youtu.be")
    }

    private fun isSpotifyUrl(url: String): Boolean {
        return url.contains("spotify.com")
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

    private suspend fun extractSpotifyMetadata(url: String, context: Context): MediaMetadata = withContext(Dispatchers.IO) {
        try {
            val accessToken = Config.getSpotifyAccessToken(context) ?: return@withContext extractSpotifyMetadataFallback(url)

            when {
                url.contains("/track/") -> {
                    val trackId = extractSpotifyId(url)
                    suspendCancellableCoroutine { continuation ->
                        SpotifyRepository.getTrack(accessToken, trackId) { track, error ->
                            if (track != null) {
                                continuation.resume(
                                    MediaMetadata(
                                        title = track.name,
                                        author = track.getArtistNames(),
                                        thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                                        type = MediaType.SPOTIFY_TRACK
                                    )
                                )
                            } else {
                                continuation.resume(extractSpotifyMetadataFallback(url))
                            }
                        }
                    }
                }
                url.contains("/playlist/") -> {
                    val playlistId = extractSpotifyId(url)
                    suspendCancellableCoroutine { continuation ->
                        SpotifyRepository.getPlaylist(accessToken, playlistId) { playlist, error ->
                            if (playlist != null) {
                                continuation.resume(
                                    MediaMetadata(
                                        title = playlist.name,
                                        author = playlist.description,
                                        thumbnailUrl = playlist.getImageUrl(),
                                        type = MediaType.SPOTIFY_PLAYLIST
                                    )
                                )
                            } else {
                                continuation.resume(extractSpotifyMetadataFallback(url))
                            }
                        }
                    }
                }
                url.contains("/album/") -> {
                    val albumId = extractSpotifyId(url)
                    suspendCancellableCoroutine { continuation ->
                        SpotifyRepository.getAlbum(accessToken, albumId) { album, error ->
                            if (album != null) {
                                continuation.resume(
                                    MediaMetadata(
                                        title = album.name,
                                        author = album.getArtistNames(),
                                        thumbnailUrl = album.getImageUrl(),
                                        type = MediaType.SPOTIFY_ALBUM
                                    )
                                )
                            } else {
                                continuation.resume(extractSpotifyMetadataFallback(url))
                            }
                        }
                    }
                }
                url.contains("/artist/") -> {
                    val artistId = extractSpotifyId(url)
                    suspendCancellableCoroutine { continuation ->
                        SpotifyRepository.getArtist(accessToken, artistId) { artist, error ->
                            if (artist != null) {
                                continuation.resume(
                                    MediaMetadata(
                                        title = artist.name,
                                        author = artist.genres?.joinToString(", "),
                                        thumbnailUrl = artist.getImageUrl(),
                                        type = MediaType.SPOTIFY_ARTIST
                                    )
                                )
                            } else {
                                continuation.resume(extractSpotifyMetadataFallback(url))
                            }
                        }
                    }
                }
                else -> extractSpotifyMetadataFallback(url)
            }
        } catch (_: Exception) {
            extractSpotifyMetadataFallback(url)
        }
    }

    private fun extractSpotifyMetadataFallback(url: String): MediaMetadata {
        val type = when {
            url.contains("/track/") -> MediaType.SPOTIFY_TRACK
            url.contains("/playlist/") -> MediaType.SPOTIFY_PLAYLIST
            url.contains("/album/") -> MediaType.SPOTIFY_ALBUM
            url.contains("/artist/") -> MediaType.SPOTIFY_ARTIST
            else -> MediaType.UNKNOWN
        }

        val title = when (type) {
            MediaType.SPOTIFY_TRACK -> "Spotify Track"
            MediaType.SPOTIFY_PLAYLIST -> "Spotify Playlist"
            MediaType.SPOTIFY_ALBUM -> "Spotify Album"
            MediaType.SPOTIFY_ARTIST -> "Spotify Artist"
            else -> "Spotify Content"
        }

        return MediaMetadata(
            title = title,
            author = null,
            thumbnailUrl = null,
            type = type
        )
    }

    private fun extractSpotifyId(url: String): String {
        // Extract ID from URLs like: https://open.spotify.com/track/ID?si=...
        return when {
            url.contains("/track/") -> url.substringAfter("/track/").substringBefore("?").substringBefore("/")
            url.contains("/playlist/") -> url.substringAfter("/playlist/").substringBefore("?").substringBefore("/")
            url.contains("/album/") -> url.substringAfter("/album/").substringBefore("?").substringBefore("/")
            url.contains("/artist/") -> url.substringAfter("/artist/").substringBefore("?").substringBefore("/")
            else -> ""
        }
    }
}
