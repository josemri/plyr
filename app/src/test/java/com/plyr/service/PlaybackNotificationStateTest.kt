package com.plyr.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del estado visible de la notificación de reproducción.
 *
 * Comprueban la regla que arregla B55 y B56: la notificación no afirma lo que no
 * sabe. Sin item en curso no puede decir "Reproducendo", ni quedar `ongoing` para
 * siempre, ni montar un `MediaStyle` que no tiene detrás items donde pulsar.
 */
class PlaybackNotificationStateTest {

    @Test
    fun conItem_muestraLaCancionYElArtista() {
        val state = PlaybackNotificationState.of("Plyr", "River", "Radiohead")

        assertEquals("River", state.title)
        assertEquals("Radiohead", state.text)
        assertTrue(state.ongoing)
        assertTrue(state.showMediaStyle)
    }

    @Test
    fun sinItem_noDiceReproduciendo() {
        // El síntoma reportado: al abrir la app sin música (B56) y al terminar la
        // cola (B55) la notificación ponía "Plyr / Reproduciendo".
        val state = PlaybackNotificationState.of("Plyr", null, null)

        assertEquals("Plyr", state.title)
        assertFalse(state.text == "Reproduciendo")
    }

    @Test
    fun sinItem_noEsOngoing() {
        // Con `setOngoing(true)` la notificación no se puede quitar y se quedaba
        // puesta indefinidamente.
        assertFalse(PlaybackNotificationState.of("Plyr", null, null).ongoing)
        assertFalse(PlaybackNotificationState.of("Plyr", "", null).ongoing)
        assertFalse(PlaybackNotificationState.of("Plyr", "   ", "  ").ongoing)
    }

    @Test
    fun sinItem_noMontaMediaStyle() {
        // Sin items en la sesión, el `MediaStyle` no puede offercer controles: era
        // lo que dejaba la notificación fantasma sin botones.
        assertFalse(PlaybackNotificationState.of("Plyr", null, null).showMediaStyle)
    }

    @Test
    fun tituloVacio_cuentaComoSinItem() {
        // Media3 puede devolver un `MediaItem` sin título: se trata como idle, no
        // como una canción con nombre vacío.
        val state = PlaybackNotificationState.of("Plyr", "  ", "Artista")

        assertEquals("Plyr", state.title)
        assertFalse(state.ongoing)
        assertFalse(state.showMediaStyle)
    }

    @Test
    fun sinArtista_noVuelveAReproduciendo() {
        // Con título pero sin artista el texto no puede caer en "Reproduciendo":
        // se usa el nombre de la app, que sí es cierto.
        val state = PlaybackNotificationState.of("Plyr", "River", null)

        assertEquals("River", state.title)
        assertEquals("Plyr", state.text)
        assertFalse(state.text == "Reproduciendo")
        assertTrue(state.ongoing)
    }

    @Test
    fun espaciosSeRecortan() {
        val state = PlaybackNotificationState.of("Plyr", "  River  ", "  Radiohead  ")

        assertEquals("River", state.title)
        assertEquals("Radiohead", state.text)
    }

    @Test
    fun idle_usaElNombreDeLaApp() {
        val state = PlaybackNotificationState.idle("Plyr")

        assertEquals("Plyr", state.title)
        assertEquals("Plyr", state.text)
        assertFalse(state.ongoing)
        assertFalse(state.showMediaStyle)
    }
}
