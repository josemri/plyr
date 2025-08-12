package com.plyr.ui.components.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.model.AudioItem
import com.plyr.network.SpotifyAlbum
import com.plyr.network.SpotifyArtistFull
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifySearchAllResponse
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun SearchMainView(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    results: List<AudioItem>,
    spotifyResults: SpotifySearchAllResponse?,
    showSpotifyResults: Boolean,
    isLoading: Boolean,
    error: String?,
    onVideoSelected: (String, String) -> Unit,
    onVideoSelectedFromSearch: (String, String, List<AudioItem>, Int) -> Unit,
    onAlbumSelected: (SpotifyAlbum) -> Unit,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit,
    onArtistSelected: (SpotifyArtistFull) -> Unit, // Agregar parámetro faltante
    onSearchTriggered: (String, Boolean) -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column {
        // Search header
        Text(
            text = "$ search",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search input
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    "Search (prefix: yt: youtube, sp: spotify)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearchTriggered(searchQuery, false)
                }
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color.White
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF4ECDC4)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Loading indicator
        if (isLoading) {
            Text(
                text = "> searching...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFD93D)
                )
            )
        }

        // Error message
        error?.let { errorMessage ->
            Text(
                text = "> error: $errorMessage",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE74C3C)
                ),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Search results
        when {
            showSpotifyResults && spotifyResults != null -> {
                SpotifySearchResults(
                    searchResults = spotifyResults,
                    onAlbumSelected = onAlbumSelected,
                    onPlaylistSelected = onPlaylistSelected,
                    onArtistSelected = onArtistSelected, // Pasar el parámetro
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope
                )
            }
            results.isNotEmpty() -> {
                YouTubeSearchResults(
                    results = results,
                    onVideoSelected = onVideoSelected,
                    onVideoSelectedFromSearch = onVideoSelectedFromSearch
                )
            }
            !isLoading && searchQuery.isNotBlank() -> {
                Text(
                    text = "> no results found",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
