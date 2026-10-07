package com.plyr.service

/**
 * Qué evento del reproductor obliga a repintar la notificación.
 *
 * El bug (B50): `MusicService` solo escuchaba `onMediaItemTransition`, así que la
 * notificación quedaba congelada entre pistas. El recorte de la ventana
 * (`PlayerViewModel.trimWindow` → `removeMediaItems`) y el relleno
 * (`growWindow` → `addMediaItems`) solo disparan `onTimelineChanged`, de modo que
 * tras dos skips seguidos el botón de siguiente desaparecía y **no volvía** hasta
 * la siguiente transición. Con la app cerrada en la notificación, eso era
 * "no se puede avanzar".
 *
 * Aquí está el listener que faltaba y la regla de cuándo repintar. Es lógica
 * pura a propósito: se testea sin Android.
 */
enum class NotificationEvent {
    /** Cambió la pista en curso: título, artista y posición. */
    ITEM_TRANSITION,

    /**
     * Cambió la lista de items del reproductor (recorte o relleno de la ventana).
     * El título no cambia, pero la notificación hay que repintarla igual (B50).
     *
     * Ojo con lo que esto **no** hace: el sistema pinta los controles de
     * reproducción (el `>>`) desde el `PlaybackState` de la sesión, y la sesión
     * solo lo refresca al cambiar los comandos del reproductor o al transicionar
     * de item, no con `onTimelineChanged`. Ver `QueueAwarePlayer` (B57).
     */
    TIMELINE_CHANGED,
}

/** Qué hacer con la notificación ante un evento. */
enum class NotificationAction {
    /** Reconstruir y volver a pintar. */
    REBUILD,

    /** No hay nada sonando: quitar la notificación y dejar la sesión viva. */
    REMOVE,

    /** Nada que cambió de verdad: no tocar la notificación. */
    SKIP,
}

object NotificationRefreshPolicy {

    /**
     * @param previous estado visible en la última notificación pintada.
     * @param next estado visible ahora mismo.
     */
    fun decide(
        event: NotificationEvent,
        previous: PlaybackNotificationState?,
        next: PlaybackNotificationState,
    ): NotificationAction = when {
        // Sin item no hay notificación que mantener (B55, B56).
        !next.showMediaStyle -> NotificationAction.REMOVE

        // Todavía no se ha pintado ninguna, o el item cambió de verdad.
        previous == null || previous != next -> NotificationAction.REBUILD

        // El item en curso es el mismo, pero la ventana ha cambiado: hay que
        // repintar igual, porque la notificación lleva el `MediaStyle` de la
        // sesión y el relleno de la ventana puede dejarla desactualizada.
        // **Esta es la clave de B50**: si aquí se comparara solo el estado, el
        // `addMediaItems` del relleno no repintaría nada.
        //
        // Esto devuelve la notificación, no el `>>` de los controles del
        // sistema: ese lo decide el `PlaybackState` de la sesión (B57).
        event == NotificationEvent.TIMELINE_CHANGED -> NotificationAction.REBUILD

        // Mismo evento y mismo estado: no hay nada que hacer.
        else -> NotificationAction.SKIP
    }
}
