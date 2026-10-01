package com.plyr.ui.components

/**
 * Decide qué URL se comparte.
 *
 * El bug (B52): la URL la construía **la pantalla** y se pasaba ya montada en
 * `ShareableItem.shareUrl`, mientras el id bueno (`youtubeVideoId`) viajaba en
 * `youtubeId` y se descartaba por precedencia (`shareUrl ?: when { … }`). Como
 * las pistas se leen de la base de datos, `AppTrack.id` es
 * `TrackEntity.remoteTrackId`, que en lo importado de Spotify es
 * `spotify_<hashTítulo>_<hashArtistas>_<índice>`: el resultado era
 * `youtube.com/watch?v=spotify_1234567_-987654_3`, un enlace que no existe.
 *
 * Aquí la regla es que **gana el id real de YouTube**, y una URL ya montada solo
 * se usa cuando no hay id. Es lógica pura a propósito: se testea sin Android.
 *
 * **Ojo, esto no arregla B53** (compartir una lista). Para las playlists el tipo
 * de URL se sigue deduciendo por prefijos del id, porque la app no guarda de qué
 * servicio viene la lista; eso requiere persistir el origen.
 */
object ShareUrlPolicy {

    const val APP_DOWNLOAD_URL = "https://github.com/josemri/plyr/releases/download/latest/plyr.apk"

    private const val YOUTUBE_WATCH = "https://www.youtube.com/watch?v="
    private const val YOUTUBE_PLAYLIST = "https://www.youtube.com/playlist?list="
    private const val SPOTIFY_PLAYLIST = "https://open.spotify.com/playlist/"

    /**
     * @param type qué se comparte: decide la prioridad.
     * @param shareUrl URL ya montada, si quien llama la tenía.
     * @param youtubeId id real de YouTube (vídeo o lista), si se conoce.
     * @param playlistOrigin origen de la lista, para no adivinar el tipo de URL
     *   por prefijos (B53).
     * @return la URL a compartir, o `null` si no hay nada que compartir.
     */
    fun resolve(
        type: ShareType,
        shareUrl: String?,
        youtubeId: String?,
        playlistOrigin: PlaylistOrigin = PlaylistOrigin.UNKNOWN,
    ): String? {
        val id = youtubeId?.trim().orEmpty()
        // Una URL de solo espacios no es una URL: devolverla tal cual acabaría
        // en un QR y un tag NFC con basura en vez de "no hay nada que compartir".
        val fallback = shareUrl?.trim()?.takeIf { it.isNotEmpty() }

        return when (type) {
            // Compartir la app es el único caso donde el destino no es una
            // canción: la URL de la descarga es siempre la correcta.
            ShareType.APP -> fallback ?: APP_DOWNLOAD_URL

            ShareType.TRACK ->
                // El id de YouTube manda. Una URL montada solo entra si no hay
                // id, y entonces es que quien la construyó sí tenía un id real.
                if (id.isNotEmpty()) watchUrl(id) else fallback

            ShareType.PLAYLIST -> playlistUrl(id, playlistOrigin)
        }
    }

    /**
     * B53: el tipo de URL sale del origen conocido, no de los prefijos del id.
     *
     * Antes, si el id no empezaba por `PL`/`UU`/`FL`/`RD` se asumía que era un
     * vídeo, y una lista importada de Spotify se compartía como
     * `youtube.com/watch?v=<idSpotify>`. Con origen desconocido no se devuelve
     * nada: es preferible no compartir a compartir un enlace que no abre.
     */
    private fun playlistUrl(id: String, origin: PlaylistOrigin): String? = when (origin) {
        PlaylistOrigin.YOUTUBE -> if (id.isNotEmpty()) YOUTUBE_PLAYLIST + id else null
        PlaylistOrigin.SPOTIFY -> if (id.isNotEmpty()) SPOTIFY_PLAYLIST + id else null
        PlaylistOrigin.UNKNOWN -> null
    }

    private fun watchUrl(id: String): String = YOUTUBE_WATCH + id
}
