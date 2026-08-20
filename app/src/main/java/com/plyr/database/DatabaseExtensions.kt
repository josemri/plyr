package com.plyr.database

import com.plyr.network.AppPlaylist
import com.plyr.network.AppTrack
import com.plyr.network.AppImage

fun PlaylistEntity.toAppPlaylist(): AppPlaylist {
    return AppPlaylist(
        id = this.remoteId,
        name = this.name,
        description = this.description,
        tracks = null,
        images = if (this.imageUrl != null) listOf(AppImage(url = this.imageUrl, height = null, width = null)) else null
    )
}

fun TrackEntity.toAppTrack(): AppTrack {
    return AppTrack(
        id = this.remoteTrackId,
        name = this.name,
        artists = this.artists.split(", ").map { artistName ->
            com.plyr.network.AppArtist(name = artistName)
        }
    )
}