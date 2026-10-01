package com.plyr.ui.components

/**
 * De dónde viene una lista guardada en la app, para poder compartirla.
 *
 * El bug (B53): el diálogo recibía el `remoteId` pelado y decidía el tipo de URL
 * por prefijos, así que una lista importada de Spotify (guardada como
 * `youtube_<idSpotify>`) se compartía como `youtube.com/watch?v=<idSpotify>`, y
 * los favoritos (`liked_songs`) o una lista creada en la app
 * (`youtube_yt_<timestamp>`) se compartían como si fueran un vídeo.
 *
 * **Aviso: aquí el origen se deduce, no se guarda.** `PlaylistEntity` no tiene
 * columna de origen, y lo único que separa lo importado de Spotify es la
 * description `"Imported from Spotify"` (`SpotifyImporter.kt:171`). La solución
 * seria es persistir `source`/`sourceId` con su migración de Room; mientras no
 * sea así, el orden de las comprobaciones está puesto para que **una heurística
 * dudosa nunca produzca una URL inventada**: los identificadores explícitos
 * mandan sobre la description, y lo que no se reconoce con certeza cae en
 * [UNKNOWN], que no se comparte.
 */
enum class PlaylistOrigin {
    /** Lista real de YouTube: `PL…`, `UU…`, `FL…`, `RD…`. */
    YOUTUBE,

    /** Playlist importada de Spotify: se comparte con la URL de Spotify. */
    SPOTIFY,

    /** Sin origen identificable. No hay nada que compartir. */
    UNKNOWN,
}

object PlaylistShare {

    /** Prefijo con el que se guardan las listas de YouTube y las creadas en la app. */
    private const val YOUTUBE_PREFIX = "youtube_"

    /** Las creadas en la app: `youtube_yt_<timestamp>`, un id interno. */
    private const val LOCAL_PREFIX = "yt_"

    /** Los favoritos nunca se borran y no son una lista de ningún servicio. */
    const val LIKED_SONGS_ID = "liked_songs"

    private const val SPOTIFY_MARKER = "Imported from Spotify"

    /** Un id de playlist de Spotify son 22 caracteres base62. */
    private const val SPOTIFY_ID_LENGTH = 22

    private val YOUTUBE_PLAYLIST_PREFIXES = listOf("PL", "UU", "FL", "RD")

    /**
     * @param remoteId clave primaria de `PlaylistEntity`.
     * @param description la description guardada, si la hay.
     */
    fun classify(remoteId: String?, description: String?): PlaylistOrigin {
        val id = remoteId?.trim().orEmpty()
        if (id.isEmpty()) return PlaylistOrigin.UNKNOWN

        // Identificadores explícitos: no hay nada que compartir y no se deducen
        // por description.
        if (id == LIKED_SONGS_ID) return PlaylistOrigin.UNKNOWN
        if (id.startsWith(YOUTUBE_PREFIX + LOCAL_PREFIX)) return PlaylistOrigin.UNKNOWN

        // El prefijo de id de YouTube es la señal más fuerte que hay: si el id
        // tiene forma de lista de YouTube, es una lista de YouTube, diga lo que
        // diga la description.
        if (id.startsWithAny(YOUTUBE_PLAYLIST_PREFIXES) ||
            id.removePrefix(YOUTUBE_PREFIX).startsWithAny(YOUTUBE_PLAYLIST_PREFIXES)) {
            return PlaylistOrigin.YOUTUBE
        }

        val inner = id.removePrefix(YOUTUBE_PREFIX)
        val markedAsSpotify = description?.trim().orEmpty().equals(SPOTIFY_MARKER, ignoreCase = true)
        if (markedAsSpotify && inner.length == SPOTIFY_ID_LENGTH && inner.all { it.isLetterOrDigit() }) {
            return PlaylistOrigin.SPOTIFY
        }

        // Lista de YouTube guardada con un id que no lleva prefijo reconocible: no
        // se sabe qué URL abrir, así que no se inventa una.
        return PlaylistOrigin.UNKNOWN
    }

    /** Si esta lista tiene una URL de verdad detrás. */
    fun isShareable(remoteId: String?, description: String?): Boolean =
        classify(remoteId, description) != PlaylistOrigin.UNKNOWN

    private fun String.startsWithAny(prefixes: List<String>): Boolean =
        prefixes.any { startsWith(it) }
}
