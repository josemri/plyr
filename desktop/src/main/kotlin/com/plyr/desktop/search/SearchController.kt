package com.plyr.desktop.search

import com.plyr.desktop.player.PlayerTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la pantalla de búsqueda. */
data class SearchState(
    val query: String = "",
    val results: List<PlayerTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** `true` si la última búsqueda terminó sin resultados. */
    val isEmptyResult: Boolean get() = !isLoading && error == null && results.isEmpty()
}

/**
 * Estado y lógica de la búsqueda de YouTube en escritorio.
 *
 * Es el port de `SearchViewModel` (Android) pero sobre `StateFlow` (M7 pidió
 * dejar `LiveData` atrás) y con la búsqueda inyectada como función, para poder
 * testearlo sin red. La búsqueda real la aporta
 * [com.plyr.network.YouTubeManager.searchVideos] a través de la UI.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val search: suspend (query: String, maxResults: Int) -> List<PlayerTrack>,
    private val maxResults: Int = 25,
) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
    }

    /**
     * Lanza la búsqueda de [query] (o de la consulta actual). Cancela cualquier
     * búsqueda anterior: solo interesa el último resultado.
     */
    fun performSearch(query: String = _state.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        job?.cancel()
        _state.value = _state.value.copy(query = trimmed, isLoading = true, error = null)

        job = scope.launch {
            try {
                val results = search(trimmed, maxResults)
                _state.value = _state.value.copy(results = results, isLoading = false, error = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(results = emptyList(), isLoading = false, error = e.message)
            }
        }
    }

    /** Vacía consulta, resultados y error. */
    fun clear() {
        job?.cancel()
        job = null
        _state.value = SearchState()
    }
}
