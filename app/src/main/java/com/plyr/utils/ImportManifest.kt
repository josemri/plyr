package com.plyr.utils

import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackEntity
import org.json.JSONArray
import org.json.JSONObject

/** El ZIP no es de este formato (o es de una versión que aún no entendemos). */
class ManifestFormatException(message: String) : Exception(message)

/**
 * Una pista tal y como se lee de `playlists.json`. [artists] viene ya unida con
 * `", "` porque así lo guarda Room en `TrackEntity.artists`.
 */
data class ImportedTrack(
    val position: Int,
    val name: String,
    val artists: String,
    val remoteTrackId: String,
    val youtubeVideoId: String?
)

/**
 * Una lista tal y como se lee de `playlists.json`. [source] y [sourceId] son
 * opcionales: un archivo anterior a que se persistiera el origen no los trae y
 * se cae a la clasificación por `remoteId`/`description`.
 */
data class ImportedPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val coverEntry: String?,
    val tracks: List<ImportedTrack>,
    val source: PlaylistSource? = null,
    val sourceId: String? = null
) {
    val trackCount: Int get() = tracks.size
}

/** Por qué una lista del ZIP no se va a crear. */
enum class SkipReason {
    /** El dispositivo ya tiene una lista con ese id. */
    ALREADY_EXISTS,

    /** Fila legacy `album_*`: la app las oculta en todos los listados. */
    LEGACY_ALBUM,

    /** Sin id no se puede crear: `remoteId` es la clave primaria. */
    INVALID_ID,

    /** El id está tombado: el usuario lo borró y no debe resucitar. */
    IGNORED_DELETED
}

/**
 * Manifiesto ya interpretado: las listas que hay que tratar más los ids que
 * se marcaron como borrados (tombs) esa misma vez.
 */
data class ParsedManifest(
    val playlists: List<ImportedPlaylist>,
    val deletedPlaylistIds: Set<String>,
    /**
     * Claves de los favoritos que el usuario quitó y no deben volver (B51). Vacío
     * en un archivo antiguo, que es lo correcto: no sincronizar borrados es más
     * conservador que borrar de más.
     */
    val removedLikedTrackKeys: Set<String> = emptySet()
)

/** Qué hay que hacer con cada lista del ZIP. */
sealed interface PlaylistAction {
    /** La lista no existe: se crea con sus pistas y su portada. */
    data class Create(val playlist: ImportedPlaylist) : PlaylistAction

    /**
     * `liked_songs` nunca se sobrescribe: se fusiona con los favoritos que ya
     * hay en el dispositivo, respetando lo que el usuario tenga ahora.
     */
    data object MergeLikedSongs : PlaylistAction

    /** No se toca nada. */
    data class Skip(val id: String, val reason: SkipReason) : PlaylistAction
}

/**
 * ImportManifest - Lógica pura de importación: leer `playlists.json`, decidir
 * qué hacer con cada lista y convertirla en `TrackEntity`.
 *
 * No toca Android ni el sistema de archivos (eso es [DataImporter] y
 * [ImportArchive]), así que todo el riesgo —validación del formato, política de
 * conflictos y generación de ids— se cubre con tests JVM.
 */
object ImportManifest {

    /** Prefijo de las filas legacy que la app oculta en los listados. */
    const val LEGACY_ALBUM_PREFIX = "album_"

    /**
     * Lee el contenido de [json] y devuelve las listas (en su orden) junto con
     * los ids borrados que el manifiesto recuerda.
     *
     * @throws ManifestFormatException si no es un manifiesto de plyr o su
     *   versión no es la que entiende esta app.
     */
    fun parse(json: String): ParsedManifest {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ManifestFormatException("El manifiesto no es un JSON válido: ${e.message}")
        }

