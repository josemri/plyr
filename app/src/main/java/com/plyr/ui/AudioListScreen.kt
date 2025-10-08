package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.plyr.model.AudioItem
import com.plyr.viewmodel.PlayerViewModel

// Estados para navegación
enum class Screen {
    HOME,
    SEARCH,
    QUEUE,
    CONFIG,
    PLAYLISTS
}

@Stable
data class MenuOption(val screen: Screen, val title: String)

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
    onThemeChanged: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }

    when (currentScreen) {
        Screen.HOME -> HomeScreen(context) { screen -> currentScreen = screen }
        Screen.SEARCH -> SearchScreen(
            context = context,
            onVideoSelectedFromSearch = onVideoSelectedFromSearch,
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
        Screen.QUEUE -> QueueScreen(
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
        Screen.CONFIG -> ConfigScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME },
            onThemeChanged = onThemeChanged
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
    }
}
