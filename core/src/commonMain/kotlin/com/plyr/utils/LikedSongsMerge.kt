package com.plyr.utils

/**
 * Qué favoritos de un archivo entran en `liked_songs` al fusionar.
 *
 * El bug (B51): `liked_songs` nunca se sobrescribe, se fusiona, y la fusión era
 * puramente aditiva —descartaba lo que ya estaba y añadía el resto—. Como el
 * usuario no tiene forma de decir "esta ya no la quiero", lo que acababa de
 * quitar se reinsertaba en la siguiente sincronización. Y como la fusión marca el
 * estado como sucio, el propio sync escribía después la lista ya resucitada en la
 * copia de la carpeta, de modo que el borrado dejaba de propagarse para siempre.
 *
 * La precedencia es: **tomb > duplicada > se añade**. Un tomb siempre gana,
 * incluso si la pista sigue presente en el dispositivo, porque "la he quitado" es
 * más específico que "ya estaba".
 *
 * Lógica pura a propósito: la fusión real necesita Room, pero la política —que
 * es donde está el bug— se testea aquí sin Android.
 */
object LikedSongsMerge {

    /**
     * Índices de [incomingKeys] que deben insertarse.
     *
     * @param existingKeys claves de lo que ya está en `liked_songs`.
     * @param incomingKeys claves de las pistas del archivo, en orden.
     * @param removedKeys claves de los favoritos que el usuario quitó.
     */
    fun selectFreshIndexes(
        existingKeys: Collection<String>,
        incomingKeys: List<String>,
        removedKeys: Collection<String>,
    ): List<Int> {
        val seen = existingKeys.toMutableSet()
        val removed = removedKeys.toSet()
        return buildList {
            incomingKeys.forEachIndexed { index, key ->
                if (key in removed) return@forEachIndexed
                if (!seen.add(key)) return@forEachIndexed
                add(index)
            }
        }
    }
}