        if (root.optString("app") != ExportManifest.APP_NAME) {
            throw ManifestFormatException("El manifiesto no fue generado por ${ExportManifest.APP_NAME}")
        }
        if (root.optInt("formatVersion", -1) != ExportManifest.FORMAT_VERSION) {
            throw ManifestFormatException(
                "Versión de formato ${root.optInt("formatVersion", -1)} no soportada " +
                    "(esta app entiende la ${ExportManifest.FORMAT_VERSION})"
            )
        }

        val playlistsJson = root.optJSONArray("playlists")
            ?: throw ManifestFormatException("El manifiesto no tiene la lista \"playlists\"")

        val playlists = (0 until playlistsJson.length()).mapNotNull { playlistIndex ->
            playlistsJson.optJSONObject(playlistIndex)?.let { ManifestJson.playlist(it) }
        }

        return ParsedManifest(
            playlists = playlists,
            deletedPlaylistIds = parseDeletedPlaylistIds(root),
            removedLikedTrackKeys = parseRemovedLikedTrackKeys(root)
        )
    }

    /**
     * Los tombs son opcionales: un archivo antiguo no los lleva, y no
     * sincronizar borrados es más conservador que borrar de más.
     */
    private fun parseDeletedPlaylistIds(root: JSONObject): Set<String> {
        val array = root.optJSONArray("deletedPlaylistIds") ?: return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val id = array.optString(i).trim()
                if (id.isNotEmpty() && id != PlaylistLocalRepository.LIKED_SONGS_ID) add(id)
            }
        }
    }

    /**
     * Los tombs de favoritos son opcionales, igual que los de listas: sin ellos
     * el archivo es válido y los favoritos que falten se restauran.
     */
    private fun parseRemovedLikedTrackKeys(root: JSONObject): Set<String> {
        val array = root.optJSONArray("removedLikedTrackKeys") ?: return emptySet()
        return buildSet {
            for (i in 0 until array.length()) {
                val key = array.optString(i).trim()
                if (key.isNotEmpty()) add(key)
            }
        }
    }

    /**
     * Decide qué hacer con cada lista según lo que ya hay en el dispositivo.
     * [existingIds] son los `remoteId` presentes; `liked_songs` siempre existe
     * (se crea al arrancar la app) y por eso nunca se da por importada.
     *
     * [deletedPlaylistIds] son los tombs que se aplican en esta importación
     * (los propios de la app más los que traía el archivo): una lista borrada
     * no se recrea, pase lo que pase con el resto de la política.
     */
    fun plan(
        playlists: List<ImportedPlaylist>,
        existingIds: Set<String>,
        deletedPlaylistIds: Set<String> = emptySet()
    ): List<PlaylistAction> = playlists.map { playlist ->
        when {
            playlist.id.isBlank() -> PlaylistAction.Skip(playlist.id, SkipReason.INVALID_ID)
            playlist.id.startsWith(LEGACY_ALBUM_PREFIX) ->
                PlaylistAction.Skip(playlist.id, SkipReason.LEGACY_ALBUM)
            playlist.id == PlaylistLocalRepository.LIKED_SONGS_ID -> PlaylistAction.MergeLikedSongs
            playlist.id in deletedPlaylistIds -> PlaylistAction.Skip(playlist.id, SkipReason.IGNORED_DELETED)
            playlist.id in existingIds -> PlaylistAction.Skip(playlist.id, SkipReason.ALREADY_EXISTS)
            else -> PlaylistAction.Create(playlist)
        }
    }

    /**
     * Convierte las pistas de una lista en `TrackEntity` listas para Room.
     *
     * Las posiciones se renumeran de 0 a n-1 siguiendo el orden del array, en vez
     * de fiarse del `position` exportado: es lo que garantiza un orden coherente
     * aunque el archivo se haya editado a mano. El `id` se sintetiza por posición
     * porque el manifiesto no lo incluye y `TrackEntity.id` es la clave primaria
     * global: reutilizar `remoteTrackId` a secas fusionaría pistas repetidas.
     */
    fun buildTracks(
        playlistId: String,
        tracks: List<ImportedTrack>,
        now: Long = System.currentTimeMillis()
    ): List<TrackEntity> = tracks.mapIndexed { index, track ->
        TrackEntity(
            id = synthesizeTrackId(playlistId, index),
            playlistId = playlistId,
            remoteTrackId = track.remoteTrackId,
            name = track.name,
            artists = track.artists,
            youtubeVideoId = track.youtubeVideoId,
            audioUrl = null,
            position = index,
            lastSyncTime = now
        )
    }

    /** `id` único dentro de la lista; opaco para el resto de la app. */
    fun synthesizeTrackId(playlistId: String, position: Int): String = "${playlistId}_$position"

    /**
     * Clave para deduplicar una pista que no tiene `youtubeVideoId` (con ese
     * campo vacío no se puede comparar por vídeo, que es lo que usa
     * `toggleLikeTrack`).
     */
    fun fallbackDedupeKey(track: TrackEntity): String = "${track.name}|${track.artists}"

    /**
     * La clave con la que se identifica un favorito, y con la que se tombstonea un
     * borrado de favorito (B51). Es **la misma** que usa [PlaylistLocalRepository]
     * para no duplicar en la fusión: si fueran dos criterios distintos, un tomb
     * no quadraría con la pista que pretende Tapar.
     */
    fun likedTrackKey(track: TrackEntity): String = likedTrackKey(track.youtubeVideoId, track.name, track.artists)

    /**
     * La misma clave, calculada desde los datos crudos que recibe
     * `toggleLikeTrack`. Existe para que marcar y desmarcar usen **literalmente**
     * el mismo criterio que la fusión y que el tomb: si el marcado construyera la
     * clave por su cuenta, un `youtubeVideoId` vacío daría una clave distinta a
     * la que luego comprueba la fusión.
     */
    fun likedTrackKey(youtubeVideoId: String?, name: String, artists: String): String =
        youtubeVideoId?.trim()?.takeIf { it.isNotEmpty() } ?: "$name|$artists"
}

