package com.plyr.service

import com.plyr.database.TrackEntity
import com.plyr.network.AppTrack
import com.plyr.network.YouTubeManager

/**
 * Resultado de crear una playlist de YouTube a partir de una playlist ya existente en la app.
 * Título y descripción se mantienen idénticos a los de la fuente: es la misma playlist.
 */
data class CreatedYouTubePlaylist(
    val title: String,
    val description: String?,
    val tracks: List<TrackEntity>
)

/**
 * Crea una playlist de YouTube (local, prefijo "youtube_") a partir de los tracks de
 * una playlist ya existente.
 *
 * La resolución de cada track a su vídeo usa la integración de YouTube ya existente
 * ([YouTubeManager.searchVideoId]); si un track ya tiene `youtubeVideoId`, no se re-busca.
 */
class YouTubePlaylistCreator(
    private val resolveVideoId: (query: String) -> String? = { query ->
        YouTubeManager.searchVideoId(query)
    }
) {

    /**
     * Construye los tracks fuente desde tracks seleccionados, manteniendo
     * nombre, artistas e id: la playlist de YouTube será la misma.
     */
    fun buildSourceTracks(selectedTracks: List<AppTrack>): List<TrackEntity> {
        return selectedTracks.mapIndexed { index, track ->
            TrackEntity(
                id = "source_${track.id}_$index",
                playlistId = "",
                remoteTrackId = track.id,
                name = track.name,
                artists = track.getArtistNames(),
                youtubeVideoId = null,
                audioUrl = null,
                position = index,
                lastSyncTime = 0L
            )
        }
    }

    /**
     * Construye la playlist de YouTube. Por cada track usa su `youtubeVideoId` si ya lo
     * tiene; si no, el id resuelto en [resolvedVideoIds] (por `remoteTrackId`); y solo
     * si no hay ninguno, lo busca con la integración existente.
     */
    fun build(
        title: String,
        description: String?,
        sourceTracks: List<TrackEntity>,
        targetPlaylistId: String,
        resolvedVideoIds: Map<String, String> = emptyMap()
    ): CreatedYouTubePlaylist {
        val tracks = mutableListOf<TrackEntity>()
        sourceTracks.forEach { track ->
            val videoId = track.youtubeVideoId
                ?: resolvedVideoIds[track.remoteTrackId]
                ?: resolveVideoId("${track.name} ${track.artists}".trim())
            if (videoId.isNullOrBlank()) return@forEach
            tracks += track.copy(
                id = "${targetPlaylistId}_${videoId}_${tracks.size}",
                playlistId = targetPlaylistId,
                youtubeVideoId = videoId,
                position = tracks.size
            )
        }
        return CreatedYouTubePlaylist(title = title, description = description, tracks = tracks)
    }
}
