package com.plyr.viewmodel

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import com.plyr.database.TrackEntity
import com.plyr.network.YouTubeManager
import com.plyr.utils.DownloadedAudioStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Resuelve las URLs de audio de las pistas y construye los `MediaItem`.
 *
 * Extraído de [PlayerViewModel] porque no depende del estado de la ventana ni de
 * los controles: solo de la red, de la caché de ficheros descargados y del id de
 * vídeo ya resuelto de cada pista.
 */
class TrackResolver(private val application: Application) {

    private companion object {
        /** Extracciones de URL simultáneas al rellenar la ventana. */
        const val RESOLVE_CONCURRENCY = 2
    }

    /**
     * Video de YouTube realmente usado por cada pista, indexado por `track.id`.
     *
     * Para las pistas resueltas por búsqueda, `track.youtubeVideoId` es `null`,
     * así que aquí queda el id que sirvió la URL: `invalidate()` no llegaba a
     * llamarse y el reintento recibía de la caché la misma URL muerta (B4).
     */
    private val resolvedVideoId = ConcurrentHashMap<String, String>()

    /** Id de vídeo conocido de [track]: el de la pista o el resuelto por búsqueda. */
    fun knownVideoId(track: TrackEntity): String? =
        track.youtubeVideoId ?: resolvedVideoId[track.id]

    /** Id de vídeo resuelto para [trackId], si ya se buscó antes. */
    fun cachedVideoId(trackId: String): String? = resolvedVideoId[trackId]

    /** Recuerda el id de vídeo que sirvió la URL de [trackId]. */
    fun remember(trackId: String, videoId: String) {
        resolvedVideoId[trackId] = videoId
    }

    fun clear() = resolvedVideoId.clear()

    /**
     * URI reproducible de un vídeo: el fichero descargado si existe (offline,
     * sin caducidad), o si no la URL de streaming de YouTube ([forceRefresh]
     * salta la caché de URLs tras un 403/410).
     */
    suspend fun resolveSourceUri(videoId: String?, forceRefresh: Boolean = false): Uri? {
        if (videoId == null) return null
        DownloadedAudioStore.localUri(application, videoId)?.let { return it }
        return YouTubeManager.getAudioUrl(videoId, forceRefresh)?.let(Uri::parse)
    }

    /**
     * Resuelve las URLs de [tracks] (que empiezan en la posición [startIndex] de
     * la cola), en paralelo y con poca concurrencia. Solo devuelve items cuyo
     * índice se conoce, y nunca si la cola cambió durante la resolución: un
     * resultado obsoleto no debe aplicarse. Los índices ausentes se ignoran
     * (huecos), no se comprimen.
     *
     * [isCurrent] decide si el resultado sigue siendo válido (p. ej. la
     * `generation` no cambió mientras se resolvía).
     *
     * [forceRefresh] salta la caché de URLs: lo usa el reintento tras una URL
     * caducada, que si no volvería a recibir la misma URL muerta (B4).
     */
    suspend fun resolveItems(
        tracks: List<TrackEntity>,
        startIndex: Int,
        forceRefresh: Boolean = false,
        isCurrent: () -> Boolean,
    ): List<ResolvedItem> {
        if (tracks.isEmpty()) return emptyList()

        val resolved = coroutineScope {
            tracks.chunked(RESOLVE_CONCURRENCY)
                .map { chunk ->
                    async(Dispatchers.IO) {
                        chunk.map { track ->
                            // Si la pista ya se resolvió antes (por búsqueda),
                            // se reutiliza ese video en vez de volver a buscarlo.
                            val knownId = track.youtubeVideoId ?: resolvedVideoId[track.id]
                            val videoId = YouTubeManager.resolveVideoId(
                                track.name,
                                track.artists,
                                knownId
                            )
                            if (videoId != null) resolvedVideoId[track.id] = videoId
                            // Preferir el fichero descargado: sin red y sin que
                            // le afecte la caducidad de la URL de googlevideo.
                            val sourceUri = videoId?.let { id ->
                                DownloadedAudioStore.localUri(application, id)
                                    ?: YouTubeManager.getAudioUrl(id, forceRefresh = forceRefresh)?.let(Uri::parse)
                            }
                            videoId to sourceUri
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

        if (!isCurrent()) return emptyList()

        return resolved.mapIndexedNotNull { i, (_, uri) ->
            if (uri == null) null else ResolvedItem(startIndex + i, createMediaItem(tracks[i], uri, startIndex + i))
        }
    }

    fun createMediaItem(track: TrackEntity, uri: Uri, queueIndex: Int): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId("${track.id}#$queueIndex")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.name)
                    .setArtist(track.artists)
                    .build()
            )
            .build()

    /** Item resuelto junto a su posición en la cola, para no perder la alineación. */
    data class ResolvedItem(val index: Int, val mediaItem: MediaItem)
}
