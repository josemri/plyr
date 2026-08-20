package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
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

    val nfcScanResult by NfcScanEvent.scanResult.collectAsState()

    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
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
            val verticalPagerState = rememberPagerState(initialPage = 1) { 2 }
            val verticalPagerScope = rememberCoroutineScope()

            BackHandler(enabled = verticalPagerState.currentPage == 0) {
                verticalPagerScope.launch {
                    verticalPagerState.animateScrollToPage(1)
                }
            }

            VerticalPager(
                state = verticalPagerState,
                modifier = Modifier.fillMaxSize()
            ) { verticalPage ->
                when (verticalPage) {
                    0 -> SearchScreen(
                        context = context,
                        onVideoSelectedFromSearch = onVideoSelectedFromSearch,
                        onBack = {
                            verticalPagerScope.launch {
                                verticalPagerState.animateScrollToPage(1)
                            }
                        },
                        playerViewModel = playerViewModel,
                        isActive = verticalPagerState.currentPage == 0
                    )
                    1 -> {
                        val horizontalPagerState = rememberPagerState(initialPage = 1) { 3 }
                        val horizontalPagerScope = rememberCoroutineScope()

                        BackHandler(enabled = horizontalPagerState.currentPage != 1) {
                            horizontalPagerScope.launch {
                                horizontalPagerState.animateScrollToPage(1)
                            }
                        }

                        HorizontalPager(
                            state = horizontalPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> ConfigScreen(
                                    context = context,
                                    onBack = {
                                        horizontalPagerScope.launch {
                                            horizontalPagerState.animateScrollToPage(1)
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
                                        currentScreen = Screen.PLAYLISTS.name
                                    }
                                )
                                2 -> PlaylistsScreen(
                                    context = context,
                                    onBack = {
                                        horizontalPagerScope.launch {
                                            horizontalPagerState.animateScrollToPage(1)
                                        }
                                    },
                                    playerViewModel = playerViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
        Screen.SEARCH -> SearchScreen(
            context = context,
            onVideoSelectedFromSearch = onVideoSelectedFromSearch,
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel,
            isActive = true
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
            onInitialConsumed = {
                playlistToOpenId = null
            }
        )
        Screen.FEED -> FeedScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME.name },
            onNavigateToSearch = {
                currentScreen = Screen.SEARCH.name
            },
            playerViewModel = playerViewModel
        )
        else -> {}
    }
}
