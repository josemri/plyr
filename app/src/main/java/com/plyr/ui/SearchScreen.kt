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
import com.plyr.utils.Translations
import com.plyr.utils.NfcScanEvent
import com.plyr.database.TrackEntity
import com.plyr.database.SearchHistoryEntity
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

@Composable
fun SearchScreen(
    context: Context,
    initialQuery: String? = null,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    isActive: Boolean = true
) {
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var results by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var youtubeAllResults by remember { mutableStateOf<YouTubeSearchManager.YouTubeSearchAllResult?>(null) }
    var showYouTubeAllResults by remember { mutableStateOf(false) }
    var selectedYouTubePlaylist by remember { mutableStateOf<YouTubeSearchManager.YouTubePlaylistInfo?>(null) }

    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()


    var showQrScanner by remember { mutableStateOf(false) }

    val nfcScanResult by NfcScanEvent.scanResult.collectAsState()

    val database = remember { PlaylistDatabase.getDatabase(context) }
    val searchHistoryDao = database.searchHistoryDao()

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
                try {
                    if (!isLoadMore) {
                        try {
                            searchHistoryDao.deleteSearchByQuery(query, "youtube")
                            searchHistoryDao.insertSearch(
                                SearchHistoryEntity(
                                    query = query,
                                    searchEngine = "youtube"
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }

                    youtubeAllResults = null
                    showYouTubeAllResults = false

                    val searchResults = youtubeSearchManager.searchYouTubeAll(query)
                    youtubeAllResults = searchResults
                    showYouTubeAllResults = true

                    val newResults = searchResults.videos.map { videoInfo ->
                        AudioItem(
                            title = videoInfo.title,
                            url = "",
                            videoId = videoInfo.videoId,
                            channel = videoInfo.uploader,
                            duration = videoInfo.getFormattedDuration()
                        )
                    }

                    results = newResults
                    isLoading = false

                } catch (e: Exception) {
                    isLoading = false
                    error = "${Translations.get(context, "search_error")}: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val query = initialQuery
        if (!query.isNullOrBlank()) {
            performSearch(query, false)
        }
    }

    LaunchedEffect(nfcScanResult) {
        val result = nfcScanResult ?: return@LaunchedEffect

        NfcScanEvent.consumeResult()

        try {
            when (result.source) {
                "youtube" -> {
                    if (result.type == "playlist") {
                        val info = youtubeSearchManager.getYouTubePlaylistInfo(result.id)
                        if (info != null) {
                            selectedYouTubePlaylist = info
                        } else {
                            error = "${Translations.get(context, "search_error_processing_qr")}: playlist not available"
                            isLoading = false
                        }
                    } else {
                        val videoUrl = "https://www.youtube.com/watch?v=${result.id}"
                        searchQuery = videoUrl
                        performSearch(videoUrl, false)
                    }
                }
                else -> {
                    error = "${Translations.get(context, "search_error_processing_qr")}: unsupported source '${result.source}'"
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            error = "${Translations.get(context, "search_error_processing_qr")}: ${e.message}"
            isLoading = false
        }
    }

    BackHandler {
        when {
            showQrScanner -> {
                showQrScanner = false
            }
            selectedYouTubePlaylist != null -> {
                selectedYouTubePlaylist = null
            }
            else -> onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val ytPlaylist = selectedYouTubePlaylist
        when {
            ytPlaylist != null -> {
                YouTubePlaylistDetailView(
                    playlist = ytPlaylist,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
            else -> {
                SearchMainView(
                    context = context,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    results = results,
                    isLoading = isLoading,
                    error = error,
                    onSearchTriggered = performSearch,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope,
                    youtubeAllResults = youtubeAllResults,
                    showYouTubeAllResults = showYouTubeAllResults,
                    onYouTubePlaylistSelected = { playlist ->
                        selectedYouTubePlaylist = playlist
                    },
                    onShowQrScannerChange = { showQrScanner = it },
                    isActive = isActive
                )
                if (showQrScanner) {
                    QrScannerDialog(
                        onDismiss = { showQrScanner = false },
                        onQrScanned = { qrResult ->
                            showQrScanner = false
                            if (qrResult != null) {
                                coroutineScope.launch {
                                    try {
                                        when (qrResult.source) {
                                            "youtube" -> {
                                                if (qrResult.type == "playlist") {
                                                    val info = youtubeSearchManager.getYouTubePlaylistInfo(qrResult.id)
                                                    if (info != null) {
                                                        selectedYouTubePlaylist = info
                                                    } else {
                                                        error = "${Translations.get(context, "search_error_processing_qr")}: playlist not available"
                                                        isLoading = false
                                                    }
                                                } else {
                                                    val videoUrl = "https://www.youtube.com/watch?v=${qrResult.id}"
                                                    searchQuery = videoUrl
                                                    performSearch(videoUrl, false)
                                                }
                                            }
                                            else -> {
                                                error = "${Translations.get(context, "search_error_processing_qr")}: unsupported source '${qrResult.source}'"
                                                isLoading = false
                                            }
                                        }
                                    } catch (e: Exception) {
                                        error = "${Translations.get(context, "search_error_processing_qr")}: ${e.message}"
                                        isLoading = false
                                    }
                                }
                            }
                        }
                    )
                }
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
    var videosExpanded by remember { mutableStateOf(true) }
    var playlistsExpanded by remember { mutableStateOf(false) }
    var legacyVideosExpanded by remember { mutableStateOf(true) }

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

    val youtubeLabel = Translations.get(context, "search_youtube_results")
    val loadMoreLabel = Translations.get(context, "search_load_more")

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "search_title") {
            Titulo(Translations.get(context, "search_title"))
        }

        item(key = "search_field") {
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

        item(key = "search_spacer") { Spacer(Modifier.height(12.dp)) }

        if (isLoading) {
            item(key = "search_loading") {
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
        }

        error?.let { err ->
            item(key = "search_error") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Translations.get(context, "search_error")}: $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }

        if (showYouTubeAllResults && youtubeAllResults != null) {
            youtubeSearchResultsSection(
                YouTubeSearchResultsState(
                    deps = YouTubeSearchSectionDeps(
                        youtubeManager = youtubeManager,
                        coverCache = coverCache,
                        coverSemaphore = coverSemaphore,
                        playerViewModel = playerViewModel,
                        coroutineScope = coroutineScope,
                    ),
                    allResults = youtubeAllResults,
                    videosExpanded = videosExpanded,
                    onToggleVideos = { videosExpanded = !videosExpanded },
                    playlistsExpanded = playlistsExpanded,
                    onTogglePlaylists = { playlistsExpanded = !playlistsExpanded },
                    currentTrack = currentTrack,
                    videoTrackEntities = videoTrackEntities,
                    onPlaylistSelected = onYouTubePlaylistSelected
                )
            )
        }

        if (results.isNotEmpty() && !showYouTubeAllResults) {
            collapsibleYouTubeSearchResultsSection(
                LegacyResultsSectionState(
                    results = results,
                    videosExpanded = legacyVideosExpanded,
                    onToggle = { legacyVideosExpanded = !legacyVideosExpanded },
                    currentTrack = currentTrack,
                    trackEntities = legacyTrackEntities,
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope,
                    youtubeLabel = youtubeLabel,
                    loadMoreLabel = loadMoreLabel,
                    onLoadMore = { onSearchTriggered(searchQuery, true) }
                )
            )
        }
    }
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
