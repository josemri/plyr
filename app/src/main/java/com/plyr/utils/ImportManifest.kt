package com.plyr.utils

import com.plyr.database.PlaylistLocalRepository
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

/** Una lista tal y como se lee de `playlists.json`. */
data class ImportedPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val coverEntry: String?,
    val tracks: List<ImportedTrack>
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
    INVALID_ID
}

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
     * Lee el contenido de [json] y devuelve sus listas en el mismo orden.
     *
     * @throws ManifestFormatException si no es un manifiesto de plyr o su
     *   versión no es la que entiende esta app.
     */
    fun parse(json: String): List<ImportedPlaylist> {
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

        return (0 until playlistsJson.length()).mapNotNull { playlistIndex ->
            playlistsJson.optJSONObject(playlistIndex)?.let { parsePlaylist(it) }
        }
    }

    /**
     * Decide qué hacer con cada lista según lo que ya hay en el dispositivo.
     * [existingIds] son los `remoteId` presentes; `liked_songs` siempre existe
     * (se crea al arrancar la app) y por eso nunca se da por importada.
     */
    fun plan(
        playlists: List<ImportedPlaylist>,
        existingIds: Set<String>
    ): List<PlaylistAction> = playlists.map { playlist ->
        when {
            playlist.id.isBlank() -> PlaylistAction.Skip(playlist.id, SkipReason.INVALID_ID)
            playlist.id.startsWith(LEGACY_ALBUM_PREFIX) ->
                PlaylistAction.Skip(playlist.id, SkipReason.LEGACY_ALBUM)
            playlist.id == PlaylistLocalRepository.LIKED_SONGS_ID -> PlaylistAction.MergeLikedSongs
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

    // === HELPERS DE PARSEO ===

    private fun parsePlaylist(json: JSONObject) = ImportedPlaylist(
        id = json.optString("id"),
        name = json.optString("name"),
        description = json.optString("description").takeIf { it.isNotEmpty() },
        coverEntry = json.optString("cover").takeIf { it.isNotEmpty() },
        tracks = parseTracks(json.optJSONArray("tracks"))
    )

    private fun parseTracks(json: JSONArray?): List<ImportedTrack> {
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
                    artists = parseArtists(trackJson.optJSONArray("artists")),
                    remoteTrackId = remoteTrackId,
                    youtubeVideoId = trackJson.optString("youtubeVideoId").takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    /** Vuelve a unir el array de artistas con el `", "` que espera Room. */
    private fun parseArtists(json: JSONArray?): String {
        if (json == null) return ""
        return (0 until json.length())
            .map { json.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }
}
