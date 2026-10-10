package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.plyr.model.AudioItem
import com.plyr.model.ScanResult
import com.plyr.utils.Translations
import com.plyr.utils.NfcScanEvent
import com.plyr.database.TrackEntity
import com.plyr.database.SearchHistoryEntity
import com.plyr.database.SearchHistoryDao
import com.plyr.database.PlaylistDatabase
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import com.plyr.ui.components.search.YouTubePlaylistDetailView
import com.plyr.ui.components.search.PLAYLIST_COVER_CONCURRENCY
import com.plyr.ui.components.search.youtubeSearchResultsSection
import com.plyr.ui.components.search.YouTubeSearchResultsState
import com.plyr.ui.components.search.YouTubeSearchSectionDeps
import com.plyr.ui.components.QrScannerDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Semaphore
import com.plyr.ui.components.Titulo

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

@Stable
private class SearchScreenState(
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

@Composable
fun SearchScreen(
    context: Context,
    initialQuery: String? = null,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    isActive: Boolean = true
) {
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val database = remember { PlaylistDatabase.getDatabase(context) }
    val state = remember {
        SearchScreenState(
            initialQuery = initialQuery,
            context = context,
            youtubeSearchManager = youtubeSearchManager,
            searchHistoryDao = database.searchHistoryDao(),
            coroutineScope = coroutineScope
        )
    }

    val nfcScanResult by NfcScanEvent.scanResult.collectAsState()

    LaunchedEffect(Unit) {
        val query = initialQuery
        if (!query.isNullOrBlank()) {
            state.performSearch(query, false)
        }
    }

    LaunchedEffect(nfcScanResult) {
        val result = nfcScanResult ?: return@LaunchedEffect
        NfcScanEvent.consumeResult()
        state.processScan(result)
    }

    SearchBackHandler(
        showQrScanner = state.showQrScanner,
        hasPlaylist = state.selectedYouTubePlaylist != null,
        onCloseQrScanner = { state.showQrScanner = false },
        onClosePlaylist = { state.selectedYouTubePlaylist = null },
        onBack = onBack
    )

    SearchScreenContent(
        context = context,
        state = state,
        playerViewModel = playerViewModel,
        coroutineScope = coroutineScope,
        onSearchTriggered = state.performSearch,
        onQrScanned = { qrResult -> coroutineScope.launch { state.processScan(qrResult) } },
        isActive = isActive
    )
}

@Composable
private fun SearchBackHandler(
    showQrScanner: Boolean,
    hasPlaylist: Boolean,
    onCloseQrScanner: () -> Unit,
    onClosePlaylist: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler {
        when {
            showQrScanner -> onCloseQrScanner()
            hasPlaylist -> onClosePlaylist()
            else -> onBack()
        }
    }
}

@Composable
private fun SearchScreenContent(
    context: Context,
    state: SearchScreenState,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    onSearchTriggered: (String, Boolean) -> Unit,
    onQrScanned: (ScanResult) -> Unit,
    isActive: Boolean
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val ytPlaylist = state.selectedYouTubePlaylist
        if (ytPlaylist != null) {
            YouTubePlaylistDetailView(
                playlist = ytPlaylist,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        } else {
            SearchMainView(
                context = context,
                searchQuery = state.searchQuery,
                onSearchQueryChange = { state.searchQuery = it },
                results = state.results,
                isLoading = state.isLoading,
                error = state.error,
                onSearchTriggered = onSearchTriggered,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope,
                youtubeAllResults = state.youtubeAllResults,
                showYouTubeAllResults = state.showYouTubeAllResults,
                onYouTubePlaylistSelected = { state.selectedYouTubePlaylist = it },
                onShowQrScannerChange = { state.showQrScanner = it },
                isActive = isActive
            )
            if (state.showQrScanner) {
                QrScannerDialog(
                    onDismiss = { state.showQrScanner = false },
                    onQrScanned = { qrResult ->
                        state.showQrScanner = false
                        if (qrResult != null) {
                            onQrScanned(qrResult)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchMainView(
    context: Context,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    results: List<AudioItem>,
    isLoading: Boolean,
    error: String?,
    onSearchTriggered: (String, Boolean) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    youtubeAllResults: YouTubeSearchManager.YouTubeSearchAllResult?,
    showYouTubeAllResults: Boolean,
    onYouTubePlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
    onShowQrScannerChange: (Boolean) -> Unit,
    isActive: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val currentTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }
    val youtubeManager = remember { YouTubeSearchManager(context) }
    val coverCache = remember { mutableStateMapOf<String, String>() }
    val coverSemaphore = remember { Semaphore(PLAYLIST_COVER_CONCURRENCY) }

    // Expansión de las secciones (hoisteada: los items viven en un LazyListScope).
    val expansion = remember { SearchExpansionState() }

    val trackData = rememberSearchTrackData(youtubeAllResults, results)
    val deps = SearchResultsDeps(
        playerViewModel = playerViewModel,
        youtubeManager = youtubeManager,
        coverCache = coverCache,
        coverSemaphore = coverSemaphore,
        coroutineScope = coroutineScope,
        currentTrack = currentTrack,
        videoTrackEntities = trackData.videoTrackEntities,
        legacyTrackEntities = trackData.legacyTrackEntities,
        youtubeLabel = Translations.get(context, "search_youtube_results"),
        loadMoreLabel = Translations.get(context, "search_load_more"),
        onPlaylistSelected = onYouTubePlaylistSelected
    )

    SearchMainList(
        context = context,
        searchQuery = searchQuery,
        isLoading = isLoading,
        error = error,
        showYouTubeAllResults = showYouTubeAllResults,
        youtubeAllResults = youtubeAllResults,
        results = results,
        deps = deps,
        expansion = expansion,
        focusRequester = focusRequester,
        onSearchQueryChange = onSearchQueryChange,
        onSearchTriggered = onSearchTriggered,
        onShowQrScannerChange = onShowQrScannerChange
    )
}

@Composable
private fun SearchMainList(
    context: Context,
    searchQuery: String,
    isLoading: Boolean,
    error: String?,
    showYouTubeAllResults: Boolean,
    youtubeAllResults: YouTubeSearchManager.YouTubeSearchAllResult?,
    results: List<AudioItem>,
    deps: SearchResultsDeps,
    expansion: SearchExpansionState,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSearchTriggered: (String, Boolean) -> Unit,
    onShowQrScannerChange: (Boolean) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "search_title") {
            Titulo(Translations.get(context, "search_title"))
        }

        item(key = "search_field") {
            SearchInputField(
                context = context,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onSearchTriggered = onSearchTriggered,
                onShowQrScannerChange = onShowQrScannerChange,
                isLoading = isLoading,
                focusRequester = focusRequester
            )
        }

        item(key = "search_spacer") { Spacer(Modifier.height(12.dp)) }

        if (isLoading) {
            item(key = "search_loading") { SearchLoadingIndicator(context) }
        }

        error?.let { err ->
            item(key = "search_error") { SearchErrorMessage(context, err) }
        }

        if (showYouTubeAllResults && youtubeAllResults != null) {
            youtubeResultsSection(deps, expansion, youtubeAllResults)
        }

        if (results.isNotEmpty() && !showYouTubeAllResults) {
            legacyResultsSection(deps, expansion, results, searchQuery, onSearchTriggered)
        }
    }
}

@Stable
private class SearchExpansionState {
    var videosExpanded by mutableStateOf(true)
    var playlistsExpanded by mutableStateOf(false)
    var legacyVideosExpanded by mutableStateOf(true)
}

private data class SearchResultsDeps(
    val playerViewModel: PlayerViewModel?,
    val youtubeManager: YouTubeSearchManager,
    val coverCache: MutableMap<String, String>,
    val coverSemaphore: Semaphore,
    val coroutineScope: CoroutineScope,
    val currentTrack: TrackEntity?,
    val videoTrackEntities: List<TrackEntity>,
    val legacyTrackEntities: List<TrackEntity>,
    val youtubeLabel: String,
    val loadMoreLabel: String,
    val onPlaylistSelected: (YouTubeSearchManager.YouTubePlaylistInfo) -> Unit,
)

private data class SearchTrackData(
    val videoTrackEntities: List<TrackEntity>,
    val legacyTrackEntities: List<TrackEntity>,
)

@Composable
private fun rememberSearchTrackData(
    youtubeAllResults: YouTubeSearchManager.YouTubeSearchAllResult?,
    results: List<AudioItem>
): SearchTrackData {
    val allVideos = youtubeAllResults?.videos ?: emptyList()
    val videoPlaylistId = remember(allVideos) { "youtube_search_${System.currentTimeMillis()}" }
    val videoTrackEntities = remember(allVideos, videoPlaylistId) {
        allVideos.mapIndexed { index, video ->
            TrackEntity(
                id = "yt_${video.videoId}",
                playlistId = videoPlaylistId,
                remoteTrackId = "yt_${video.videoId}", // placeholder obligatorio
                name = video.title,
                artists = video.uploader,
                youtubeVideoId = video.videoId,
                audioUrl = null,
                position = index,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }
    val legacyTrackEntities = remember(results) {
        results.mapIndexed { trackIndex, item ->
            TrackEntity(
                id = "youtube_${item.videoId}",
                playlistId = "youtube_search",
                remoteTrackId = item.videoId,
                name = item.title,
                artists = item.channel,
                youtubeVideoId = item.videoId,
                audioUrl = null,
                position = trackIndex,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }
    return remember(videoTrackEntities, legacyTrackEntities) {
        SearchTrackData(videoTrackEntities, legacyTrackEntities)
    }
}

private fun LazyListScope.youtubeResultsSection(
    deps: SearchResultsDeps,
    expansion: SearchExpansionState,
    allResults: YouTubeSearchManager.YouTubeSearchAllResult
) {
    youtubeSearchResultsSection(
        YouTubeSearchResultsState(
            deps = YouTubeSearchSectionDeps(
                youtubeManager = deps.youtubeManager,
                coverCache = deps.coverCache,
                coverSemaphore = deps.coverSemaphore,
                playerViewModel = deps.playerViewModel,
                coroutineScope = deps.coroutineScope,
            ),
            allResults = allResults,
            videosExpanded = expansion.videosExpanded,
            onToggleVideos = { expansion.videosExpanded = !expansion.videosExpanded },
            playlistsExpanded = expansion.playlistsExpanded,
            onTogglePlaylists = { expansion.playlistsExpanded = !expansion.playlistsExpanded },
            currentTrack = deps.currentTrack,
            videoTrackEntities = deps.videoTrackEntities,
            onPlaylistSelected = deps.onPlaylistSelected
        )
    )
}

private fun LazyListScope.legacyResultsSection(
    deps: SearchResultsDeps,
    expansion: SearchExpansionState,
    results: List<AudioItem>,
    searchQuery: String,
    onSearchTriggered: (String, Boolean) -> Unit
) {
    collapsibleYouTubeSearchResultsSection(
        LegacyResultsSectionState(
            results = results,
            videosExpanded = expansion.legacyVideosExpanded,
            onToggle = { expansion.legacyVideosExpanded = !expansion.legacyVideosExpanded },
            currentTrack = deps.currentTrack,
            trackEntities = deps.legacyTrackEntities,
            playerViewModel = deps.playerViewModel,
            coroutineScope = deps.coroutineScope,
            youtubeLabel = deps.youtubeLabel,
            loadMoreLabel = deps.loadMoreLabel,
            onLoadMore = { onSearchTriggered(searchQuery, true) }
        )
    )
}

@Composable
private fun SearchInputField(
    context: Context,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchTriggered: (String, Boolean) -> Unit,
    onShowQrScannerChange: (Boolean) -> Unit,
    isLoading: Boolean,
    focusRequester: FocusRequester
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = {
            Text(
                Translations.get(context, "search_placeholder"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        trailingIcon = {
            SearchFieldTrailingIcon(
                context = context,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onShowQrScannerChange = onShowQrScannerChange
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                if (searchQuery.isNotBlank() && !isLoading) {
                    onSearchTriggered(searchQuery, false)
                }
            }
        ),
        enabled = !isLoading,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.secondary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.Monospace
        )
    )
}

@Composable
private fun SearchFieldTrailingIcon(
    context: Context,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onShowQrScannerChange: (Boolean) -> Unit
) {
    Row {
        if (searchQuery.isNotEmpty()) {
            IconButton(onClick = {
                onSearchQueryChange("")
            }) {
                Text(
                    text = "x",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
        IconButton(onClick = { onShowQrScannerChange(true) }) {
            Text(
                text = Translations.get(context, "search_scan_qr"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun SearchLoadingIndicator(context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "\$ ${Translations.get(context, "search_loading")}",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary
            )
        )
    }
}

@Composable
private fun SearchErrorMessage(context: Context, error: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        "${Translations.get(context, "search_error")}: $error",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace
        )
    )
}

/**
 * Resultados "legacy" (basados en [AudioItem]) como items de un [LazyListScope].
 * Antes era un `Column` con `forEachIndexed`, que componía toda la lista de golpe.
 * Los estados y dependencias se agrupan en un solo parámetro para mantener la
 * firma corta (detekt LongParameterList).
 */
private data class LegacyResultsSectionState(
    val results: List<AudioItem>,
    val videosExpanded: Boolean,
    val onToggle: () -> Unit,
    val currentTrack: TrackEntity?,
    val trackEntities: List<TrackEntity>,
    val playerViewModel: PlayerViewModel?,
    val coroutineScope: CoroutineScope,
    val youtubeLabel: String,
    val loadMoreLabel: String,
    val onLoadMore: () -> Unit,
)

private fun LazyListScope.collapsibleYouTubeSearchResultsSection(state: LegacyResultsSectionState) {
    val results = state.results
    item(key = "legacy_header") {
        Text(
            text = if (state.videosExpanded) "v ${state.youtubeLabel} [${results.size}]" else "> ${state.youtubeLabel} [${results.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier
                .clickable { state.onToggle() }
                .padding(4.dp)
        )
    }

    if (state.videosExpanded) {
        items(
            count = results.size,
            key = { index -> results[index].videoId }
        ) { index ->
            val item = results[index]
            val song = Song(
                number = index + 1,
                title = item.title,
                artist = item.channel,
                youtubeId = item.videoId,
                shareUrl = "https://www.youtube.com/watch?v=${item.videoId}"
            )
            val isPlaying = state.currentTrack?.youtubeVideoId == item.videoId
            SongListItem(
                song = song,
                trackEntities = state.trackEntities,
                index = index,
                playerViewModel = state.playerViewModel,
                coroutineScope = state.coroutineScope,
                modifier = Modifier.fillMaxWidth(),
                isCurrentlyPlaying = isPlaying
            )
        }

        if (results.size >= 10) {
            item(key = "legacy_load_more") {
                Text(
                    text = "> ${state.loadMoreLabel}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .clickable { state.onLoadMore() }
                        .padding(8.dp)
                )
            }
        }
    }
}
