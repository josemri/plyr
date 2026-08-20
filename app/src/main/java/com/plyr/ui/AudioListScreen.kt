package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.plyr.model.AudioItem
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.NfcScanEvent
import kotlinx.coroutines.launch

// Estados para navegación
enum class Screen {
    HOME,
    SEARCH,
    QUEUE,
    CONFIG,
    PLAYLISTS,
    FEED
}

@Stable
data class MenuOption(val screen: Screen, val title: String)

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
    onThemeChanged: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null,
    navigateToScreenRequest: String? = null,
    onNavigateHandled: () -> Unit = {}
) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val currentScreenEnum = Screen.valueOf(currentScreen)

    var playlistToOpenId by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlaylistCreate by rememberSaveable { mutableStateOf(false) }
    var searchInitialQuery by rememberSaveable { mutableStateOf<String?>(null) }

    val nfcScanResult by NfcScanEvent.scanResult.collectAsState()

    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            android.util.Log.d("AudioListScreen", "🏷️ NFC detected, navigating to SearchScreen from ${currentScreen}")
            searchInitialQuery = null
            currentScreen = Screen.SEARCH.name
        }
    }

    BackHandler(enabled = currentScreenEnum != Screen.HOME) {
        currentScreen = Screen.HOME.name
    }

    LaunchedEffect(navigateToScreenRequest) {
        if (navigateToScreenRequest != null) {
            currentScreen = navigateToScreenRequest
            onNavigateHandled()
        }
    }

    when (currentScreenEnum) {
        Screen.HOME -> {
            val pagerState = rememberPagerState(initialPage = 1) { 3 }
            val pagerScope = rememberCoroutineScope()

            BackHandler(enabled = pagerState.currentPage != 1) {
                pagerScope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> ConfigScreen(
                        context = context,
                        onBack = {
                            pagerScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onThemeChanged = onThemeChanged
                    )
                    1 -> HomeScreen(
                        context = context,
                        playerViewModel = playerViewModel,
                        onNavigateToScreen = { screen -> currentScreen = screen.name },
                        onOpenPlaylist = { playlistId ->
                            playlistToOpenId = playlistId
                            openPlaylistCreate = false
                            currentScreen = Screen.PLAYLISTS.name
                        },
                        onCreatePlaylist = {
                            playlistToOpenId = null
                            openPlaylistCreate = true
                            currentScreen = Screen.PLAYLISTS.name
                        },
                        onSearchSubmitted = { query ->
                            searchInitialQuery = query
                            currentScreen = Screen.SEARCH.name
                        }
                    )
                    2 -> PlaylistsScreen(
                        context = context,
                        onBack = {
                            pagerScope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
        Screen.SEARCH -> SearchScreen(
            context = context,
            initialQuery = searchInitialQuery,
            onVideoSelectedFromSearch = onVideoSelectedFromSearch,
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel
        )
        Screen.QUEUE -> QueueScreen(
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel,
            initialPlaylistId = playlistToOpenId,
            openCreate = openPlaylistCreate,
            onInitialConsumed = {
                playlistToOpenId = null
                openPlaylistCreate = false
            }
        )
        Screen.FEED -> FeedScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME.name },
            onNavigateToSearch = {
                searchInitialQuery = null
                currentScreen = Screen.SEARCH.name
            },
            playerViewModel = playerViewModel
        )
        else -> {}
    }
}
