package com.plyr.utils

import com.plyr.database.TrackRow

/**
 * Identidad de una pista, compartida por Android y escritorio.
 *
 * Es la que usan marcar/desmarcar favoritos y la fusión de `liked_songs` para no
 * duplicar ni resucitar pistas (B1/B13/B51): manda el `youtubeVideoId` y, si
 * falta, se cae a `nombre|artistas`. [likedTrackKey] y [fallbackDedupeKey] tienen
 * que ser **literalmente** el mismo criterio que la fusión y que los tombs; si
 * divergieran, un borrado se guardaría con una clave que la fusión nunca consulta
 * y la pista seguiría resucitando.
 *
 * Lógica pura a propósito: así se testea sin Android y la comparten el adaptador
 * Room (`:app`) y el de SQLDelight (`:desktop`).
 */
object TrackIdentity {

    /**
     * Clave para deduplicar una pista que no tiene `youtubeVideoId` (con ese
     * campo vacío no se puede comparar por vídeo).
     */
    fun fallbackDedupeKey(track: TrackRow): String = fallbackDedupeKey(track.name, track.artists)

    fun fallbackDedupeKey(name: String, artists: String): String = "$name|$artists"

    /** Clave con la que se identifica un favorito (y con la que se tombstonea su borrado). */
    fun likedTrackKey(track: TrackRow): String =
        likedTrackKey(track.youtubeVideoId, track.name, track.artists)

    /**
     * La misma clave, calculada desde los datos crudos que recibe `toggleLikeTrack`.
     * Existe para que marcar y desmarcar usen el mismo criterio que la fusión y el
     * tomb: si el marcado construyera la clave por su cuenta, un `youtubeVideoId`
     * vacío daría una clave distinta a la que luego comprueba la fusión.
     */
    fun likedTrackKey(youtubeVideoId: String?, name: String, artists: String): String =
        youtubeVideoId?.trim()?.takeIf { it.isNotEmpty() } ?: "$name|$artists"

    /**
     * Fila de *liked* que corresponde a una canción, o `null` si no está.
     *
     * La identidad es el `youtubeVideoId`; si la canción llega sin id, se recurre
     * a nombre+artista con [fallbackDedupeKey], que es la única clave disponible y
     * la misma que usa la fusión. Sin ese recurso, un `""` no encontraba nunca la
     * fila guardada y cada intento de quitar un favorito acababa **añadiendo** otra
     * fila (B1).
     *
     * [remoteTrackId] se mantiene por compatibilidad con las llamadas de `:app`; la
     * identidad no lo usa.
     */
    @Suppress("UNUSED_PARAMETER")
    fun <T : TrackRow> likedTrackOf(
        tracks: List<T>,
        name: String,
        artists: String,
        remoteTrackId: String,
        youtubeVideoId: String?,
    ): T? {
        if (!youtubeVideoId.isNullOrBlank()) {
            tracks.firstOrNull { it.youtubeVideoId == youtubeVideoId }?.let { return it }
        }
        val key = fallbackDedupeKey(name, artists)
        return tracks.firstOrNull { fallbackDedupeKey(it) == key }
    }
}
