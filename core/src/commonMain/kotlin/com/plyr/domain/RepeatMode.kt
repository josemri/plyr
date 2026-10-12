package com.plyr.domain

/**
 * Modos de repetición de la cola, compartidos por todos los targets.
 *
 * Viven en `:core` para que la lógica pura (p. ej. `QueueIndex` y `QueueEngine`)
 * no dependa de `Config`, que en Android se apoya en `SharedPreferences`.
 * `Config` los reexpone como alias para no romper a quien ya los usaba.
 */
object RepeatMode {
    /** Sin repetición: la cola termina en la última canción. */
    const val OFF = "off"

    /** Repetir la canción actual indefinidamente. */
    const val ONE = "one"

    /** Repetir la cola entera en bucle. */
    const val ALL = "all"
}
