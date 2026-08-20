package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.plyr.utils.Config
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
import com.plyr.ui.components.search.YouTubeSearchResults
import com.plyr.ui.components.QrScannerDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import com.plyr.ui.components.Titulo

@Composable
fun SearchScreen(
    context: Context,
    initialQuery: String? = null,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    isActive: Boolean = true
) {
    var searchQuery by remember { mutableStateOf(initialQuery ?: "") }
    var results by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var currentLanguage by remember { mutableStateOf(Config.getLanguage(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            val newLanguage = Config.getLanguage(context)
            if (newLanguage != currentLanguage) {
                currentLanguage = newLanguage
            }
        }
    }

    var youtubeAllResults by remember { mutableStateOf<YouTubeSearchManager.YouTubeSearchAllResult?>(null) }
    var showYouTubeAllResults by remember { mutableStateOf(false) }
    var selectedYouTubePlaylist by remember { mutableStateOf<YouTubeSearchManager.YouTubePlaylistInfo?>(null) }

    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val currentPlayingTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }

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
                    val videoUrl = "https://www.youtube.com/watch?v=${result.id}"
                    searchQuery = videoUrl
                    performSearch(videoUrl, false)
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
        when {
            selectedYouTubePlaylist != null -> {
                YouTubePlaylistDetailView(
                    playlist = selectedYouTubePlaylist!!,
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
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch,
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
                                                val videoUrl = "https://www.youtube.com/watch?v=${qrResult.id}"
                                                searchQuery = videoUrl
                                                performSearch(videoUrl, false)
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
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Titulo(Translations.get(context, "search_title"))

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

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
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

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "${Translations.get(context, "search_error")}: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        if (showYouTubeAllResults && youtubeAllResults != null) {
            YouTubeSearchResults(
                results = null,
                youtubeAllResults = youtubeAllResults,
                onVideoSelectedFromSearch = onVideoSelectedFromSearch,
                onPlaylistSelected = onYouTubePlaylistSelected,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        }

        if (results.isNotEmpty() && !showYouTubeAllResults) {
            CollapsibleYouTubeSearchResultsView(
                context = context,
                results = results,
                onLoadMore = { onSearchTriggered(searchQuery, true) },
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        }
    }
}

@Composable
fun CollapsibleYouTubeSearchResultsView(
    context: Context,
    results: List<AudioItem>,
    onLoadMore: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var videosExpanded by remember { mutableStateOf(true) }

    val currentPlayingTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }

    val youtubeLabel = Translations.get(context, "search_youtube_results")
    val loadMoreLabel = Translations.get(context, "search_load_more")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (videosExpanded) "v $youtubeLabel [${results.size}]" else "> $youtubeLabel [${results.size}]",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier
                .clickable { videosExpanded = !videosExpanded }
                .padding(4.dp)
        )

        if (videosExpanded) {
            val trackEntities = results.mapIndexed { trackIndex, item ->
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                results.forEachIndexed { index, item ->
                    val song = Song(
                        number = index + 1,
                        title = item.title,
                        artist = item.channel,
                        youtubeId = item.videoId,
                        shareUrl = "https://www.youtube.com/watch?v=${item.videoId}"
                    )
                    val isPlaying = currentPlayingTrack?.youtubeVideoId == item.videoId
                    SongListItem(
                        song = song,
                        trackEntities = trackEntities,
                        index = index,
                        playerViewModel = playerViewModel,
                        coroutineScope = coroutineScope,
                        modifier = Modifier.fillMaxWidth(),
                        isCurrentlyPlaying = isPlaying
                    )
                }

                if (results.size >= 10) {
                    Text(
                        text = "> $loadMoreLabel",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .clickable { onLoadMore() }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}
