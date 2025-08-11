package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import com.plyr.model.AudioItem
import com.plyr.ui.navigation.Screen
import com.plyr.ui.screens.*
import com.plyr.viewmodel.PlayerViewModel

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit = { _, _, _, _ -> },
    onThemeChanged: (String) -> Unit = {},
    playerViewModel: PlayerViewModel? = null
) {
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    // Handle back button - always go to HOME, never exit app
    BackHandler(enabled = currentScreen != Screen.HOME) {
        currentScreen = Screen.HOME
    }

    when (currentScreen) {
        Screen.HOME -> HomeScreen(
            context = context,
            onNavigateToScreen = { screen -> currentScreen = screen }
        )
        Screen.SEARCH -> SearchScreen(
            context = context,
            onVideoSelected = onVideoSelected,
            onVideoSelectedFromSearch = onVideoSelectedFromSearch,
            onBack = { currentScreen = Screen.HOME },
            playerViewModel = playerViewModel
        )
        Screen.QUEUE -> QueueScreen(
            context = context,
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
