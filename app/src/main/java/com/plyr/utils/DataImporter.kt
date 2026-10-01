package com.plyr.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.plyr.database.PlaylistEntity
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.service.CoverImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Resumen de una importación completada. */
data class ImportSummary(
    val importedPlaylists: Int,
    val importedTracks: Int,
    val restoredCovers: Int,
    val mergedLikedTracks: Int,
    val skippedPlaylists: Int,
    val deletedPlaylists: Int = 0
)

/**
 * DataImporter - Restaura un ZIP previamente exportado por [DataExporter].
 *
 * El archivo se lee con [ImportArchive] (que acota tamaños), se interpreta con
 * [ImportManifest] (que decide lista por lista) y aquí solo se encarga de lo
 * que necesita Android: abrir el `Uri` de SAF, persistir las portadas en
 * `filesDir/covers` y escribir en Room.
 *
 * Reglas, en resumen:
 * - Las listas que ya existen se dejan intactas.
 * - `liked_songs` se fusiona con tus favoritos actuales, sin sobrescribirlos.
 * - Las filas legacy `album_*` se ignoran (la app no las muestra).
 * - Si una portada falta o está corrupta, la lista se importa igualmente sin ella.
 */
object DataImporter {

    private const val TAG = "DataImporter"

    /**
     * Importa el ZIP de [source]. Puede ser una copia de seguridad completa o un
     * archivo parcial: siempre es seguro repetirla.
     *
     * @return [Result.success] con el resumen, o [Result.failure] con
     *   [ManifestFormatException] si el archivo no es una exportación válida, o
     *   con la causa del error de lectura/escritura.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<ImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val stream = context.contentResolver.openInputStream(source)
                    ?: throw IOException("No se pudo abrir el archivo seleccionado")
                val entries = stream.use { ImportArchive.read(it) }

                // ImportArchive garantiza que el manifiesto está
                val manifestBytes = entries.getValue(ExportManifest.FILE_NAME)
                val manifest = ImportManifest.parse(manifestBytes.toString(Charsets.UTF_8))

                val repository = PlaylistLocalRepository(context)
                val existingPlaylists = repository.getAllPlaylists()

                // Los tombs son la unión de lo que esta app borró y lo que el
                // archivo recuerda como borrado. Fusionarlos es lo que propaga
                // el borrado en los dos sentidos: lo que se borró en otro
                // dispositivo se borra aquí, y lo borrado aquí viaja al exportar.
                val deletedIds = Config.getDeletedPlaylistIds(context).toMutableSet()
                deletedIds.addAll(manifest.deletedPlaylistIds)
                Config.setDeletedPlaylistIds(context, deletedIds)

                // Mismo criterio para los favoritos (B51): la unión de lo que esta
                // app borró y lo que el archivo recuerda, para que el borrado viaje
                // en los dos sentidos.
                val removedLiked = Config.getRemovedLikedTrackKeys(context).toMutableSet()
                removedLiked.addAll(manifest.removedLikedTrackKeys)
                Config.setRemovedLikedTrackKeys(context, removedLiked)

                val actions = ImportManifest.plan(
                    playlists = manifest.playlists,
                    existingIds = existingPlaylists.mapTo(mutableSetOf()) { it.remoteId },
                    deletedPlaylistIds = deletedIds
                )

                var importedPlaylists = 0
                var importedTracks = 0
                var restoredCovers = 0
                var mergedLikedTracks = 0
                var skippedPlaylists = 0
                var deletedPlaylists = 0

                actions.forEach { action ->
                    when (action) {
                        is PlaylistAction.Skip -> {
                            skippedPlaylists++
                            Log.d(TAG, "Importación: ${action.id} omitida (${action.reason})")
                        }

                        PlaylistAction.MergeLikedSongs -> {
                            val liked = manifest.playlists.firstOrNull {
                                it.id == PlaylistLocalRepository.LIKED_SONGS_ID
                            }
                            if (liked != null) {
                                mergedLikedTracks += mergeLikedSongs(repository, liked, removedLiked)
                            }
                        }

                        is PlaylistAction.Create -> {
                            val result = createPlaylist(context, repository, action.playlist, entries)
                            if (result != null) {
                                importedPlaylists++
                                importedTracks += result.tracks
                                restoredCovers += if (result.coverRestored) 1 else 0
                            } else {
                                skippedPlaylists++
                            }
                        }
                    }
                }

                // Las listas que quedaron tomadas (borradas aquí o en el
                // archivo) no deben vivir en el dispositivo, salvo liked_songs.
                existingPlaylists.forEach { existing ->
                    if (existing.remoteId in deletedIds &&
                        existing.remoteId != PlaylistLocalRepository.LIKED_SONGS_ID
                    ) {
                        repository.deletePlaylist(existing.remoteId)
                        deletedPlaylists++
                        Log.d(TAG, "Importación: aplicado borrado de ${existing.remoteId}")
                    }
                }

                Log.d(
                    TAG,
                    "Importadas $importedPlaylists listas ($importedTracks pistas), " +
                        "$mergedLikedTracks favoritos, $deletedPlaylists borradas, $skippedPlaylists omitidas"
                )
                ImportSummary(
                    importedPlaylists = importedPlaylists,
                    importedTracks = importedTracks,
                    restoredCovers = restoredCovers,
                    mergedLikedTracks = mergedLikedTracks,
                    skippedPlaylists = skippedPlaylists,
                    deletedPlaylists = deletedPlaylists
                )
            }
        }

    // === ESCRITURA ===

    /** Crea una lista con sus pistas y su portada. Null si no se pudo guardar. */
    private suspend fun createPlaylist(
        context: Context,
        repository: PlaylistLocalRepository,
        playlist: ImportedPlaylist,
        entries: Map<String, ByteArray>
    ): CreateResult? {
        val now = System.currentTimeMillis()
        val tracks: List<TrackEntity> =
            ImportManifest.buildTracks(playlistId = playlist.id, tracks = playlist.tracks, now = now)
        val cover = restoreCover(context, playlist, entries)

        val saved = repository.restorePlaylist(
            playlist = PlaylistEntity(
                remoteId = playlist.id,
                name = playlist.name.ifBlank { playlist.id },
                description = playlist.description,
                trackCount = tracks.size,
                imageUrl = cover,
                lastSyncTime = now
            ),
            tracks = tracks
        )
        if (!saved) {
            Log.w(TAG, "No se pudo restaurar la lista ${playlist.id}")
            return null
        }
        return CreateResult(tracks = tracks.size, coverRestored = cover != null)
    }

