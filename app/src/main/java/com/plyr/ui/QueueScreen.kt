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
import com.plyr.ui.utils.stableKeys

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

            val playlist = currentPlaylist
            if (playlist != null && playlist.isNotEmpty()) {
                // Claves estables: el `id` de la pista desambiguado con el número de
                // aparición. Antes la clave incluía la posición, así que cualquier
                // inserción/borrado la cambiaba y Compose perdía el estado de cada fila.
                val itemKeys = remember(playlist) { stableKeys(playlist.map { it.id }) }
                // Lista de canciones con SongListItem
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        count = playlist.size,
                        key = { index -> itemKeys[index] }
                    ) { index ->
                        val track = playlist[index]
                        val isCurrentTrack = currentTrackIndex == index

                        // Convertir TrackEntity a Song
                        val song = Song(
                            number = index + 1,
                            title = track.name,
                            artist = track.artists.ifEmpty { Translations.get(context, "unknown_artist") },
                            remoteId = track.remoteTrackId,
                            youtubeId = track.youtubeVideoId,
                            shareUrl = null // TrackEntity no tiene shareUrl
                        )

                        SongListItem(
                            song = song,
                            trackEntities = playlist,
                            index = index,
                            playerViewModel = playerViewModel,
                            coroutineScope = coroutineScope,
                            isCurrentlyPlaying = isCurrentTrack,
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
                        text = Translations.get(context, "no_tracks_loaded"),
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
                    text = Translations.get(context, "player_not_available"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline
                    )
                )
             }
         }
     }
 }
