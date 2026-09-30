package com.plyr.service

import com.plyr.database.TrackEntity
import com.plyr.network.AppTrack
import com.plyr.network.YouTubeManager
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resultado de crear una playlist de YouTube a partir de una playlist ya existente en la app.
 * Título y descripción se mantienen idénticos a los de la fuente: es la misma playlist.
 * [discardedTracks] cuenta las canciones que no pudieron resolverse a un vídeo (B15).
 */
data class CreatedYouTubePlaylist(
    val title: String,
    val description: String?,
    val tracks: List<TrackEntity>,
    val discardedTracks: Int = 0
)

/**
 * Crea una playlist de YouTube (local, prefijo "youtube_") a partir de los tracks de
 * una playlist ya existente.
 *
 * La resolución de cada track a su vídeo usa la integración de YouTube ya existente
 * ([YouTubeManager.searchVideoId]); si un track ya tiene `youtubeVideoId`, no se re-busca.
 */
class YouTubePlaylistCreator(
    private val resolveVideoId: suspend (query: String) -> String? = { query ->
        withTimeoutOrNull(RESOLUTION_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                YouTubeManager.searchVideoId(query)
            }
        }
    }
) {

    companion object {
        /**
         * B16: cada búsqueda se corta sola si el extractor se cuelga, en vez de
         * dejar al usuario minutos mirando un spinner.
         */
        private const val RESOLUTION_TIMEOUT_MS = 15_000L
    }

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
                // B28: AppTrack ahora transporta youtubeVideoId (via toAppTrack),
                // así el id sale y vuelve sin perderse por el camino
                youtubeVideoId = track.youtubeVideoId,
                audioUrl = null,
                position = track.position ?: index,
                lastSyncTime = 0L
            )
        }
    }

    /**
     * Construye la playlist de YouTube. Por cada track usa su `youtubeVideoId` si ya lo
     * tiene; si no, el id resuelto en [resolvedVideoIds] (por `remoteTrackId`); y solo
     * si no hay ninguno, lo busca con la integración existente (cada búsqueda con
     * timeout). Es `suspend` para poder cancelarla: si la composición que la lanza
     * desaparece (navegar atrás) deja de resolver en la siguiente pista (B16).
     */
    suspend fun build(
        title: String,
        description: String?,
        sourceTracks: List<TrackEntity>,
        targetPlaylistId: String,
        resolvedVideoIds: Map<String, String> = emptyMap()
    ): CreatedYouTubePlaylist {
        val tracks = mutableListOf<TrackEntity>()
        var discarded = 0
        sourceTracks.forEach { track ->
            coroutineContext.ensureActive()
            val videoId = track.youtubeVideoId
                ?: resolvedVideoIds[track.remoteTrackId]
                ?: resolveVideoId("${track.name} ${track.artists}".trim())
            if (videoId.isNullOrBlank()) {
                discarded++
                return@forEach
            }
            tracks += track.copy(
                id = "${targetPlaylistId}_${videoId}_${tracks.size}",
                playlistId = targetPlaylistId,
                youtubeVideoId = videoId,
                position = tracks.size
            )
        }
        return CreatedYouTubePlaylist(
            title = title,
            description = description,
            tracks = tracks,
            discardedTracks = discarded
        )
    }
}