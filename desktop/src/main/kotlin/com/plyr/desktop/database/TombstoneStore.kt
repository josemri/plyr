package com.plyr.desktop.database

import com.plyr.utils.Tombstones

/**
 * Almacén de tombs de borrado (ids de listas y claves de favoritos quitados)
 * para escritorio.
 *
 * La semántica pura la aporta [Tombstones] (`:core`); aquí solo se guardan los
 * conjuntos. El escritorio aún no tiene copia de seguridad (llega en M10), que es
 * lo único que consulta los tombs, así que la implementación por defecto vive en
 * memoria. Se deja como interfaz para enchufar en M10 un almacén persistente sin
 * tocar los repositorios.
 */
interface TombstoneStore {
    fun deletedPlaylistIds(): Set<String>
    fun addDeletedPlaylistId(id: String)
    fun removeDeletedPlaylistId(id: String)

    fun removedLikedTrackKeys(): Set<String>
    fun addRemovedLikedTrackKey(key: String)
    fun removeRemovedLikedTrackKey(key: String)
}

/** Implementación en memoria (provisional hasta M10). */
class InMemoryTombstoneStore : TombstoneStore {

    private var deletedPlaylistIds: Set<String> = emptySet()
    private var removedLikedKeys: Set<String> = emptySet()

    override fun deletedPlaylistIds(): Set<String> = deletedPlaylistIds

    override fun addDeletedPlaylistId(id: String) {
        deletedPlaylistIds = Tombstones.add(deletedPlaylistIds, id)
    }

    override fun removeDeletedPlaylistId(id: String) {
        deletedPlaylistIds = Tombstones.remove(deletedPlaylistIds, id)
    }

    override fun removedLikedTrackKeys(): Set<String> = removedLikedKeys

    override fun addRemovedLikedTrackKey(key: String) {
        removedLikedKeys = Tombstones.add(removedLikedKeys, key)
    }

    override fun removeRemovedLikedTrackKey(key: String) {
        removedLikedKeys = Tombstones.remove(removedLikedKeys, key)
    }
}
