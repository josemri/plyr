package com.plyr.desktop.playlists

import com.plyr.desktop.player.PlayerTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del buscador de pistas dentro de una lista (M22).
 *
 * `results` incluye tanto las pistas encontradas en YouTube como las que ya se
 * han elegido ([selected]); así el mismo buscador sirve para el alta (elegir y
 * guardar) y para el modo edición (añadir al vuelo).
 */
data class TrackSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<PlayerTrack> = emptyList(),
    val selected: List<PlayerTrack> = emptyList(),
    val error: String? = null,
) {
    /** ¿Está [track] entre las elegidas? (sin duplicar por `videoId`). */
    fun isSelected(track: PlayerTrack): Boolean = selected.any { samePlayerTrack(it, track) }
}

/** ¿Son la misma canción? Por `videoId`; si falta en alguna, por `id`. */
internal fun samePlayerTrack(a: PlayerTrack, b: PlayerTrack): Boolean =
    if (a.videoId.isNullOrBlank() || b.videoId.isNullOrBlank()) a.id == b.id else a.videoId == b.videoId

/**
 * Buscador de canciones de YouTube reutilizable (M22).
 *
 * Es el port ligero de `CreatePlaylistViewModel` (Android): la búsqueda se
 * inyecta como función para poder testearlo sin red, y [selected] acumula las
 * pistas elegidas sin duplicar (mismo `videoId`). Lo usan tanto el formulario de
 * alta como el modo edición del detalle.
 */
class TrackSearchController(
    private val scope: CoroutineScope,
    private val searchTracks: suspend (query: String) -> List<PlayerTrack>,
) {

    private val _state = MutableStateFlow(TrackSearchState())
    val state: StateFlow<TrackSearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Busca la consulta actual y reemplaza los resultados. */
    fun performSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        job?.cancel()
        _state.update { it.copy(isSearching = true, error = null) }

        job = scope.launch {
            try {
                val found = searchTracks(query)
                _state.update { it.copy(isSearching = false, results = found, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSearching = false, results = emptyList(), error = e.message) }
            }
        }
    }

    /** Marca/desmarca [track] entre las elegidas (sin duplicar). */
    fun toggleSelected(track: PlayerTrack) {
        _state.update {
            val current = it.selected
            val existing = current.any { selected -> samePlayerTrack(selected, track) }
            it.copy(selected = if (existing) current.filterNot { selected -> samePlayerTrack(selected, track) } else current + track)
        }
    }

    fun isSelected(track: PlayerTrack): Boolean = _state.value.isSelected(track)

    /** Vacía consulta, resultados y error; conserva las elegidas. */
    fun clear() {
        job?.cancel()
        job = null
        _state.update { it.copy(query = "", isSearching = false, results = emptyList(), error = null) }
    }
}
