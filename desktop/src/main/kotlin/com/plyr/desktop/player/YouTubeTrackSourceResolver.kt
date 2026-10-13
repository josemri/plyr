package com.plyr.desktop.player

import com.plyr.network.YouTubeManager

/**
 * Resolutor real de [TrackSourceResolver] sobre YouTube (`:core`).
 *
 * El escritorio aún no descarga audio (eso es M10), así que la URI es siempre la
 * URL de streaming de YouTube. Si la pista no trae id de vídeo (p. ej. llegó de
 * una lista sin resolver), se busca por "título artista", igual que en Android.
 *
 * [YouTubeManager] ya deduplica extracciones y cachea URLs, así que saltar a una
 * pista resuelta antes es instantáneo.
 */
class YouTubeTrackSourceResolver : TrackSourceResolver {

    override suspend fun resolve(
        track: PlayerTrack,
        knownVideoId: String?,
        forceRefresh: Boolean,
    ): ResolvedSource? {
        val videoId = knownVideoId
            ?: track.videoId
            ?: YouTubeManager.resolveVideoId(track.title, track.artist, null)
            ?: return null

        val url = YouTubeManager.getAudioUrl(videoId, forceRefresh) ?: return null
        return ResolvedSource(videoId = videoId, uri = url)
    }
}
