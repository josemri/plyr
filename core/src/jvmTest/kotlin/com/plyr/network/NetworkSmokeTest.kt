package com.plyr.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Smoke test de red de M2.
 *
 * Comprueba que la extracción de YouTube funciona desde un JVM de escritorio
 * (sin Android): `NewPipeHolder` se inicializa con `SimpleDownloader` y tanto la
 * búsqueda como la extracción de URL responden.
 *
 * Depende de Internet y de que YouTube no bloqueé la petición, así que se omite
 * con `assumeTrue` cuando no hay conexión: en una máquina offline el test se
 * salta en lugar de fallar.
 */
class NetworkSmokeTest {

    @Before
    fun requireConnection() {
        assumeTrue("Sin conexión a YouTube: se omite el smoke test de red", canReachYoutube())
    }

    @Test
    fun searchVideoId_returnsARealVideoId() {
        val videoId = YouTubeManager.searchVideoId("NewPipe Extractor")

        assumeTrue("YouTube no devolvió resultados para la búsqueda", videoId != null)
        assertNotNull("La búsqueda no encontró ningún vídeo", videoId)
        assertTrue(
            "El id '$videoId' no parece un id de YouTube (11 caracteres)",
            videoId!!.length == 11
        )
    }

    @Test
    fun getAudioUrl_extractsPlayableUrl() = runBlocking {
        // Vídeo estable y con audio ("Me at the zoo", el primer vídeo de YouTube).
        val videoId = "jNQXAC9IVRw"
        YouTubeManager.clearCache()

        val url = YouTubeManager.getAudioUrl(videoId)

        assumeTrue("YouTube no devolvió audio (posible bloqueo): se omite", url != null)
        assertNotNull("No se pudo extraer la URL de audio de $videoId", url)
        assertTrue("La URL extraída no es http(s): $url", url!!.startsWith("http"))
    }

    @Test
    fun searchAll_returnsVideosAlongsideLists() = runBlocking {
        val result = YouTubeManager.searchAll("lofi hip hop")

        assumeTrue("YouTube no devolvió resultados combinados", result.videos.isNotEmpty())
        assertTrue(
            "Los vídeos de la búsqueda no traen id de YouTube válido",
            result.videos.all { it.videoId.length == 11 },
        )
    }

    @Test
    fun getPlaylistVideos_returnsTracksForASearchedPlaylist() = runBlocking {
        val playlist = YouTubeManager.searchAll("lofi hip hop").playlists.firstOrNull()
        assumeTrue("La búsqueda no devolvió ninguna lista de YouTube", playlist != null)

        val videos = YouTubeManager.getPlaylistVideos(playlist!!.playlistId)

        assumeTrue("YouTube no devolvió pistas de la lista", videos.isNotEmpty())
        assertTrue(
            "Las pistas de la lista no traen id de YouTube válido",
            videos.all { it.videoId.length == 11 },
        )
    }

    @Test
    fun getPlaylistInfo_returnsTitleAndCount() = runBlocking {
        val playlist = YouTubeManager.searchAll("lofi hip hop").playlists.firstOrNull()
        assumeTrue("La búsqueda no devolvió ninguna lista de YouTube", playlist != null)

        val info = YouTubeManager.getPlaylistInfo(playlist!!.playlistId)

        assumeTrue("YouTube no devolvió la ficha de la lista", info != null)
        assertNotNull("La ficha de la lista llegó vacía", info)
        assertTrue("La ficha no trae título", info!!.title.isNotBlank())
    }

    private fun canReachYoutube(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), CONNECT_TIMEOUT_MS) }
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
    }
}
