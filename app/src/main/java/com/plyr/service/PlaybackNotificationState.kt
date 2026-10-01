package com.plyr.service

/**
 * Qué debe mostrar la notificación de reproducción.
 *
 * La notificación se construía siempre con el item actual y, cuando no había
 * ninguno, caía en los valores por defecto: título `"Plyr"` y texto
 * `"Reproduciendo"`, con `setOngoing(true)` y `MediaStyle` sobre una sesión sin
 * items. Eso producía dos síntomas:
 *
 *  - **B55**: al terminar la cola (`stopAtQueueEnd` → `player.stop()` +
 *    `clearMediaItems()`) quedaba una notificación permanente que decía estar
 *    reproduciendo, sin canción y sin controles.
 *  - **B56**: la notificación provisional que `startForegroundService` obliga a
 *    publicar al abrir la app se quedaba con "Plyr / Reproduciendo" si el
 *    usuario no llegaba a poner ninguna canción, porque solo se sustituye cuando
 *    el `PlayerViewModel` invoca `onMediaSessionUpdate` al empezar a sonar algo.
 *
 * La regla que lo arregla es que **la notificación no afirma lo que no sabe**:
 * sin item no hay canción que mostrar, así que no se pone `MediaStyle` (no hay
 * controles que offercer), no se marca `ongoing` (el usuario puede quitarla) y el
 * texto no dice "Reproduciendo".
 *
 * Es lógica pura a propósito: se testea sin Android.
 */
data class PlaybackNotificationState(
    val title: String,
    val text: String,
    val ongoing: Boolean,
    val showMediaStyle: Boolean,
) {
    companion object {
        /**
         * @param appName nombre de la app, para los casos sin item.
         * @param itemTitle título de la pista en curso, o `null`/vacío si no hay.
         * @param itemArtist artista de la pista en curso, o `null`/vacío si no hay.
         */
        fun of(appName: String, itemTitle: String?, itemArtist: String?): PlaybackNotificationState {
            val title = itemTitle?.trim().orEmpty()
            if (title.isEmpty()) return idle(appName)

            // Sin artista el texto caería en "Reproduciendo", que es justo lo que
            // este estado pretende no afirmar.
            val text = itemArtist?.trim().orEmpty().ifEmpty { appName }
            return PlaybackNotificationState(
                title = title,
                text = text,
                ongoing = true,
                showMediaStyle = true,
            )
        }

        /** Estado sin nada sonando: honrado, descartable y sin controles falsos. */
        fun idle(appName: String): PlaybackNotificationState =
            PlaybackNotificationState(
                title = appName,
                text = appName,
                ongoing = false,
                showMediaStyle = false,
            )
    }
}
