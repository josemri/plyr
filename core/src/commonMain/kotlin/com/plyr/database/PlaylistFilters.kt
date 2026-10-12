package com.plyr.database

/** Identidad de la lista especial de favoritos, común a Android y escritorio. */
object LikedSongs {
    const val ID = "liked_songs"
}

/**
 * Filtro y orden de las listas que se muestran en el carrusel del Home y en la
 * rejilla de listas, compartidos por Android y escritorio (antes vivían en
 * `PlaylistLocalRepository`).
 *
 * Dos exclusiones:
 * - `album_*`: los álbumes de Spotify no son listas del usuario.
 * - *liked* vacía: la fila se crea siempre al arrancar
 *   (`ensureLikedSongsPlaylist`), así que sin este filtro el primer elemento del
 *   Home era un corazón rojo sin nada detrás (F1).
 *
 * **Es un filtro, no un borrado**: `toggleLikeTrack`, `mergeLikedSongsTracks` e
 * `ImportManifest.plan` dan por hecho que la fila de *liked* existe, y borrarla
 * "si está vacía" rompería las tres. `trackCount` lo mantienen las tres rutas que
 * la modifican, así que basta con mirarlo — y como la tabla se observa, la fila
 * aparece y desaparece sola.
 */
object PlaylistFilters {

    /** Prefijo de las filas legacy que la app oculta en los listados. */
    const val LEGACY_ALBUM_PREFIX = "album_"

    fun <T : PlaylistRow> visiblePlaylists(
        playlists: List<T>,
        likedSongsId: String = LikedSongs.ID,
    ): List<T> =
        playlists
            .filter { !it.remoteId.startsWith(LEGACY_ALBUM_PREFIX) }
            .filter { it.remoteId != likedSongsId || it.trackCount > 0 }
            .sortedBy { if (it.remoteId == likedSongsId) "" else it.name }
}
