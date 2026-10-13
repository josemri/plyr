package com.plyr.utils

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la importación de Spotify compartida (M11): parseo de URLs, del HTML
 * embebido y construcción del plan de importación con una búsqueda falsa, sin
 * red ni base de datos.
 */
class SpotifyImportTest {

    // --- extractPlaylistId ---

    @Test
    fun extractPlaylistId_urlCompletaConQuery() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImport.extractPlaylistId(
                "https://open.spotify.com/playlist/6Ue8iXcrM1F1NRoHsi7Vli?si=abc123",
            ),
        )
    }

    @Test
    fun extractPlaylistId_urlSinQuery() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImport.extractPlaylistId("https://open.spotify.com/playlist/6Ue8iXcrM1F1NRoHsi7Vli"),
        )
    }

    @Test
    fun extractPlaylistId_uriDeSpotify() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImport.extractPlaylistId("spotify:playlist:6Ue8iXcrM1F1NRoHsi7Vli"),
        )
    }

    @Test
    fun extractPlaylistId_idSueltoDe22Caracteres() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImport.extractPlaylistId("6Ue8iXcrM1F1NRoHsi7Vli"),
        )
    }

    @Test
    fun extractPlaylistId_conEspaciosAlrededor() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImport.extractPlaylistId("  6Ue8iXcrM1F1NRoHsi7Vli  "),
        )
    }

    @Test
    fun extractPlaylistId_invalido_devuelveNull() {
        assertNull(SpotifyImport.extractPlaylistId(""))
        assertNull(SpotifyImport.extractPlaylistId("https://example.com/playlist/abc"))
        assertNull(SpotifyImport.extractPlaylistId("corto"))
        assertNull(SpotifyImport.extractPlaylistId("6Ue8iXcrM1F1NRoHsi7Vlix"))
    }

    // --- identidad ---

    @Test
    fun dbPlaylistId_usaElPrefijoInterno() {
        assertEquals("youtube_abc123", SpotifyImport.dbPlaylistId("abc123"))
    }

    @Test
    fun remoteTrackId_tieneElFormatoDeAndroid() {
        assertEquals(
            "spotify_${"Uno".hashCode()}_${listOf("A", "B").hashCode()}_0",
            SpotifyImport.remoteTrackId("Uno", listOf("A", "B"), 0),
        )
    }

    // --- parseEmbedHtml ---

    private val sampleHtml = """
        <!DOCTYPE html><html><head>
        <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Mi Lista","coverArt":{"sources":[{"url":"https://img/cover.jpg"}]},"trackList":[{"title":"Uno","subtitle":"Artista A, Artista B","duration":1000},{"title":"Dos","subtitle":"","duration":2000}]}}}}}}</script>
        </head></html>
    """.trimIndent()

    @Test
    fun parseEmbedHtml_leeNombrePortadaYPistas() {
        val playlist = SpotifyImport.parseEmbedHtml("id22", sampleHtml).getOrThrow()

        assertEquals("id22", playlist.id)
        assertEquals("Mi Lista", playlist.name)
        assertEquals("https://img/cover.jpg", playlist.imageUrl)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Uno", playlist.tracks[0].name)
        assertEquals(listOf("Artista A", "Artista B"), playlist.tracks[0].artists)
        assertEquals(1000L, playlist.tracks[0].durationMs)
    }

    @Test
    fun parseEmbedHtml_pistaSinSubtitulo_usaUnknownArtist() {
        val playlist = SpotifyImport.parseEmbedHtml("id22", sampleHtml).getOrThrow()

        assertEquals(listOf("Unknown Artist"), playlist.tracks[1].artists)
    }

    @Test
    fun parseEmbedHtml_sinScript_falla() {
        val result = SpotifyImport.parseEmbedHtml("id22", "<html>sin datos</html>")

        assertTrue(result.isFailure)
    }

    @Test
    fun parseEmbedHtml_sinEntity_falla() {
        val html = """
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{}}}}}</script>
        """.trimIndent()

        assertTrue(SpotifyImport.parseEmbedHtml("id22", html).isFailure)
    }

    // --- buildPlan ---

    @Test
    fun buildPlan_buscaCadaPistaYCuentaCoincidencias() = runBlocking {
        val playlist = SpotifyImport.SpotifyPlaylist(
            id = "abc1234567890123456789",
            name = "Mi Lista",
            imageUrl = null,
            tracks = listOf(
                SpotifyImport.SpotifyTrack("Uno", listOf("A", "B"), 1000),
                SpotifyImport.SpotifyTrack("Dos", listOf("C"), 2000),
            ),
        )
        val queries = mutableListOf<String>()

        val plan = SpotifyImport.buildPlan(
            playlist = playlist,
            searchTrack = { query ->
                queries.add(query)
                if (query.startsWith("Uno")) "vid1" else null
            },
            searchDelayMs = 0,
        )

        assertEquals(2, plan.tracks.size)
        assertEquals(2, plan.totalCount)
        assertEquals(1, plan.matchedCount)
        assertEquals("youtube_abc1234567890123456789", plan.dbPlaylistId)
        assertEquals("Imported from Spotify", plan.description)
        assertEquals("vid1", plan.tracks[0].youtubeVideoId)
        assertNull(plan.tracks[1].youtubeVideoId)
        assertEquals("A, B", plan.tracks[0].artists)
        assertEquals(0, plan.tracks[0].position)
        assertEquals(1, plan.tracks[1].position)
        assertEquals(SpotifyImport.remoteTrackId("Uno", listOf("A", "B"), 0), plan.tracks[0].remoteTrackId)
        assertEquals(listOf("Uno - A, B", "Dos - C"), queries)
        assertEquals(SpotifyImport.importedMessage("Mi Lista", 1, 2), plan.message)
    }

    @Test
    fun buildPlan_notificaElProgreso() = runBlocking {
        val playlist = SpotifyImport.SpotifyPlaylist(
            id = "id22",
            name = "Lista",
            imageUrl = null,
            tracks = listOf(
                SpotifyImport.SpotifyTrack("Uno", listOf("A"), 0),
                SpotifyImport.SpotifyTrack("Dos", listOf("B"), 0),
            ),
        )
        val progress = mutableListOf<Pair<Int, Int>>()

        SpotifyImport.buildPlan(
            playlist = playlist,
            searchTrack = { null },
            onProgress = { current, total, _ -> progress.add(current to total) },
            searchDelayMs = 0,
        )

        assertEquals(listOf(1 to 2, 2 to 2), progress)
    }
}
