package com.plyr.desktop.search

import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.model.AudioItem
import com.plyr.network.YouTubePlaylistInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del detalle de una lista de YouTube (M19).
 *
 * [isSaved] refleja si la lista ya está guardada como lista local (mismo criterio
 * que Android: su `remoteId` es `youtube_<playlistId>`).
 */
data class YouTubePlaylistDetailState(
    val playlistId: String = "",
    val title: String = "",
    val uploader: String = "",
    val thumbnailUrl: String? = null,
    val tracks: List<TrackRecord> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
) {
    /** `true` si ya terminó de cargar y no hay ni error ni pistas. */
    val isEmpty: Boolean get() = !isLoading && error == null && tracks.isEmpty()
}

/**
 * Controlador del detalle de una lista de YouTube buscada (M19).
 *
 * Carga sus pistas con [loadVideos] (inyectado, para testear sin red) y permite
 * guardarla como lista local o quitarla. Es el port de `YouTubePlaylistDetailView`
 * de Android, pero separando estado ([StateFlow]) y vista, como el resto de
 * controladores de escritorio.
 */
class YouTubePlaylistDetailController(
    private val scope: CoroutineScope,
    private val repository: PlaylistRepository,
    private val loadVideos: suspend (playlistId: String) -> List<AudioItem>,
) {
    private val _state = MutableStateFlow(YouTubePlaylistDetailState())
    val state: StateFlow<YouTubePlaylistDetailState> = _state.asStateFlow()

    private var job: Job? = null
    private var playlist: YouTubePlaylistInfo? = null

    /** Abre [playlist]: publica la ficha al instante y carga sus pistas. */
    fun open(playlist: YouTubePlaylistInfo) {
        job?.cancel()
        this.playlist = playlist
        val localId = localPlaylistId(playlist.playlistId)
        _state.value = YouTubePlaylistDetailState(
            playlistId = playlist.playlistId,
            title = playlist.title,
            uploader = playlist.uploader,
            thumbnailUrl = playlist.thumbnailUrl,
            isLoading = true,
            isSaved = repository.playlistById(localId) != null,
        )
        job = scope.launch {
            try {
                val tracks = loadVideos(playlist.playlistId)
                    .mapIndexed { index, item -> item.toTrackRecord(localId, index) }
                _state.update { it.copy(tracks = tracks, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(tracks = emptyList(), isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Guarda la lista abierta como lista local, o la borra si ya lo estaba
     * (botón conmutable, como el `<save>`/`<saved>` de Android).
     */
    fun toggleSave() {
        val info = playlist ?: return
        val localId = localPlaylistId(info.playlistId)
        if (repository.playlistById(localId) != null) {
            repository.deletePlaylist(localId)
            _state.update { it.copy(isSaved = false) }
            return
        }
        val tracks = _state.value.tracks
        val record = PlaylistRecord(
            remoteId = localId,
            name = info.title,
            description = "YouTube playlist by ${info.uploader}",
            trackCount = tracks.size,
            imageUrl = info.thumbnailUrl,
            lastSyncTime = System.currentTimeMillis(),
            source = PlaylistSource.YOUTUBE,
            sourceId = info.playlistId,
        )
        val saved = repository.savePlaylist(record, tracks)
        _state.update { it.copy(isSaved = saved) }
    }

    /** Cierra el detalle y olvida la lista abierta. */
    fun close() {
        job?.cancel()
        job = null
        playlist = null
        _state.value = YouTubePlaylistDetailState()
    }

    companion object {
        /**
         * `remoteId` local de una lista de YouTube. Es el mismo prefijo que usa
         * Android al guardarla (`saveYouTubePlaylist`), para que las listas
         * guardadas en un lado se reconozcan en el otro.
         */
        fun localPlaylistId(playlistId: String): String = "youtube_$playlistId"
    }
}

/** Convierte un resultado de vídeo de la lista en una pista local guardable. */
private fun AudioItem.toTrackRecord(playlistId: String, index: Int): TrackRecord {
    val video = videoId.takeIf { it.isNotBlank() }
    val remote = video ?: "track_$index"
    return TrackRecord(
        id = "${playlistId}_$remote",
        playlistId = playlistId,
        remoteTrackId = remote,
        name = title,
        artists = channel,
        youtubeVideoId = video,
        audioUrl = null,
        position = index,
        lastSyncTime = System.currentTimeMillis(),
    )
}
