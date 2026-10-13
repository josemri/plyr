package com.plyr.desktop.player

import com.plyr.database.TrackRecord
import com.plyr.model.AudioItem

/**
 * Una pista reproducible en el escritorio.
 *
 * Es el equivalente neutral de `TrackEntity` (Android) para la cola de
 * reproducción: solo lleva lo que el motor y la UI necesitan (nombre, artista y
 * el id de vídeo ya conocido, si lo hay). Se construye desde los resultados de
 * búsqueda ([AudioItem]) o, en M8, desde las pistas de una lista
 * ([com.plyr.database.TrackRecord]).
 *
 * [id] es único dentro de la cola: identifica la entrada para las operaciones
 * del motor (mover, saltar) aunque dos canciones compartan título.
 */
data class PlayerTrack(
    val id: String,
    val title: String,
    val artist: String,
    val videoId: String? = null,
    val duration: String = "",
) {
    companion object {
        /** Construye una pista de cola a partir de un resultado de búsqueda. */
        fun fromSearchResult(index: Int, item: AudioItem): PlayerTrack {
            val videoId = item.videoId.takeIf { it.isNotBlank() }
            return PlayerTrack(
                id = "search_${videoId ?: index}#$index",
                title = item.title,
                artist = item.channel,
                videoId = videoId,
                duration = item.duration,
            )
        }

        fun fromTrackRecord(index: Int, track: com.plyr.database.TrackRecord): PlayerTrack {
            return PlayerTrack(
                id = track.remoteTrackId.takeIf { it.isNotBlank() } ?: "track_${index}",
                title = track.name,
                artist = track.artists,
                videoId = track.youtubeVideoId,
            )
        }
    }

    /**
     * Prepara esta pista como pista de una lista (M8, "añadir a lista").
     * `id`, `position` y `playlistId` los normaliza el repositorio al
     * insertarla; aquí solo importan la identidad y los metadatos.
     */
    fun toTrackRecord(playlistId: String, now: Long = System.currentTimeMillis()): TrackRecord =
        TrackRecord(
            id = "",
            playlistId = playlistId,
            remoteTrackId = videoId ?: id,
            name = title,
            artists = artist,
            youtubeVideoId = videoId,
            audioUrl = null,
            position = 0,
            lastSyncTime = now,
        )
}
