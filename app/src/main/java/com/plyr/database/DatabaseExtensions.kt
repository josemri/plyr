package com.plyr.database

import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyTrack

// Convertir PlaylistEntity a SpotifyPlaylist para compatibilidad con UI existente
fun PlaylistEntity.toSpotifyPlaylist(): SpotifyPlaylist {
    return SpotifyPlaylist(
        id = this.spotifyId,
        name = this.name,
        description = this.description,
        tracks = null, // No incluimos tracks aquí para evitar cargas innecesarias
        images = if (this.imageUrl != null) listOf() else null // Simplificado por ahora
    )
}

// Convertir TrackEntity a SpotifyTrack para compatibilidad con UI existente
fun TrackEntity.toSpotifyTrack(): SpotifyTrack {
    return SpotifyTrack(
        id = this.spotifyTrackId,
        name = this.name,
        artists = this.artists.split(", ").map { artistName ->
            com.plyr.network.SpotifyArtist(name = artistName)
        }
    )
}

// Extensión para obtener el ID de YouTube si existe
fun TrackEntity.hasYoutubeId(): Boolean = youtubeVideoId != null

// Extensión para mostrar información de progreso de sincronización
data class PlaylistSyncInfo(
    val playlist: PlaylistEntity,
    val totalTracks: Int,
    val tracksWithYoutubeId: Int,
    val syncProgress: Float
) {
    fun getSyncStatus(): String {
        return when {
            totalTracks == 0 -> "Sin tracks"
            tracksWithYoutubeId == totalTracks -> "Completo"
            tracksWithYoutubeId == 0 -> "Pendiente"
            else -> "$tracksWithYoutubeId/$totalTracks"
        }
    }
}
