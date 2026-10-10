package com.plyr.database

import android.content.Context
import com.plyr.utils.DataSync

/**
 * Acceso compartido a la base de datos de listas.
 *
 * [PlaylistLocalRepository] y sus repositorios especializados ([LikedSongsRepository],
 * [PlaylistRepository], [PlaylistTracksRepository]) comparten la misma instancia de
 * Room, los DAOs y el aviso a la copia de seguridad ([DataSync]).
 */
internal class PlaylistStore(context: Context) {

    val database = PlaylistDatabase.getDatabase(context)
    val playlistDao = database.playlistDao()
    val trackDao = database.trackDao()
    val appContext: Context = context.applicationContext

    /** Avisa a la copia de seguridad de que los datos cambiaron (ver [DataSync]). */
    fun markDirty() = DataSync.markDirty(appContext)
}
