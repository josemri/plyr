package com.plyr.desktop.database

import com.plyr.desktop.config.DesktopSettings
import com.plyr.utils.Tombstones
import com.russhwolf.settings.Settings

/**
 * Almacén de tombs de borrado (ids de listas y claves de favoritos quitados)
 * para escritorio.
 *
 * La semántica pura la aporta [Tombstones] (`:core`); aquí solo se guardan los
 * conjuntos. El escritorio tenía una implementación en memoria hasta M10; la
 * copia de seguridad usa [SettingsTombstoneStore], que los persiste.
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

/**
 * Implementación persistente (M10) sobre [Settings] (`java.util.prefs`), para
 * que los tombs sobrevivan a reinicios.
 *
 * Usa las **mismas claves** que `TombstoneConfig` de Android (`deleted_playlist_ids`
 * y `removed_liked_track_keys`). Los conjuntos se serializan como texto separado
 * por saltos de línea, que ni un id de lista ni una clave de favorito contienen.
 */
class SettingsTombstoneStore(
    private val settings: Settings = DesktopSettings.create(),
) : TombstoneStore {

    override fun deletedPlaylistIds(): Set<String> = read(KEY_DELETED_PLAYLISTS)

    override fun addDeletedPlaylistId(id: String) {
        write(KEY_DELETED_PLAYLISTS, deletedPlaylistIds() + id)
    }

    override fun removeDeletedPlaylistId(id: String) {
        write(KEY_DELETED_PLAYLISTS, deletedPlaylistIds() - id)
    }

    override fun removedLikedTrackKeys(): Set<String> = read(KEY_REMOVED_LIKED)

    override fun addRemovedLikedTrackKey(key: String) {
        write(KEY_REMOVED_LIKED, removedLikedTrackKeys() + key)
    }

    override fun removeRemovedLikedTrackKey(key: String) {
        write(KEY_REMOVED_LIKED, removedLikedTrackKeys() - key)
    }

    private fun read(key: String): Set<String> =
        settings.getStringOrNull(key)
            ?.split(SEPARATOR)
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    private fun write(key: String, values: Set<String>) {
        if (values.isEmpty()) settings.remove(key)
        else settings.putString(key, values.joinToString(SEPARATOR))
    }

    private companion object {
        const val KEY_DELETED_PLAYLISTS = "deleted_playlist_ids"
        const val KEY_REMOVED_LIKED = "removed_liked_track_keys"
        const val SEPARATOR = "\n"
    }
}
