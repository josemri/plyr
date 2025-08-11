package com.plyr.ui.navigation

import androidx.compose.runtime.Stable

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
