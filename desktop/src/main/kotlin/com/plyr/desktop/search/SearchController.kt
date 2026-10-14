package com.plyr.desktop.search

import com.plyr.desktop.player.PlayerTrack
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
 * Resultado de una búsqueda: los vídeos y las listas que devuelve YouTube.
 *
 * Antes la búsqueda solo devolvía vídeos (`List<PlayerTrack>`); M19 añade las
 * listas para poder abrirlas y guardarlas. Es el equivalente en escritorio de
 * separar los dos cubos de `SearchViewModel` (Android).
 */
data class SearchResults(
    val videos: List<PlayerTrack> = emptyList(),
    val playlists: List<YouTubePlaylistInfo> = emptyList(),
)

/**
 * Estado de la pantalla de búsqueda.
 *
 * `videosExpanded`/`playlistsExpanded` son el plegado de cada sección (M19):
 * ambos arrancan expandidos y se recuerdan entre búsquedas.
 */
data class SearchState(
    val query: String = "",
    val results: List<PlayerTrack> = emptyList(),
    val playlists: List<YouTubePlaylistInfo> = emptyList(),
    val videosExpanded: Boolean = true,
    val playlistsExpanded: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** `true` si la última búsqueda terminó sin resultados (ni vídeos ni listas). */
    val isEmptyResult: Boolean
        get() = !isLoading && error == null && results.isEmpty() && playlists.isEmpty()

    /** `true` si hay algo que listar: vídeos, listas o ambos. */
    val hasResults: Boolean get() = results.isNotEmpty() || playlists.isNotEmpty()
}

/**
 * Estado y lógica de la búsqueda de YouTube en escritorio.
 *
 * Es el port de `SearchViewModel` (Android) pero sobre `StateFlow` (M7 pidió
 * dejar `LiveData` atrás) y con la búsqueda inyectada como función, para poder
 * testearlo sin red. La búsqueda real la aporta
 * [com.plyr.network.YouTubeManager.searchAll] a través de la UI.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val search: suspend (query: String, maxResults: Int) -> SearchResults,
    private val maxResults: Int = 25,
) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Pliega/despliega la sección de vídeos. */
    fun toggleVideos() {
        _state.update { it.copy(videosExpanded = !it.videosExpanded) }
    }

    /** Pliega/despliega la sección de listas. */
    fun togglePlaylists() {
        _state.update { it.copy(playlistsExpanded = !it.playlistsExpanded) }
    }

    /**
     * Lanza la búsqueda de [query] (o de la consulta actual). Cancela cualquier
     * búsqueda anterior: solo interesa el último resultado.
     */
    fun performSearch(query: String = _state.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        job?.cancel()
        _state.update { it.copy(query = trimmed, isLoading = true, error = null) }

        job = scope.launch {
            try {
                val found = search(trimmed, maxResults)
                _state.update {
                    it.copy(
                        results = found.videos,
                        playlists = found.playlists,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        results = emptyList(),
                        playlists = emptyList(),
                        isLoading = false,
                        error = e.message,
                    )
                }
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
