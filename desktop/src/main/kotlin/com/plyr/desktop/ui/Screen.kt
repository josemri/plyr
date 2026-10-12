package com.plyr.desktop.ui

import androidx.compose.runtime.Stable

/**
 * Pantallas de la app de escritorio. Mismo conjunto que Android
 * (`com.plyr.ui.Screen`) para mantener una navegación equivalente.
 *
 * [titleKey] es la clave de traducción del título de cada pantalla.
 */
enum class Screen(val titleKey: String) {
    HOME("home_feed"),
    SEARCH("search_title"),
    QUEUE("plyr_queue"),
    PLAYLISTS("plyr_lists"),
    FEED("feed_title"),
    CONFIG("config_title"),
}

/** Opción de menú: una pantalla y el texto ya traducido que la representa. */
@Stable
data class MenuOption(val screen: Screen, val title: String)
