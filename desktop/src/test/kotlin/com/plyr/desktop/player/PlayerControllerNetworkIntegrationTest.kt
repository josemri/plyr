package com.plyr.desktop.player

import com.plyr.network.YouTubeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Reproduce el gesto real de la app: pulsar una canción de YouTube y esperar a
 * que suene.
 *
 * Los otros tests cubren por separado las piezas (resolución de URL sin red
 * simulada, VLC con un fichero local, VLC con una URL real suelta), pero no el
 * camino completo pulsar → resolver por red → VLC. Aquí sí.
 *
 * No cubre el ciclo de vida de Compose (eso lo prueba
 * `AudioListScreenTest.laRecomposicionNoRecreaElControladorDelReproductor`):
 * este test construye el controlador directamente, sin la pantalla.
 *
 * Necesita red y `libvlc`; sin cualquiera de los dos se omite.
 */
class PlayerControllerNetworkIntegrationTest {

    @Test
    fun pulsarUnaCancionDeYouTubeLlegaAReproducir() {
        val dir = VlcEnvironment.findLibraryDir()
        assumeTrue("libvlc no disponible: se omite", dir != null)
        assumeTrue("sin conexión a YouTube: se omite", canReachYoutube())

        // "Me at the zoo": vídeo estable, con audio.
        val videoId = "jNQXAC9IVRw"
        YouTubeManager.clearCache()
        val resolved = kotlinx.coroutines.runBlocking {
            runCatching { YouTubeManager.getAudioUrl(videoId) }.getOrNull()
        }
        assumeTrue("YouTube no devolvió audio (bloqueo): se omite", resolved != null)

        val player = VlcAudioPlayer(libraryDir = dir)
        val scope = CoroutineScope(Dispatchers.Default)
        val controller = PlayerController(
            scope = scope,
            openPlayer = { player },
            // Resolutor real: resuelve el vídeo por red igual que la app.
            sourceResolver = YouTubeTrackSourceResolver(),
        )

        try {
            controller.play(
                listOf(PlayerTrack(id = videoId, title = "Me at the zoo", artist = "jawed", videoId = videoId)),
                0,
            )

            val started = waitUntil(timeoutMs = 30_000) { controller.state.value.isPlaying }

            assertTrue(
                "La canción de YouTube no llegó a sonar; estado=${controller.state.value}",
                started,
            )
            assertTrue(
                "Se quedó cargando tras arrancar: ${controller.state.value}",
                !controller.state.value.isLoading,
            )
        } finally {
            controller.release()
        }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(200)
        }
        return condition()
    }

    private fun canReachYoutube(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("www.youtube.com", 443), 4_000) }
        true
    } catch (_: Exception) {
        false
    }
}
