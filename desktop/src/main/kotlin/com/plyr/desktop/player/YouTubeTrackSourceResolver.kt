package com.plyr.desktop.player

import com.plyr.desktop.download.DownloadedAudioStore
import com.plyr.network.YouTubeManager

/**
 * Resolutor real de [TrackSourceResolver] sobre YouTube (`:core`) y el almacén
 * offline (`:desktop`).
 *
 * **El fichero descargado manda** (M16): si el audio del vídeo ya está en
 * [DownloadedAudioStore], se reproduce desde disco, sin red y sin que le afecte
 * la caducidad de las URLs de `googlevideo`. Solo si no está descargado se
 * resuelve la URL de streaming de YouTube, igual que hacía Android en
 * `TrackResolver`.
 *
 * Si la pista no trae id de vídeo (p. ej. llegó de una lista sin resolver), se
 * busca por "título artista". [YouTubeManager] ya deduplica extracciones y
 * cachea URLs, así que saltar a una pista resuelta antes es instantáneo.
 *
 * [resolveVideoId] y [streamingUrl] se inyectan en los tests para no tocar la red.
 */
class YouTubeTrackSourceResolver(
    private val store: DownloadedAudioStore = DownloadedAudioStore(),
    private val resolveVideoId: suspend (String, String) -> String? = { title, artist ->
        YouTubeManager.resolveVideoId(title, artist, null)
    },
    private val streamingUrl: suspend (String, Boolean) -> String? = { videoId, forceRefresh ->
        YouTubeManager.getAudioUrl(videoId, forceRefresh)
    },
) : TrackSourceResolver {

    override suspend fun resolve(
        track: PlayerTrack,
        knownVideoId: String?,
        forceRefresh: Boolean,
    ): ResolvedSource? {
        val videoId = knownVideoId
            ?: track.videoId
            ?: resolveVideoId(track.title, track.artist)
            ?: return null

        store.localUri(videoId)?.let { return ResolvedSource(videoId = videoId, uri = it) }

        val url = streamingUrl(videoId, forceRefresh) ?: return null
        return ResolvedSource(videoId = videoId, uri = url)
    }
}
