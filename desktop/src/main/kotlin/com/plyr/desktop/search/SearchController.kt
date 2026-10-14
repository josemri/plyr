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
    /** `true` si YouTube tiene otra página detrás de esta (M21). */
    val hasMore: Boolean = false,
)

/**
 * Estado de la pantalla de búsqueda.
 *
 * `videosExpanded`/`playlistsExpanded` son el plegado de cada sección (M19):
 * ambos arrancan expandidos y se recuerdan entre búsquedas. `isLoading` es la
 * búsqueda inicial (sustituye la lista) y `isLoadingMore` la carga de la página
 * siguiente (M21, mantiene la lista visible); `hasMore` lo marca YouTube y
 * `canLoadMore` decide si se ofrece «cargar más».
 */
data class SearchState(
    val query: String = "",
    val results: List<PlayerTrack> = emptyList(),
    val playlists: List<YouTubePlaylistInfo> = emptyList(),
    val videosExpanded: Boolean = true,
    val playlistsExpanded: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
) {
    /** `true` si la última búsqueda terminó sin resultados (ni vídeos ni listas). */
    val isEmptyResult: Boolean
        get() = !isLoading && error == null && results.isEmpty() && playlists.isEmpty()

    /** `true` si hay algo que listar: vídeos, listas o ambos. */
    val hasResults: Boolean get() = results.isNotEmpty() || playlists.isNotEmpty()

    /**
     * Si se ofrece «cargar más»: como Android, solo con al menos [MORE_THRESHOLD]
     * vídeos, y además con una página más detrás ([hasMore]). A diferencia de
     * Android —que solo mira el tamaño— aquí también se exige esa página para no
     * enseñar un botón que no cargaría nada.
     */
    val canLoadMore: Boolean
        get() = hasMore && results.size >= MORE_THRESHOLD && !isLoading && !isLoadingMore

    companion object {
        /** Umbral de Android para mostrar «cargar más». */
        const val MORE_THRESHOLD = 10
    }
}

/**
 * Estado y lógica de la búsqueda de YouTube en escritorio.
 *
 * Es el port de `SearchViewModel` (Android) pero sobre `StateFlow` (M7 pidió
 * dejar `LiveData` atrás) y con la búsqueda inyectada como función, para poder
 * testearlo sin red. La búsqueda real la aporta
 * [com.plyr.network.YouTubeManager.searchAll] a través de la UI, que desde M21
 * recibe también la página a pedir.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val search: suspend (query: String, page: Int) -> SearchResults,
) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null

    /** Última página cargada de la búsqueda en curso (1 = la primera). */
    private var page = 1

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
     * búsqueda anterior —solo interesa el último resultado— y vuelve a la página 1.
     */
    fun performSearch(query: String = _state.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        job?.cancel()
        page = 1
        _state.update { it.copy(query = trimmed, isLoading = true, isLoadingMore = false, error = null) }

        job = scope.launch {
            try {
                val found = search(trimmed, 1)
                _state.update {
                    it.copy(
                        results = found.videos,
                        playlists = found.playlists,
                        hasMore = found.hasMore,
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
                        hasMore = false,
                        isLoading = false,
                        error = e.message,
                    )
                }
            }
        }
    }

    /**
     * Pide la página siguiente y la **añade** a lo ya mostrado (M21).
     *
     * No hace nada si no procede cargar más (pocos resultados, ya cargando o sin
     * más páginas). A diferencia de [performSearch], no vacía la lista: los
     * resultados nuevos se acumulan al final, que es lo que espera «cargar más».
     */
    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return

        val nextPage = page + 1
        _state.update { it.copy(isLoadingMore = true, error = null) }

        job = scope.launch {
            try {
                val found = search(current.query, nextPage)
                page = nextPage
                _state.update {
                    it.copy(
                        results = it.results + found.videos,
                        playlists = it.playlists + found.playlists,
                        hasMore = found.hasMore,
                        isLoadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingMore = false, error = e.message) }
            }
        }
    }

    /** Vacía consulta, resultados y error. */
    fun clear() {
        job?.cancel()
        job = null
        page = 1
        _state.value = SearchState()
    }
}
