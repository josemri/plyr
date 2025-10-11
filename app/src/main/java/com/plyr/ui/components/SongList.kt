package com.plyr.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plyr.database.TrackEntity
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyTrack
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

@Composable
fun SongList(
    playlist: SpotifyPlaylist,
    tracks: List<SpotifyTrack>,
    trackEntities: List<TrackEntity>? = null,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    val entities = trackEntities ?: remember(tracks) {
        tracks.mapIndexed { trackIndex, spotifyTrack ->
            TrackEntity(
                id = "spotify_${spotifyTrack.id}",
                playlistId = playlist.id,
                spotifyTrackId = spotifyTrack.id,
                name = spotifyTrack.name,
                artists = spotifyTrack.getArtistNames(),
                youtubeVideoId = null,
                audioUrl = null,
                position = trackIndex,
                lastSyncTime = System.currentTimeMillis()
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(tracks.size) { index ->
            val track = tracks[index]
            val song = Song(
                number = index + 1,
                title = track.name,
                artist = track.getArtistNames(),
                spotifyId = track.id,
                spotifyUrl = "https://open.spotify.com/track/${track.id}"
            )
            SongListItem(
                song = song,
                trackEntities = entities,
                index = index,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
