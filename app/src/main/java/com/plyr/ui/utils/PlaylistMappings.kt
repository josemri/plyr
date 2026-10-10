package com.plyr.ui.utils

import com.plyr.database.TrackEntity
import com.plyr.network.AppTrack

const val YOUTUBE_PLAYLIST_DESCRIPTION_PREFIX = "YouTube Playlist by "
const val YOUTUBE_PLAYLIST_ID_PREFIX = "youtube_"
const val YOUTUBE_VIDEO_ID_LENGTH = 11

/** Nombre de canal extraído de la descripción generada al importar una playlist de YouTube. */
fun youtubeAuthorFromDescription(description: String?): String? {
    if (description == null || !description.startsWith(YOUTUBE_PLAYLIST_DESCRIPTION_PREFIX)) return null
    return description.removePrefix(YOUTUBE_PLAYLIST_DESCRIPTION_PREFIX).takeIf { it.isNotBlank() }
}

/** Una playlist es remota de YouTube si su id usa el prefijo reservado. */
fun isYouTubePlaylistId(id: String?): Boolean =
    id?.startsWith(YOUTUBE_PLAYLIST_ID_PREFIX) == true

/** Id local de una playlist remota (sin el prefijo de YouTube). */
fun stripYouTubePlaylistId(id: String): String =
    id.removePrefix(YOUTUBE_PLAYLIST_ID_PREFIX)

/** Un id de vídeo de YouTube válido tiene exactamente 11 caracteres. */
fun isYouTubeVideoId(id: String): Boolean = id.length == YOUTUBE_VIDEO_ID_LENGTH

/**
 * Construye las entidades de pista de los resultados de búsqueda de YouTube que
 * alimentan a [com.plyr.ui.components.SongListItem]. [timestamp] se inyecta para
 * mantener la función pura y testeable.
 */
fun buildSearchTrackEntities(
    tracks: List<AppTrack>,
    idPrefix: String,
    timestamp: Long,
    maxTracks: Int = 10,
): List<TrackEntity> = tracks.take(maxTracks).mapIndexed { index, track ->
    TrackEntity(
        id = "${idPrefix}_${track.id}_$index",
        playlistId = "${idPrefix}_$timestamp",
        remoteTrackId = track.id,
        name = track.name,
        artists = track.getArtistNames(),
        youtubeVideoId = null,
        audioUrl = null,
        position = index,
        lastSyncTime = timestamp,
    )
}
