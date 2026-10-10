package com.plyr.database

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.plyr.utils.ImportManifest
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
        const val LIKED_SONGS_ID = "liked_songs"

        /**
         * Fila de *liked* que corresponde a una canción, o `null` si no está.
         *
         * La identidad es el `youtubeVideoId`; si la canción llega sin id, se
         * recurre a nombre+artista con [ImportManifest.fallbackDedupeKey], que
         * es la única clave disponible y la misma que usa `mergeLikedSongsTracks`.
         * Sin ese recurso, un `""` no encontraba nunca la fila guardada y cada
         * intento de quitar un favorito acababa **añadiendo** otra fila (B1).
         */
        fun likedTrackOf(
            tracks: List<TrackEntity>,
            name: String,
            artists: String,
            remoteTrackId: String,
            youtubeVideoId: String?
        ): TrackEntity? {
            if (!youtubeVideoId.isNullOrBlank()) {
                tracks.firstOrNull { it.youtubeVideoId == youtubeVideoId }?.let { return it }
            }
            val key = ImportManifest.fallbackDedupeKey(
                TrackEntity(
                    id = "",
                    playlistId = LIKED_SONGS_ID,
                    remoteTrackId = remoteTrackId,
                    name = name,
                    artists = artists,
                    youtubeVideoId = youtubeVideoId,
                    audioUrl = null,
                    position = 0
                )
            )
            return tracks.firstOrNull { ImportManifest.fallbackDedupeKey(it) == key }
        }

        /**
         * Listas que se muestran en el carrusel del Home y en la rejilla de
         * listas, ya filtradas y ordenadas.
         *
         * Dos exclusiones:
         * - `album_*`: los álbumes de Spotify no son listas del usuario.
         * - *liked* vacía: la fila se crea siempre al arrancar
         *   (`ensureLikedSongsPlaylist`), así que sin este filtro el primer
         *   elemento del Home era un corazón rojo sin nada detrás (F1).
         *
         * **Es un filtro, no un borrado**: `toggleLikeTrack`,
         * `mergeLikedSongsTracks` e `ImportManifest.plan` dan por hecho que la
         * fila de *liked* existe, y borrarla "si está vacía" rompería las tres.
         * `trackCount` lo mantienen las tres rutas que la modifican, así que
         * basta con mirarlo — y como `getAllPlaylists()` es un `Flow` sobre la
         * tabla, aparece y desaparece sola.
         */
        fun visiblePlaylists(playlists: List<PlaylistEntity>): List<PlaylistEntity> =
            playlists
                .filter { !it.remoteId.startsWith("album_") }
                .filter { it.remoteId != LIKED_SONGS_ID || it.trackCount > 0 }
                .sortedBy { if (it.remoteId == LIKED_SONGS_ID) "" else it.name }
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
