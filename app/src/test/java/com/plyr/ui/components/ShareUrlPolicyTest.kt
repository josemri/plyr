package com.plyr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de la política de URL compartida (B52).
 *
 * El bug era de precedencia: `ShareDialog` cogía `shareUrl` (montada por la
 * pantalla con `AppTrack.id`, que es `remoteTrackId`) y descartaba el
 * `youtubeVideoId` correcto. En lo importado de Spotify eso=shareaba
 * `youtube.com/watch?v=spotify_<hash>_<hash>_<índice>`, que no existe.
 */
class ShareUrlPolicyTest {

    /** Id de Spotify tal y como lo guarda `SpotifyImporter`. */
    private val spotifyRemoteId = "spotify_1234567_-987654_3"

    private val videoId = "dQw4w9WgXcQ"

    @Test
    fun cancion_conIdDeYoutube_mandaElIdYNoLaUrlDeSpotify() {
        // Este es el caso exacto del bug: shareUrl venía con el hash de Spotify
        // y youtubeId con el vídeo real. Antes ganaba shareUrl.
        val url = ShareUrlPolicy.resolve(
            type = ShareType.TRACK,
            shareUrl = "https://www.youtube.com/watch?v=$spotifyRemoteId",
            youtubeId = videoId,
        )

        assertEquals("https://www.youtube.com/watch?v=$videoId", url)
    }

    @Test
    fun cancion_conIdDeYoutube_ignoraShareUrlPorCompleto() {
        val url = ShareUrlPolicy.resolve(
            type = ShareType.TRACK,
            shareUrl = "https://www.youtube.com/watch?v=$spotifyRemoteId",
            youtubeId = videoId,
        )

        // Ni el prefijo de Spotify ni el host equivocado se colan.
        assertEquals(false, url!!.contains("spotify_"))
    }

    @Test
    fun cancion_sinId_usaLaUrlQueVino() {
        // Si no hay id de YouTube, una URL previa (p. ej. de resultados de
        // búsqueda, donde sí es el vídeo real) sigue sirviendo.
        val url = ShareUrlPolicy.resolve(
            type = ShareType.TRACK,
            shareUrl = "https://www.youtube.com/watch?v=$videoId",
            youtubeId = null,
        )

        assertEquals("https://www.youtube.com/watch?v=$videoId", url)
    }

    @Test
    fun cancion_sinNada_devuelveNull() {
        // Es el caso de la cola con pistas sin coincidencia en YouTube: el
        // diálogo muestra el aviso de "no hay nada que compartir" (B54).
        assertNull(ShareUrlPolicy.resolve(ShareType.TRACK, shareUrl = null, youtubeId = null))
        assertNull(ShareUrlPolicy.resolve(ShareType.TRACK, shareUrl = "", youtubeId = ""))
        assertNull(ShareUrlPolicy.resolve(ShareType.TRACK, shareUrl = "  ", youtubeId = "  "))
    }

    @Test
    fun cancion_idSoloEspacios_cuentaComoAusente() {
        // Media3 o la base pueden devolver strings vacíos con espacios: si se
        // aceptaran, la URL sería "watch?v=" que tampoco existe.
        val url = ShareUrlPolicy.resolve(
            type = ShareType.TRACK,
            shareUrl = "https://www.youtube.com/watch?v=$videoId",
            youtubeId = "   ",
        )

        assertEquals("https://www.youtube.com/watch?v=$videoId", url)
    }

    @Test
    fun app_comparteElEnlaceDeDescarga() {
        assertEquals(
            ShareUrlPolicy.APP_DOWNLOAD_URL,
            ShareUrlPolicy.resolve(ShareType.APP, shareUrl = null, youtubeId = null),
        )
    }

    @Test
    fun app_ignoraQueLePasenUnId() {
        // Compartir la app no es compartir una canción: un id colado no debe
        // convertirla en un enlace de YouTube.
        assertEquals(
            ShareUrlPolicy.APP_DOWNLOAD_URL,
            ShareUrlPolicy.resolve(ShareType.APP, shareUrl = null, youtubeId = videoId),
        )
    }

    @Test
    fun lista_conPrefijoDeLista_armaPlaylistUrl() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLabcdef",
            ShareUrlPolicy.resolve(ShareType.PLAYLIST, shareUrl = null, youtubeId = "PLabcdef"),
        )
    }

    @Test
    fun lista_sinPrefijo_usaLaUrlQueVino() {
        // Contexto de B53, que NO se arregla aquí: sin origen persistido no hay
        // forma de saber si un id sin prefijo es de Spotify. La lista importada
        // sigue guardada con el prefijo `youtube_`, que no es de lista.
        assertEquals(
            "https://open.spotify.com/playlist/37i9dQ",
            ShareUrlPolicy.resolve(
                type = ShareType.PLAYLIST,
                shareUrl = "https://open.spotify.com/playlist/37i9dQ",
                youtubeId = "37i9dQZF1DX",
            ),
        )
    }
}
