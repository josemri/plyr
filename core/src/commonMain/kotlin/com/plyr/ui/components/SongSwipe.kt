package com.plyr.ui.components

/**
 * Decisión pura del gesto de swipe de una fila de canción: a partir del
 * desplazamiento acumulado y del umbral en píxeles, qué acción debe ejecutarse.
 *
 * Vivía dentro de las lambdas de `pointerInput` en `SongListItem`; extraerla
 * permite testear en JVM el umbral, la dirección y el caso neutro sin estado.
 */
fun swipeActionFor(offset: Float, thresholdPx: Float): SwipeAction = when {
    offset > thresholdPx -> SwipeAction.RIGHT
    offset < -thresholdPx -> SwipeAction.LEFT
    else -> SwipeAction.NONE
}

/** Acción que corresponde a un desplazamiento de swipe. */
enum class SwipeAction {
    /** Desplazamiento positivo: acción derecha (por defecto, *liked*). */
    RIGHT,

    /** Desplazamiento negativo: acción izquierda (por defecto, *cola*). */
    LEFT,

    /** Dentro del umbral: volver al centro sin ejecutar nada. */
    NONE
}