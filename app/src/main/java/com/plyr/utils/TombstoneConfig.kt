package com.plyr.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Tombs de borrado: recuerda qué listas y qué favoritos se borraron para que la
 * sincronización no los resucite.
 *
 * Persistidas en el fichero compartido de [ConfigPrefs]. Ver `DataSync`.
 */
object TombstoneConfig {

    // Ids de listas que se borraron y no deben resucitar al sincronizar.
    private const val KEY_DELETED_PLAYLIST_IDS = "deleted_playlist_ids"
    private const val KEY_REMOVED_LIKED_TRACK_KEYS = "removed_liked_track_keys"

    /**
     * Ids de las listas que el usuario borró en algún momento.
     *
     * Borrar no es solo quitar la fila de Room: también hay que recordarlo,
     * porque la copia de la carpeta guarda cada lista y la fusión la devolvería
     * en la siguiente sincronización. El id viaja en el manifiesto del ZIP y la
     * unión local+archivo es lo que se aplica al importar.
     */
    fun getDeletedPlaylistIds(context: Context): Set<String> =
        ConfigPrefs.get(context).getStringSet(KEY_DELETED_PLAYLIST_IDS, emptySet())
            ?.toSet()
            ?: emptySet()

    fun setDeletedPlaylistIds(context: Context, ids: Collection<String>) {
        ConfigPrefs.get(context).edit {
            if (ids.isEmpty()) remove(KEY_DELETED_PLAYLIST_IDS)
            else putStringSet(KEY_DELETED_PLAYLIST_IDS, ids.toSet())
        }
    }

    /** Marca [id] como borrada. Idempotente. */
    fun addDeletedPlaylistId(context: Context, id: String) {
        setDeletedPlaylistIds(context, getDeletedPlaylistIds(context) + id)
    }

    /** Olvida que [id] se borró: la lista vuelve a poder sincronizarse. */
    fun removeDeletedPlaylistId(context: Context, id: String) {
        setDeletedPlaylistIds(context, getDeletedPlaylistIds(context) - id)
    }

    /**
     * Claves de los favoritos que el usuario quitó de `liked_songs` (B51).
     *
     * Es el equivalente de [getDeletedPlaylistIds] para las pistas: `liked_songs`
     * nunca se sobrescribe (se fusiona), así que sin una marca de "esto lo quité
     * yo" la fusión lo reinsertaba en cada sincronización y el borrado acababa
     * propagándose al propio archivo, con lo que ya no se podía deshacer.
     *
     * La clave es la misma que usa la fusión para no duplicar:
     * [ImportManifest.likedTrackKey].
     */
    fun getRemovedLikedTrackKeys(context: Context): Set<String> =
        ConfigPrefs.get(context).getStringSet(KEY_REMOVED_LIKED_TRACK_KEYS, emptySet())
            ?.toSet()
            ?: emptySet()

    fun setRemovedLikedTrackKeys(context: Context, keys: Collection<String>) {
        ConfigPrefs.get(context).edit {
            if (keys.isEmpty()) remove(KEY_REMOVED_LIKED_TRACK_KEYS)
            else putStringSet(KEY_REMOVED_LIKED_TRACK_KEYS, keys.toSet())
        }
    }

    /** Marca [key] como favorita que el usuario quitó. Idempotente. */
    fun addRemovedLikedTrackKey(context: Context, key: String) {
        setRemovedLikedTrackKeys(context, getRemovedLikedTrackKeys(context) + key)
    }

    /**
     * Olvida el borrado de [key]: el usuario la volvió a marcar como favorita, así
     * que su intención actual es que exista. Sin esto, volver a marcarla no la
     * sacaría del tomb y en la siguiente fusión se saltaría de todas formas.
     */
    fun removeRemovedLikedTrackKey(context: Context, key: String) {
        setRemovedLikedTrackKeys(context, getRemovedLikedTrackKeys(context) - key)
    }
}
