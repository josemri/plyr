package com.plyr.desktop.player

import com.plyr.player.AudioTrack
import com.plyr.player.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Test de humo de [VlcAudioPlayer].
 *
 * Solo se ejecuta si hay `libvlc` disponible (en esta máquina o forzando
 * `PLYR_VLC_DIR`); en caso contrario se omite con [assumeTrue], para no romper
 * la build en entornos sin VLC. Comprueba que el reproductor se construye y que
 * la ventana de pistas (alta/baja de items) se lleva igual que la de Android.
 */
class VlcAudioPlayerSmokeTest {

    private fun track(id: String) = AudioTrack(mediaId = id, title = id, artist = "test", uri = "file:///$id")

    @Test
    fun construyeLiberaYGestionaLaVentana() {
        val dir = VlcEnvironment.findLibraryDir()
        assumeTrue("libvlc no disponible: se omite el test de humo", dir != null)

        val player = VlcAudioPlayer(libraryDir = dir)
        try {
            assertEquals(PlaybackState.IDLE, player.playbackState)
            assertFalse(player.isPlaying)
            assertEquals(0, player.mediaItemCount)

            player.setMediaItems(listOf(track("a"), track("b"), track("c")), startIndex = 1)
            assertEquals(3, player.mediaItemCount)
            assertEquals(1, player.currentMediaItemIndex)

            player.addMediaItems(listOf(track("d")))
            assertEquals(4, player.mediaItemCount)

            // Quitar items por delante del actual desplaza el índice.
            player.removeMediaItems(0, 1)
            assertEquals(3, player.mediaItemCount)
            assertEquals(0, player.currentMediaItemIndex)

            player.clearMediaItems()
            assertEquals(0, player.mediaItemCount)
        } finally {
            player.release()
        }
    }
}
