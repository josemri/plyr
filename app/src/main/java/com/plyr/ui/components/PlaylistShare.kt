package com.plyr.ui.components

import com.plyr.database.PlaylistSource

/**
 * De dónde viene una lista guardada en la app, para poder compartirla.
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

    fun classify(
        remoteId: String?,
        description: String?,
        source: PlaylistSource? = null,
        sourceId: String? = null,
    ): PlaylistOrigin {
        val id = remoteId?.trim().orEmpty()
        if (id.isEmpty()) return PlaylistOrigin.UNKNOWN
        val stored = classifyFromStoredSource(source, id, sourceId)
        if (stored != null) return stored
        if (isLiked(id) || isLocalCreated(id)) return PlaylistOrigin.UNKNOWN
        if (isYoutubePlaylistId(id)) return PlaylistOrigin.YOUTUBE
        val inner = extractInner(id)
        return if (isMarkedAsSpotify(description) && isValidSpotifyId(inner)) {
            PlaylistOrigin.SPOTIFY
        } else {
            PlaylistOrigin.UNKNOWN
        }
    }

    private fun classifyFromStoredSource(
        source: PlaylistSource?,
        id: String,
        sourceId: String?,
    ): PlaylistOrigin? = when (source) {
        PlaylistSource.SPOTIFY -> {
            val sid = (sourceId ?: extractInner(id)).trim()
            if (isValidSpotifyId(sid)) PlaylistOrigin.SPOTIFY else PlaylistOrigin.UNKNOWN
        }
        PlaylistSource.YOUTUBE -> PlaylistOrigin.YOUTUBE
        PlaylistSource.LOCAL -> PlaylistOrigin.UNKNOWN
        PlaylistSource.UNKNOWN -> null
        null -> null
    }

    private fun isLiked(id: String): Boolean = id == LIKED_SONGS_ID

    private fun isLocalCreated(id: String): Boolean =
        id.startsWith(YOUTUBE_PREFIX + LOCAL_PREFIX)

    private fun isYoutubePlaylistId(id: String): Boolean {
        return id.startsWithAny(YOUTUBE_PLAYLIST_PREFIXES) ||
            extractInner(id).startsWithAny(YOUTUBE_PLAYLIST_PREFIXES)
    }

    private fun extractInner(id: String): String = id.removePrefix(YOUTUBE_PREFIX)

    private fun isMarkedAsSpotify(description: String?): Boolean =
        description?.trim().orEmpty().equals(SPOTIFY_MARKER, ignoreCase = true)

    private fun isValidSpotifyId(sid: String): Boolean {
        if (sid.isEmpty()) return false
        if (sid.length != SPOTIFY_ID_LENGTH) return false
        return sid.all { it.isLetterOrDigit() }
    }

    fun isShareable(
        remoteId: String?,
        description: String?,
        source: PlaylistSource? = null,
        sourceId: String? = null,
    ): Boolean = classify(remoteId, description, source, sourceId) != PlaylistOrigin.UNKNOWN

    private fun String.startsWithAny(prefixes: List<String>): Boolean =
        prefixes.any { startsWith(it) }
}
