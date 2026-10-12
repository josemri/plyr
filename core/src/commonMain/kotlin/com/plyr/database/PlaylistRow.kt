package com.plyr.database

/**
 * Vista mínima de una lista que necesita la lógica pura compartida
 * ([PlaylistFilters.visiblePlaylists]).
 *
 * La implementan tanto `PlaylistEntity` (Room, en `:app`) como [PlaylistRecord]
 * (modelo de `:core` que usa el escritorio). Así el filtro y el orden de las
 * listas visibles viven en un solo sitio y no se pueden desincronizar entre
 * Android y escritorio.
 */
interface PlaylistRow {
    val remoteId: String
    val name: String
    val trackCount: Int
}

/**
 * Vista mínima de una pista para la identidad compartida
 * ([com.plyr.utils.TrackIdentity]): deduplicar favoritos y recordar borrados
 * (B1/B13/B51).
 *
 * La implementan `TrackEntity` (Room, en `:app`) y [TrackRecord] (`:core`).
 */
interface TrackRow {
    val name: String
    val artists: String
    val youtubeVideoId: String?
}

/**
 * Modelo completo de una lista, equivalente a `PlaylistEntity` de Room pero sin
 * dependencias de Android. Es el tipo que devuelven los repositorios de
 * escritorio (SQLDelight) y que consumirá la UI de `:desktop`.
 */
data class PlaylistRecord(
    override val remoteId: String,
    override val name: String,
    val description: String?,
    override val trackCount: Int,
    val imageUrl: String?,
    val lastSyncTime: Long,
    val source: PlaylistSource,
    val sourceId: String?,
) : PlaylistRow

/**
 * Modelo completo de una pista, equivalente a `TrackEntity` de Room pero sin
 * dependencias de Android.
 */
data class TrackRecord(
    val id: String,
    val playlistId: String,
    val remoteTrackId: String,
    override val name: String,
    override val artists: String,
    override val youtubeVideoId: String?,
    val audioUrl: String?,
    val position: Int,
    val lastSyncTime: Long,
) : TrackRow
