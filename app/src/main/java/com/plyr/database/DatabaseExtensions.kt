package com.plyr.database

import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyTrack

/**
 * DatabaseExtensions - Funciones de extensión para entidades de base de datos
 * 
 * Proporciona conversiones entre entidades locales y modelos de Spotify,
 * así como utilidades para gestión de sincronización.
 */

// === CONVERSIONES DE COMPATIBILIDAD ===

/**
 * Convierte PlaylistEntity a SpotifyPlaylist para compatibilidad con UI existente.
 * Permite reutilizar componentes UI sin modificaciones.
 */
fun PlaylistEntity.toSpotifyPlaylist(): SpotifyPlaylist {
    return SpotifyPlaylist(
        id = this.spotifyId,
        name = this.name,
        description = this.description,
        tracks = null, // No incluimos tracks aquí para evitar cargas innecesarias
        images = if (this.imageUrl != null) listOf() else null // Simplificado por ahora
    )
}

/**
 * Convierte TrackEntity a SpotifyTrack para compatibilidad con UI existente.
 * Reconstruye la lista de artistas desde el string concatenado.
 */
fun TrackEntity.toSpotifyTrack(): SpotifyTrack {
    return SpotifyTrack(
        id = this.spotifyTrackId,
        name = this.name,
        artists = this.artists.split(", ").map { artistName ->
            com.plyr.network.SpotifyArtist(name = artistName)
        }
    )
}

// === UTILIDADES DE TRACKS ===

/**
 * Verifica si un track tiene YouTube ID asignado.
 * Útil para determinar si un track puede reproducirse.
 */
fun TrackEntity.hasYoutubeId(): Boolean = youtubeVideoId != null

// === INFORMACIÓN DE SINCRONIZACIÓN ===

/**
 * Clase de datos para mostrar información de progreso de sincronización.
 * Proporciona estadísticas útiles sobre el estado de una playlist.
 */
data class PlaylistSyncInfo(
    val playlist: PlaylistEntity,
    val totalTracks: Int,
    val tracksWithYoutubeId: Int,
    val syncProgress: Float
) {
    /**
     * Obtiene un string descriptivo del estado de sincronización.
     * 
     * @return Estado legible para mostrar en UI
     */
    fun getSyncStatus(): String {
        return when {
            totalTracks == 0 -> "Sin tracks"
            tracksWithYoutubeId == totalTracks -> "Completo"
            tracksWithYoutubeId == 0 -> "Pendiente"
            else -> "$tracksWithYoutubeId/$totalTracks"
        }
    }
}
