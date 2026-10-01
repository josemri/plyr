package com.plyr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la clasificación del origen de una lista (B53).
 *
 * Cubren una fila por cada caso de la tabla del informe:
 *
 * | Lista                  | `remoteId`             | Compartir          |
 * |------------------------|------------------------|--------------------|
 * | Playlist de YouTube    | `youtube_PLxxxx`       | `playlist?list=`   |
 * | Importada de Spotify   | `youtube_<id22>`       | URL de Spotify     |
 * | Favoritos               | `liked_songs`          | no                 |
 * | Creada en la app       | `youtube_yt_<ts>`      | no                 |
 */
class PlaylistShareTest {

    private val spotifyId = "37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun playlistDeYoutube_seComparteConLaUrlDeYoutube() {
        assertEquals(PlaylistOrigin.YOUTUBE, PlaylistShare.classify("youtube_PLabc123", null))
        assertEquals(
            PlaylistOrigin.YOUTUBE,
            PlaylistShare.classify("youtube_UUabc123", "Imported from Spotify"),
        )
        // Los cuatro prefijos de lista que la app ya reconocía.
        assertEquals(PlaylistOrigin.YOUTUBE, PlaylistShare.classify("youtube_FLabc123", null))
        assertEquals(PlaylistOrigin.YOUTUBE, PlaylistShare.classify("youtube_RDabc123", null))
    }

    @Test
    fun playlistImportadaDeSpotify_seComparteConLaUrlDeSpotify() {
        assertEquals(
            PlaylistOrigin.SPOTIFY,
            PlaylistShare.classify("youtube_$spotifyId", "Imported from Spotify"),
        )
    }

    @Test
    fun favoritos_noSeComparte() {
        // Es lo que el usuario más comparte y lo que peor salía: un QR a
        // youtube.com/watch?v=liked_songs.
        assertEquals(PlaylistOrigin.UNKNOWN, PlaylistShare.classify("liked_songs", null))
        assertEquals(PlaylistOrigin.UNKNOWN, PlaylistShare.classify("liked_songs", "Imported from Spotify"))
    }

    @Test
    fun creadaEnLaApp_noSeComparte() {
        // `yt_<timestamp>` es un id interno, no un id de ningún servicio.
        assertEquals(PlaylistOrigin.UNKNOWN, PlaylistShare.classify("youtube_yt_1759000000000", null))
        assertEquals(
            PlaylistOrigin.UNKNOWN,
            PlaylistShare.classify("youtube_yt_1759000000000", "Imported from Spotify"),
        )
    }

    @Test
    fun idVacioOspaceado_noSeComparte() {
        assertEquals(PlaylistOrigin.UNKNOWN, PlaylistShare.classify(null, "Imported from Spotify"))
        assertEquals(PlaylistOrigin.UNKNOWN, PlaylistShare.classify("", null))
        assertEquals(PlaylistOrigin.UNKNOWN, PlaylistShare.classify("   ", "Imported from Spotify"))
    }

    /**
     * La description es texto que el usuario puede escribir, así que no basta
     * para dar por buena una lista de Spotify: el id tiene que tener forma de id
     * de playlist de Spotify.
     */
    @Test
    fun descriptionMarcadaPeroElIdNoEsDeSpotify_noSeComparte() {
        assertEquals(
            PlaylistOrigin.UNKNOWN,
            PlaylistShare.classify("youtube_corto", "Imported from Spotify"),
        )
        assertEquals(
            PlaylistOrigin.UNKNOWN,
            PlaylistShare.classify("youtube_${spotifyId}x", "Imported from Spotify"),
        )
        assertEquals(
            PlaylistOrigin.UNKNOWN,
            PlaylistShare.classify("youtube_${spotifyId.replace('i', '/')}", "Imported from Spotify"),
        )
    }

    @Test
    fun laDescriptionMarcaNoDistingueMayusculas() {
        assertEquals(
            PlaylistOrigin.SPOTIFY,
            PlaylistShare.classify("youtube_$spotifyId", "  imported FROM spotify  "),
        )
    }

    @Test
    fun isShareable_coincideConLaClasificacion() {
        assertTrue(PlaylistShare.isShareable("youtube_PLabc123", null))
        assertTrue(PlaylistShare.isShareable("youtube_$spotifyId", "Imported from Spotify"))

        assertFalse(PlaylistShare.isShareable("liked_songs", null))
        assertFalse(PlaylistShare.isShareable("youtube_yt_1759000000000", null))
        assertFalse(PlaylistShare.isShareable(null, null))
    }
}