/**
 * Helpers de parseo de `playlists.json`. Viven aparte para no engordar
 * [ImportManifest] con funciones internas.
 */
private object ManifestJson {

    fun playlist(json: JSONObject) = ImportedPlaylist(
        id = json.optString("id"),
        name = json.optString("name"),
        description = json.optString("description").takeIf { it.isNotEmpty() },
        coverEntry = json.optString("cover").takeIf { it.isNotEmpty() },
        tracks = tracks(json.optJSONArray("tracks")),
        source = PlaylistSource.fromStorage(json.optString("source")),
        sourceId = json.optString("sourceId").takeIf { it.isNotEmpty() }
    )

    private fun tracks(json: JSONArray?): List<ImportedTrack> {
        if (json == null) return emptyList()
        return (0 until json.length()).mapNotNull { index ->
            json.optJSONObject(index)?.let { trackJson ->
                val name = trackJson.optString("name")
                // Sin `remoteTrackId` se cae al vídeo y, en último término, a un id
                // sintético por posición: el borrado de pistas y el enlace de
                // compartir trabajan con este campo.
                val remoteTrackId = trackJson.optString("remoteTrackId").ifBlank {
                    trackJson.optString("youtubeVideoId").ifBlank { "imported_${trackJson.optInt("position", index)}" }
                }
                ImportedTrack(
                    position = trackJson.optInt("position", index),
                    name = name,
                    artists = artists(trackJson.optJSONArray("artists")),
                    remoteTrackId = remoteTrackId,
                    youtubeVideoId = trackJson.optString("youtubeVideoId").takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    /** Vuelve a unir el array de artistas con el `", "` que espera Room. */
    private fun artists(json: JSONArray?): String {
        if (json == null) return ""
        return (0 until json.length())
            .map { json.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }
}
