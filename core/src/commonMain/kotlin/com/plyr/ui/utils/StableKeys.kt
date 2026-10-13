package com.plyr.ui.utils

/**
 * Genera claves estables para listas perezosas a partir de una lista de ids que
 * pueden repetirse (la cola o una playlist pueden contener la misma pista más de
 * una vez).
 *
 * La primera aparición de un id usa el propio id; las siguientes se desambiguan
 * con su número de aparición (`id#1`, `id#2`, …). A diferencia de usar la
 * posición, la clave no cambia cuando la lista se reordena o crece por delante,
 * de modo que Compose conserva el estado de cada fila; y a diferencia de usar
 * solo el id, nunca produce claves duplicadas.
 */
fun stableKeys(ids: List<String>): List<String> {
    val occurrences = HashMap<String, Int>(ids.size)
    return ids.map { id ->
        val seen = occurrences.getOrDefault(id, 0)
        occurrences[id] = seen + 1
        if (seen == 0) id else "$id#$seen"
    }
}
