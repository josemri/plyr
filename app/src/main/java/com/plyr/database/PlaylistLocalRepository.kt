package com.plyr.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.plyr.utils.TrackIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Datos de presentación de una lista que se guarda como creada en la app o
 * importada: sin pistas ni origen, que son los otros dos argumentos del
 * guardado. Agruparlos mantiene la firma por debajo del límite de parámetros
 * de detekt y deja un punto único donde se arma el `PlaylistEntity`.
 */
data class CreatedPlaylist(
    val playlistId: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
)

/**
 * Punto de entrada a los datos locales de listas.
 *
 * Las escrituras viven en repositorios especializados, expuestos como
 * propiedades: [liked] (lista especial `liked_songs`), [playlists] (alta, edición
 * y borrado) y [tracks] (pistas de una lista). Esta clase conserva las lecturas
 * y los helpers puros.
 */
class PlaylistLocalRepository(context: Context) {

    private val store = PlaylistStore(context)

    /** Operaciones sobre la lista especial `liked_songs`. */
    val liked = LikedSongsRepository(store)

    /** Alta, edición y borrado de listas. */
    val playlists = PlaylistRepository(store)

    /** Pistas de una lista: añadir, quitar y reordenar. */
    val tracks = PlaylistTracksRepository(store)

    companion object {
        const val LIKED_SONGS_ID = LikedSongs.ID

        /**
         * Fila de *liked* que corresponde a una canción, o `null` si no está.
         *
         * La identidad vive en [TrackIdentity] (`:core`), compartida con el
         * escritorio: manda el `youtubeVideoId` y, si la canción llega sin id, se
         * recurre a nombre+artista con [TrackIdentity.fallbackDedupeKey], que es
         * la única clave disponible y la misma que usa `mergeLikedSongsTracks`.
         * Sin ese recurso, un `""` no encontraba nunca la fila guardada y cada
         * intento de quitar un favorito acababa **añadiendo** otra fila (B1).
         */
        fun likedTrackOf(
            tracks: List<TrackEntity>,
            name: String,
            artists: String,
            remoteTrackId: String,
            youtubeVideoId: String?
        ): TrackEntity? =
            TrackIdentity.likedTrackOf(tracks, name, artists, remoteTrackId, youtubeVideoId)

        /**
         * Listas que se muestran en el carrusel del Home y en la rejilla de
         * listas, ya filtradas y ordenadas.
         *
         * El filtro y el orden viven en [PlaylistFilters] (`:core`), compartidos
         * con el escritorio. Ver su documentación para el porqué de las dos
         * exclusiones (`album_*` y *liked* vacía).
         */
        fun visiblePlaylists(playlists: List<PlaylistEntity>): List<PlaylistEntity> =
            PlaylistFilters.visiblePlaylists(playlists, LIKED_SONGS_ID)
    }

    // === OBSERVATION ===

    fun getAllPlaylistsLiveData(): LiveData<List<PlaylistEntity>> {
        return store.playlistDao.getAllPlaylists().asLiveData()
    }

    fun getTracksByPlaylistLiveData(playlistId: String): LiveData<List<TrackEntity>> {
        return store.trackDao.getTracksByPlaylist(playlistId).asLiveData()
    }

    /** Lectura puntual de todas las listas (ordenadas por nombre), para la exportación. */
    suspend fun getAllPlaylists(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        store.playlistDao.getAllPlaylistsSync()
    }

    /** Lectura puntual de las pistas de una lista (ordenadas por posición), para la exportación. */
    suspend fun getTracksByPlaylistSync(playlistId: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        store.trackDao.getTracksByPlaylistSync(playlistId)
    }
}
