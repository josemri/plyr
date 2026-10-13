package com.plyr.desktop.playlists

import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.database.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Estado de la pantalla de playlists (M8).
 */
data class PlaylistsState(
    val playlists: List<PlaylistRecord> = emptyList(),
    val selectedPlaylistId: String? = null,
    val tracks: List<TrackRecord> = emptyList(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showCreate: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showExitEditDialog: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val newTitle: String = "",
    val newDescription: String = "",
    val originalTitle: String = "",
    val originalDescription: String = "",
) {
    val selectedPlaylist: PlaylistRecord?
        get() = selectedPlaylistId?.let { id -> playlists.find { it.remoteId == id } }
}

/**
 * Controlador para la gestión de playlists en escritorio.
 *
 * Envolvente ligero sobre [PlaylistRepository] que expone un [StateFlow] de
 * estado, similar a [com.plyr.desktop.search.SearchController] y
 * [com.plyr.desktop.player.PlayerController].
 */
class PlaylistsController(
    private val scope: CoroutineScope,
    private val repository: PlaylistRepository,
) {
    private val _state = MutableStateFlow(PlaylistsState())
    val state: StateFlow<PlaylistsState> = _state.asStateFlow()

    init {
        scope.launch {
            repository.observePlaylists().collectLatest { list ->
                _state.update {
                    val visible = com.plyr.database.PlaylistFilters.visiblePlaylists(list, com.plyr.database.LikedSongs.ID)
                    it.copy(playlists = visible)
                }
            }
        }
        // Ensure liked songs exists
        repository.ensureLikedSongsPlaylist()
    }

    fun selectPlaylist(playlistId: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null, selectedPlaylistId = playlistId) }
            val tracks = repository.tracksByPlaylist(playlistId)
            val playlist = repository.playlistById(playlistId)
            _state.update {
                it.copy(
                    isLoading = false,
                    tracks = tracks.sortedBy { track -> track.position },
                    newTitle = playlist?.name ?: "",
                    newDescription = playlist?.description ?: "",
                    originalTitle = playlist?.name ?: "",
                    originalDescription = playlist?.description ?: "",
                    isEditing = false,
                    hasUnsavedChanges = false,
                )
            }
        }
    }

    fun deselectPlaylist() {
        _state.update {
            it.copy(
                selectedPlaylistId = null,
                tracks = emptyList(),
                isEditing = false,
                showCreate = false,
                hasUnsavedChanges = false,
                showDeleteDialog = false,
                showExitEditDialog = false,
            )
        }
    }

    fun showCreate() {
        _state.update {
            it.copy(
                showCreate = true,
                newTitle = "",
                newDescription = "",
                hasUnsavedChanges = false,
            )
        }
    }

    fun hideCreate() {
        _state.update { it.copy(showCreate = false) }
    }

    /**
     * Da de alta una lista local vacía y la abre. Es el alta que faltaba: el
     * formulario solo cerraba el diálogo, así que no se guardaba nada.
     *
     * @return el `remoteId` de la lista creada, o `null` si el nombre está vacío.
     */
    fun createPlaylist(title: String, description: String?): String? {
        val name = title.trim()
        if (name.isEmpty()) return null
        val id = "local_${UUID.randomUUID()}"
        val record = PlaylistRecord(
            remoteId = id,
            name = name,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            trackCount = 0,
            imageUrl = null,
            lastSyncTime = System.currentTimeMillis(),
            source = PlaylistSource.LOCAL,
            sourceId = null,
        )
        if (!repository.savePlaylist(record, emptyList())) return null
        _state.update { it.copy(showCreate = false) }
        selectPlaylist(id)
        return id
    }

    /** Portada personalizada (M8): guarda la ruta del recorte elegido por el usuario. */
    fun updateCover(imageUrl: String): Boolean {
        val id = state.value.selectedPlaylistId ?: return false
        return repository.updatePlaylistImage(id, imageUrl)
    }

    /**
     * Añade [track] al final de [playlistId] (acción contextual de la búsqueda,
     * equivalente al "añadir a lista" de Android). Refresca la lista abierta si
     * es la destino.
     */
    fun addTrackToPlaylist(playlistId: String, track: TrackRecord) {
        repository.addTrack(playlistId, track)
        if (state.value.selectedPlaylistId == playlistId) refresh()
    }

    fun enterEditMode() {
        val p = state.value.selectedPlaylist
        _state.update {
            it.copy(
                isEditing = true,
                newTitle = p?.name ?: "",
                newDescription = p?.description ?: "",
                originalTitle = p?.name ?: "",
                originalDescription = p?.description ?: "",
                hasUnsavedChanges = false,
            )
        }
    }

    fun cancelEdit() {
        if (state.value.hasUnsavedChanges) {
            _state.update { it.copy(showExitEditDialog = true) }
        } else {
            exitEdit()
        }
    }

    fun exitEdit() {
        _state.update {
            it.copy(
                isEditing = false,
                showExitEditDialog = false,
                hasUnsavedChanges = false,
                newTitle = it.originalTitle,
                newDescription = it.originalDescription,
            )
        }
    }

    fun confirmExitEdit() {
        exitEdit()
    }

    fun updateTitle(title: String) {
        _state.update {
            it.copy(
                newTitle = title,
                hasUnsavedChanges = title != it.originalTitle || it.newDescription != it.originalDescription,
            )
        }
    }

    fun updateDescription(desc: String) {
        _state.update {
            it.copy(
                newDescription = desc,
                hasUnsavedChanges = it.newTitle != it.originalTitle || desc != it.originalDescription,
            )
        }
    }

    fun saveEdits() {
        val id = state.value.selectedPlaylistId ?: return
        val title = state.value.newTitle.trim()
        val desc = state.value.newDescription.trim()
        if (title.isEmpty()) return
        repository.updatePlaylistDetails(id, title, if (desc.isEmpty()) null else desc)
        scope.launch {
            val tracks = repository.tracksByPlaylist(id)
            _state.update {
                it.copy(
                    isEditing = false,
                    hasUnsavedChanges = false,
                    originalTitle = title,
                    originalDescription = desc,
                    tracks = tracks.sortedBy { t -> t.position },
                )
            }
        }
    }

    fun reorderTracks(from: Int, to: Int) {
        val tracks = state.value.tracks
        if (from !in tracks.indices || to !in tracks.indices || from == to) return
        val reordered = tracks.toMutableList().apply { add(to, removeAt(from)) }
        // Renumber positions for display
        // We don't have mutable position in TrackRecord - but we sort by original index in view
        // Instead, just send ordered ids - repository handles it
        _state.update { it.copy(tracks = reordered) }
        val id = state.value.selectedPlaylistId ?: return
        val orderedIds = reordered.map { it.remoteTrackId }
        repository.reorderTracks(id, orderedIds)
    }

    fun removeTrack(remoteTrackId: String) {
        val id = state.value.selectedPlaylistId ?: return
        repository.removeTrack(id, remoteTrackId)
        scope.launch {
            val tracks = repository.tracksByPlaylist(id)
            _state.update { it.copy(tracks = tracks.sortedBy { t -> t.position }) }
        }
    }

    fun addTrack(track: TrackRecord) {
        val id = state.value.selectedPlaylistId ?: return
        repository.addTrack(id, track)
        scope.launch {
            val tracks = repository.tracksByPlaylist(id)
            _state.update { it.copy(tracks = tracks.sortedBy { t -> t.position }) }
        }
    }

    fun showDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        val id = state.value.selectedPlaylistId ?: return
        repository.deletePlaylist(id)
        _state.update {
            it.copy(
                showDeleteDialog = false,
                selectedPlaylistId = null,
                tracks = emptyList(),
            )
        }
        onDeleted()
    }

    fun refresh() {
        val id = state.value.selectedPlaylistId
        if (id != null) {
            scope.launch {
                val tracks = repository.tracksByPlaylist(id)
                _state.update { it.copy(tracks = tracks.sortedBy { t -> t.position }) }
            }
        }
    }
}
