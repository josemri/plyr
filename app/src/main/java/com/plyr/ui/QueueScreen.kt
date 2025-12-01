package com.plyr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.Translations
import com.plyr.ui.components.Titulo
import androidx.compose.ui.platform.LocalContext
import com.plyr.ui.components.SongListItem
import com.plyr.ui.components.Song

@Composable
fun QueueScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Handle back button
    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
		Titulo(Translations.get(context, "plyr_queue"))

        // Contenido de la playlist
        if (playerViewModel != null) {
            val currentPlaylist by playerViewModel.currentPlaylist.observeAsState()
            val currentTrackIndex by playerViewModel.currentTrackIndex.observeAsState()

            if (currentPlaylist != null && currentPlaylist!!.isNotEmpty()) {
                // Lista de canciones con SongListItem
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        count = currentPlaylist!!.size,
                        key = { index -> "${currentPlaylist!![index].id}_$index" }
                    ) { index ->
                        val track = currentPlaylist!![index]
                        val isCurrentTrack = currentTrackIndex == index

                        // Convertir TrackEntity a Song
                        val song = Song(
                            number = index + 1,
                            title = track.name,
                            artist = track.artists.ifEmpty { "Unknown Artist" },
                            spotifyId = track.spotifyTrackId,
                            youtubeId = track.youtubeVideoId,
                            spotifyUrl = null // TrackEntity no tiene spotifyUrl
                        )

                        SongListItem(
                            song = song,
                            trackEntities = currentPlaylist!!,
                            index = index,
                            playerViewModel = playerViewModel,
                            coroutineScope = coroutineScope,
                            isSelected = isCurrentTrack,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            } else {
                // Estado vacío
            Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
				    Text(
                        text = Translations.get(context, "No tracks loaded"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline
                        )
                    )
                }
		    }
        } else {
            // PlayerViewModel no disponible
		    Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Translations.get(context, "Player not available"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
             }
         }
     }
 }
