package com.plyr.desktop.player

import com.plyr.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File

/**
 * Integración real de [PlayerController] sobre [VlcAudioPlayer] (M7).
 *
 * A diferencia de [PlayerControllerTest] (que usa [FakeAudioPlayer]), aquí se
 * arranca el motor de verdad y se reproduce un WAV local, sin red: así se
 * comprueba que el cableado controlador → VLC llega a `playing`/`READY` y que
 * `isLoading` se apaga. Se omite si no hay `libvlc`.
 */
class PlayerControllerVlcIntegrationTest {

    @Test
    fun reproduceUnFicheroLocalYDejaDeCargar() {
        val dir = VlcEnvironment.findLibraryDir()
        assumeTrue("libvlc no disponible: se omite la integración de M7", dir != null)

        val wav = File.createTempFile("plyr-m7-", ".wav").apply { writeSilentWav(this, seconds = 3) }
        val player = VlcAudioPlayer(libraryDir = dir)
        val scope = CoroutineScope(Dispatchers.Default)
        val controller = PlayerController(
            scope = scope,
            openPlayer = { player },
            sourceResolver = { _, _, _ -> ResolvedSource(videoId = "local", uri = "file://${wav.absolutePath}") },
        )

        try {
            controller.play(
                listOf(PlayerTrack(id = "a", title = "Local", artist = "test", videoId = "local")),
                0,
            )

            val started = waitUntil(timeoutMs = 15_000) { controller.state.value.isPlaying }

            assertTrue(
                "El controlador no llegó a 'playing' con VLC; estado=${controller.state.value}",
                started,
            )
            assertTrue(
                "El estado sigue marcado como cargando: ${controller.state.value}",
                !controller.state.value.isLoading,
            )
        } finally {
            controller.release()
            wav.delete()
        }
    }

    /** Espera activa hasta que [condition] se cumpla o venza [timeoutMs]. */
    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    /** Genera un WAV PCM 16 bits mono con silencio, suficiente para que VLC lo abra. */
    private fun writeSilentWav(file: File, seconds: Int, sampleRate: Int = 44_100) {
        val samples = sampleRate * seconds
        val dataSize = samples * 2
        DataOutputStream(file.outputStream().buffered()).use { out ->
            fun le32(v: Int) = intArrayOf(v and 0xFF, (v shr 8) and 0xFF, (v shr 16) and 0xFF, (v shr 24) and 0xFF)
                .forEach(out::write)
            fun le16(v: Int) = intArrayOf(v and 0xFF, (v shr 8) and 0xFF).forEach(out::write)

            out.writeBytes("RIFF"); le32(36 + dataSize); out.writeBytes("WAVE")
            out.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sampleRate)
            le32(sampleRate * 2); le16(2); le16(16)
            out.writeBytes("data"); le32(dataSize)
            repeat(samples) { le16(0) }
        }
    }
}
