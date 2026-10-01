package com.plyr.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de la política de refresco de la notificación (B50).
 *
 * El bug: `MusicService` solo escuchaba `onMediaItemTransition`. El recorte y el
 * relleno de la ventana solo disparan `onTimelineChanged`, así que tras dos
 * skips seguidos el botón de siguiente desaparecía y no volvía hasta la siguiente
 * transición.
 */
class NotificationRefreshPolicyTest {

    private val appName = "Plyr"

    private val song = PlaybackNotificationState.of(appName, "River", "Joni Mitchell")

    private val anotherSong = PlaybackNotificationState.of(appName, "Blue", "Joni Mitchell")

    @Test
    fun primeraPintada_siempreRepinta() {
        // No hay estado previo con el que comparar: hay que pintar sí o sí.
        assertEquals(
            NotificationAction.REBUILD,
            NotificationRefreshPolicy.decide(
                NotificationEvent.ITEM_TRANSITION,
                previous = null,
                next = song,
            ),
        )
    }

    @Test
    fun cambioDePista_repinta() {
        assertEquals(
            NotificationAction.REBUILD,
            NotificationRefreshPolicy.decide(
                NotificationEvent.ITEM_TRANSITION,
                previous = song,
                next = anotherSong,
            ),
        )
    }

    /** Este es el corazón de B50: el item no cambia, pero la ventana sí. */
    @Test
    fun cambioDeVentana_repintaAunqueElItemSeaElMismo() {
        assertEquals(
            NotificationAction.REBUILD,
            NotificationRefreshPolicy.decide(
                NotificationEvent.TIMELINE_CHANGED,
                previous = song,
                next = song,
            ),
        )
    }

    @Test
    fun nadaCambiado_noRepinta() {
        // Mismo evento y mismo estado: repintar sería un `startForeground` inútil
        // en cada callback.
        assertEquals(
            NotificationAction.SKIP,
            NotificationRefreshPolicy.decide(
                NotificationEvent.ITEM_TRANSITION,
                previous = song,
                next = song.copy(),
            ),
        )
    }

    @Test
    fun sinItem_quitaLaNotificacion() {
        // B55/B56: sin nada sonando la notificación se retira en vez de quedarse
        // mintiendo, y da igual qué evento lo provocara.
        val idle = PlaybackNotificationState.idle(appName)

        for (event in NotificationEvent.values()) {
            assertEquals(
                event.name,
                NotificationAction.REMOVE,
                NotificationRefreshPolicy.decide(event, previous = song, next = idle),
            )
        }
    }

    @Test
    fun sinItemTrasQuitarLaNotificacion_sigueQuitando() {
        // El estado idle ya es el último pintado (el `clearMediaItems` de
        // `stopAtQueueEnd` puede disparar más de un evento): reintentar el
        // `stopForeground` es inocuo, pero no debe pintar una notificación.
        val idle = PlaybackNotificationState.idle(appName)

        assertEquals(
            NotificationAction.REMOVE,
            NotificationRefreshPolicy.decide(
                NotificationEvent.TIMELINE_CHANGED,
                previous = idle,
                next = idle.copy(),
            ),
        )
    }

    @Test
    fun itemQueVuelve_despuesDeRetirar_laNotificacionSeRePinta() {
        // Tras un `REMOVE` el estado previo es idle; cuando entra una pista el
        // estado cambia y tiene que volver a pintarse.
        val idle = PlaybackNotificationState.idle(appName)

        assertEquals(
            NotificationAction.REBUILD,
            NotificationRefreshPolicy.decide(
                NotificationEvent.ITEM_TRANSITION,
                previous = idle,
                next = song,
            ),
        )
    }
}
