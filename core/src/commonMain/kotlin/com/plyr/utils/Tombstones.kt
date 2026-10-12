package com.plyr.utils

/**
 * Operaciones puras sobre los tombs de borrado (ids de listas borradas y claves
 * de favoritos quitados).
 *
 * La persistencia es de cada plataforma —SharedPreferences en Android (ver
 * `TombstoneConfig`) y el almacén que se enchufe en escritorio—, pero la
 * semántica de fusionar/añadir/olvidar se comparte y se testea sin Android.
 */
object Tombstones {

    /**
     * Une los tombs locales con los del archivo: es lo que propaga el borrado en
     * los dos sentidos, ya que un tomb creado al importar viaja al exportar.
     */
    fun merge(local: Collection<String>, incoming: Collection<String>): Set<String> =
        local.toSet() + incoming

    /** Marca [id] como tombado. Idempotente. */
    fun add(ids: Collection<String>, id: String): Set<String> = ids.toSet() + id

    /** Olvida el tomb de [id]: vuelve a poder sincronizarse. Idempotente. */
    fun remove(ids: Collection<String>, id: String): Set<String> = ids.toSet() - id
}
