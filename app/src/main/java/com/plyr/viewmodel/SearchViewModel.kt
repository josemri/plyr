package com.plyr.viewmodel

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plyr.database.SearchHistoryDao
import com.plyr.database.SearchHistoryEntity
import com.plyr.model.AudioItem
import com.plyr.model.ScanResult
import com.plyr.service.YouTubeSearchManager
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado y lógica de la pantalla de búsqueda.
 *
 * Extraído de `SearchScreen.kt` para separar la orquestación (búsqueda en
 * YouTube, historial, procesado de un scan NFC/QR) de la composición. El estado
 * es "Compose state" (`mutableStateOf`) para que la UI lo observe tal cual.
 *
 * Se retiene con `remember` en la pantalla: su ciclo de vida es el de la entrada
 * en el composable, no el de la Activity, que es lo que la pantalla ya asumía.
 */
@Stable
class SearchViewModel(
    initialQuery: String?,
    val context: Context,
    val youtubeSearchManager: YouTubeSearchManager,
    val searchHistoryDao: SearchHistoryDao,
    val coroutineScope: CoroutineScope,
) {
    var searchQuery by mutableStateOf(initialQuery ?: "")
    var results by mutableStateOf<List<AudioItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var youtubeAllResults by mutableStateOf<YouTubeSearchManager.YouTubeSearchAllResult?>(null)
    var showYouTubeAllResults by mutableStateOf(false)
    var selectedYouTubePlaylist by mutableStateOf<YouTubeSearchManager.YouTubePlaylistInfo?>(null)
    var showQrScanner by mutableStateOf(false)

    private val resultActions = SearchResultActions(
        onResults = { results = it },
        onYouTubeAllResults = { youtubeAllResults = it },
        onShowYouTubeAllResults = { showYouTubeAllResults = it },
        onLoadingChange = { isLoading = it },
        onError = { error = it },
    )

    val performSearch: (String, Boolean) -> Unit = { query, isLoadMore ->
        if (query.isNotBlank() && (!isLoading || isLoadMore)) {
            if (isLoadMore) {
                isLoading = true
            } else {
                isLoading = true
                results = emptyList()
                youtubeAllResults = null
                showYouTubeAllResults = false
            }
            error = null

            coroutineScope.launch {
                executeSearch(
                    query = query,
                    isLoadMore = isLoadMore,
                    deps = SearchDependencies(
                        youtubeSearchManager = youtubeSearchManager,
                        searchHistoryDao = searchHistoryDao,
                        errorPrefix = Translations.get(context, "search_error")
                    ),
                    actions = resultActions
                )
            }
        }
    }

    private val scanActions = ScanResultActions(
        onPlaylistSelected = { selectedYouTubePlaylist = it },
        onSearchVideo = { url ->
            searchQuery = url
            performSearch(url, false)
        },
        onError = { error = it },
        onLoadingChange = { isLoading = it },
    )

    suspend fun processScan(result: ScanResult) {
        processScanResult(result, context, youtubeSearchManager, scanActions)
    }
}

private data class SearchResultActions(
    val onResults: (List<AudioItem>) -> Unit,
    val onYouTubeAllResults: (YouTubeSearchManager.YouTubeSearchAllResult?) -> Unit,
    val onShowYouTubeAllResults: (Boolean) -> Unit,
    val onLoadingChange: (Boolean) -> Unit,
    val onError: (String?) -> Unit,
)

private data class ScanResultActions(
    val onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
    val onSearchVideo: (String) -> Unit,
    val onError: (String) -> Unit,
    val onLoadingChange: (Boolean) -> Unit,
)

private data class SearchDependencies(
    val youtubeSearchManager: YouTubeSearchManager,
    val searchHistoryDao: SearchHistoryDao,
    val errorPrefix: String,
)

private suspend fun executeSearch(
    query: String,
    isLoadMore: Boolean,
    deps: SearchDependencies,
    actions: SearchResultActions,
) {
    try {
        if (!isLoadMore) {
            try {
                deps.searchHistoryDao.deleteSearchByQuery(query, "youtube")
                deps.searchHistoryDao.insertSearch(
                    SearchHistoryEntity(
                        query = query,
                        searchEngine = "youtube"
                    )
                )
            } catch (_: Exception) {
            }
        }

        actions.onYouTubeAllResults(null)
        actions.onShowYouTubeAllResults(false)

        val searchResults = deps.youtubeSearchManager.searchYouTubeAll(query)
        actions.onYouTubeAllResults(searchResults)
        actions.onShowYouTubeAllResults(true)

        val newResults = searchResults.videos.map { videoInfo ->
            AudioItem(
                title = videoInfo.title,
                url = "",
                videoId = videoInfo.videoId,
                channel = videoInfo.uploader,
                duration = videoInfo.getFormattedDuration()
            )
        }

        actions.onResults(newResults)
        actions.onLoadingChange(false)

    } catch (e: Exception) {
        actions.onLoadingChange(false)
        actions.onError("${deps.errorPrefix}: ${e.message}")
    }
}

private suspend fun processScanResult(
    result: ScanResult,
    context: Context,
    youtubeSearchManager: YouTubeSearchManager,
    actions: ScanResultActions,
) {
    val prefix = Translations.get(context, "search_error_processing_qr")
    try {
        when (result.source) {
            "youtube" -> processYouTubeScanResult(result, youtubeSearchManager, prefix, actions)
            else -> {
                actions.onError("$prefix: unsupported source '${result.source}'")
                actions.onLoadingChange(false)
            }
        }
    } catch (e: Exception) {
        actions.onError("$prefix: ${e.message}")
        actions.onLoadingChange(false)
    }
}

private suspend fun processYouTubeScanResult(
    result: ScanResult,
    youtubeSearchManager: YouTubeSearchManager,
    prefix: String,
    actions: ScanResultActions,
) {
    if (result.type == "playlist") {
        val info = youtubeSearchManager.getYouTubePlaylistInfo(result.id)
        if (info != null) {
            actions.onPlaylistSelected(info)
        } else {
            actions.onError("$prefix: playlist not available")
            actions.onLoadingChange(false)
        }
    } else {
        val videoUrl = "https://www.youtube.com/watch?v=${result.id}"
        actions.onSearchVideo(videoUrl)
    }
}