    /**
     * Fusiona los favoritos del archivo con los del dispositivo y devuelve
     * cuántas pistas se añadieron. La portada del ZIP se ignora: `liked_songs`
     * se dibuja siempre con un corazón, no con una imagen.
     */
    private suspend fun mergeLikedSongs(
        repository: PlaylistLocalRepository,
        playlist: ImportedPlaylist,
        removedKeys: Set<String>
    ): Int = repository.mergeLikedSongsTracks(
        tracks = ImportManifest.buildTracks(playlistId = playlist.id, tracks = playlist.tracks),
        removedKeys = removedKeys,
    )

    // === PORTADAS ===

    /**
     * Escribe la portada del ZIP en `filesDir/covers` y devuelve su URI `file://`,
     * o null si no venía portada o no se pudo decodificar. La lista se importa
     * igualmente: perder una imagen no debe abortar la importación.
     */
    private fun restoreCover(
        context: Context,
        playlist: ImportedPlaylist,
        entries: Map<String, ByteArray>
    ): String? {
        val entryName = playlist.coverEntry ?: return null
        val bytes = entries[entryName]
        if (bytes == null) {
            Log.w(TAG, "La lista ${playlist.id} apunta a una portada ausente: $entryName")
            return null
        }

        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Portada ilegible en ${playlist.id}: ${e.message}")
            null
        } ?: return null

        return CoverImageManager.save(context, playlist.id.removePrefix("youtube_"), bitmap)
    }

    private class CreateResult(val tracks: Int, val coverRestored: Boolean)
}
