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
import com.plyr.viewmodel.ImportViewModel
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
    onThemeChanged: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null,
    importViewModel: ImportViewModel? = null,
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

    AudioListScreenContent(
        currentScreenEnum = currentScreenEnum,
        context = context,
        playerViewModel = playerViewModel,
        importViewModel = importViewModel,
        playlistToOpenId = playlistToOpenId,
        onThemeChanged = onThemeChanged,
        onNavigateToScreen = { currentScreen = it },
        onOpenPlaylist = { playlistId ->
            playlistToOpenId = playlistId
            currentScreen = Screen.PLAYLISTS.name
        },
        onConsumePlaylistId = { playlistToOpenId = null }
    )
}

@Composable
private fun AudioListScreenContent(
    currentScreenEnum: Screen,
    context: Context,
    playerViewModel: PlayerViewModel?,
    importViewModel: ImportViewModel?,
    playlistToOpenId: String?,
    onThemeChanged: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onConsumePlaylistId: () -> Unit
) {
    when (currentScreenEnum) {
        Screen.HOME -> AudioListHomeContent(
            context = context,
            playerViewModel = playerViewModel,
            importViewModel = importViewModel,
            onThemeChanged = onThemeChanged,
            onNavigateToScreen = onNavigateToScreen,
            onOpenPlaylist = onOpenPlaylist
        )
        Screen.SEARCH -> SearchScreen(
            context = context,
            onBack = { onNavigateToScreen(Screen.HOME.name) },
            playerViewModel = playerViewModel,
            isActive = true
        )
        Screen.QUEUE -> QueueScreen(
            onBack = { onNavigateToScreen(Screen.HOME.name) },
            playerViewModel = playerViewModel
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { onNavigateToScreen(Screen.HOME.name) },
            playerViewModel = playerViewModel,
            initialPlaylistId = playlistToOpenId,
            onInitialConsumed = onConsumePlaylistId
        )
        Screen.FEED -> FeedScreen(
            context = context,
            onBack = { onNavigateToScreen(Screen.HOME.name) },
            onNavigateToSearch = { onNavigateToScreen(Screen.SEARCH.name) },
            playerViewModel = playerViewModel
        )
        else -> {}
    }
}

@Composable
private fun AudioListHomeContent(
    context: Context,
    playerViewModel: PlayerViewModel?,
    importViewModel: ImportViewModel?,
    onThemeChanged: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
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
                onBack = {
                    verticalPagerScope.launch {
                        verticalPagerState.animateScrollToPage(1)
                    }
                },
                playerViewModel = playerViewModel,
                isActive = verticalPagerState.currentPage == 0
            )
            1 -> AudioListHomeHorizontalPager(
                context = context,
                playerViewModel = playerViewModel,
                importViewModel = importViewModel,
                onThemeChanged = onThemeChanged,
                onNavigateToScreen = onNavigateToScreen,
                onOpenPlaylist = onOpenPlaylist
            )
        }
    }
}

@Composable
private fun AudioListHomeHorizontalPager(
    context: Context,
    playerViewModel: PlayerViewModel?,
    importViewModel: ImportViewModel?,
    onThemeChanged: (String) -> Unit,
    onNavigateToScreen: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit
) {
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
                onThemeChanged = onThemeChanged,
                importViewModel = importViewModel
            )
            1 -> HomeScreen(
                context = context,
                onNavigateToScreen = { screen -> onNavigateToScreen(screen.name) },
                onOpenPlaylist = { playlistId -> onOpenPlaylist(playlistId) }
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
