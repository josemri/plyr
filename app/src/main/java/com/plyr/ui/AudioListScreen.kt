package com.plyr.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.plyr.model.AudioItem
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.NfcScanEvent

// Estados para navegación
enum class Screen {
    HOME,
    SEARCH,
    QUEUE,
    CONFIG,
    PLAYLISTS,
    LOCAL,
    FEED
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
    // Usar rememberSaveable para persistir el estado de navegación durante cambios de configuración
    var currentScreen by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val currentScreenEnum = Screen.valueOf(currentScreen)

    // Observar eventos de NFC para navegar al SearchScreen desde cualquier pantalla
    val nfcScanResult by NfcScanEvent.scanResult.collectAsState()

    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            android.util.Log.d("AudioListScreen", "🏷️ NFC detected, navigating to SearchScreen from ${currentScreen}")
            // Navegar al SearchScreen cuando se detecta un NFC, desde cualquier pantalla
            currentScreen = Screen.SEARCH.name
        }
    }

    BackHandler(enabled = currentScreenEnum != Screen.HOME) {
        currentScreen = Screen.HOME.name
    }

    when (currentScreenEnum) {
        Screen.HOME -> HomeScreen(
            context = context,
            playerViewModel = playerViewModel,
            onNavigateToScreen = { screen -> currentScreen = screen.name }
        )
        Screen.SEARCH -> SearchScreen(
            context = context,
            onVideoSelectedFromSearch = onVideoSelectedFromSearch,
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel
        )
        Screen.QUEUE -> QueueScreen(
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel
        )
        Screen.CONFIG -> ConfigScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME.name },
            onThemeChanged = onThemeChanged
        )
        Screen.PLAYLISTS -> PlaylistsScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel
        )
        Screen.LOCAL -> LocalScreen(
            onBack = { currentScreen = Screen.HOME.name },
            playerViewModel = playerViewModel
        )
        Screen.FEED -> FeedScreen(
            context = context,
            onBack = { currentScreen = Screen.HOME.name },
            onNavigateToSearch = { currentScreen = Screen.SEARCH.name },
            playerViewModel = playerViewModel
        )
    }
}
