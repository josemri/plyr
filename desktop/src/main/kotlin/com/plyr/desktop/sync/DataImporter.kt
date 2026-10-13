package com.plyr.desktop.sync

import com.plyr.core.util.Logger
import com.plyr.database.LikedSongs
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.database.TombstoneStore
import com.plyr.desktop.platform.AppPaths
import com.plyr.utils.ExportManifest
import com.plyr.utils.ImportArchive
import com.plyr.utils.ImportManifest
import com.plyr.utils.ImportedPlaylist
import com.plyr.utils.ParsedManifest
import com.plyr.utils.PlaylistAction
import com.plyr.utils.Tombstones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private const val TAG = "DataImporter"

/** Nombre de la carpeta, dentro de los datos, donde viven las portadas restauradas. */
private const val COVERS_DIR_NAME = "covers"

/** Resumen de una importación completada. */
data class ImportSummary(
    val importedPlaylists: Int,
    val importedTracks: Int,
    val restoredCovers: Int,
    val mergedLikedTracks: Int,
    val skippedPlaylists: Int,
    val deletedPlaylists: Int = 0,
)

/**
 * DataImporter - Restaura un ZIP previamente exportado por [DataExporter].
 *
 * El archivo se lee con [ImportArchive] (que acota tamaños), se interpreta con
 * `ImportManifest` (`:core`, compartido con Android) y aquí solo se hace lo que
 * necesita el escritorio: escribir en los repositorios SQLDelight y persistir las
 * portadas bajo `dataDir/covers` con `java.nio`.
 *
 * Reglas, en resumen:
 * - Las listas que ya existen se dejan intactas.
 * - `liked_songs` se fusiona con tus favoritos actuales, sin sobrescribirlos.
 * - Las filas legacy `album_*` se ignoran (la app no las muestra).
 * - Si una portada falta o está corrupta, la lista se importa igualmente sin ella.
 */
class DataImporter(
    private val repository: PlaylistRepository,
    private val tombstones: TombstoneStore,
    private val coversDir: Path = Path.of(AppPaths.directories.dataDir, COVERS_DIR_NAME),
) {

    /**
     * Importa el ZIP de [source]. Puede ser una copia de seguridad completa o un
     * archivo parcial: siempre es seguro repetirla.
     *
     * @return [Result.success] con el resumen, o [Result.failure] con
     *   `ManifestFormatException` si el archivo no es una exportación válida, o
     *   con la causa del error de lectura/escritura.
     */
    suspend fun importFrom(source: Path): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = Files.newInputStream(source).use { ImportArchive.read(it) }
            applyEntries(entries)
        }
    }

    /** Igual que [importFrom] pero leyendo de un flujo ya abierto (que consume y cierra). */
    suspend fun importFrom(source: InputStream): Result<ImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val entries = source.use { ImportArchive.read(it) }
                applyEntries(entries)
            }
        }

    private fun applyEntries(entries: Map<String, ByteArray>): ImportSummary {
        // ImportArchive garantiza que el manifiesto está.
        val manifestBytes = entries.getValue(ExportManifest.FILE_NAME)
        val manifest = ImportManifest.parse(manifestBytes.toString(Charsets.UTF_8))

        val existingPlaylists = repository.allPlaylists()
        val merged = mergeTombstones(manifest)

        val actions = ImportManifest.plan(
            playlists = manifest.playlists,
            existingIds = existingPlaylists.mapTo(mutableSetOf()) { it.remoteId },
            deletedPlaylistIds = merged.deletedPlaylistIds,
        )

        val counters = applyActions(manifest, actions, entries, merged.removedLikedTrackKeys)
        applyPendingDeletions(existingPlaylists, merged.deletedPlaylistIds, counters)

        Logger.d(
            TAG,
            "Importadas ${counters.importedPlaylists} listas (${counters.importedTracks} pistas), " +
                "${counters.mergedLikedTracks} favoritos, ${counters.deletedPlaylists} borradas, " +
                "${counters.skippedPlaylists} omitidas",
        )
        return counters.toSummary()
    }

    // === TOMBSTONES ===

    /**
     * Fusiona los ids borrados de la app y del archivo (y lo mismo con los
     * favoritos quitados): es lo que propaga el borrado en los dos sentidos, ya
     * que un tomb creado al importar viaja al exportar. La semántica pura la
     * aporta [Tombstones] (`:core`).
     */
    private fun mergeTombstones(manifest: ParsedManifest): MergedTombstones {
        val deletedIds =
            Tombstones.merge(tombstones.deletedPlaylistIds(), manifest.deletedPlaylistIds)
        deletedIds.forEach(tombstones::addDeletedPlaylistId)

        val removedLiked =
            Tombstones.merge(tombstones.removedLikedTrackKeys(), manifest.removedLikedTrackKeys)
        removedLiked.forEach(tombstones::addRemovedLikedTrackKey)

        return MergedTombstones(deletedIds, removedLiked)
    }

    /** Ejecuta el plan de importación y acumula los contadores. */
    private fun applyActions(
        manifest: ParsedManifest,
        actions: List<PlaylistAction>,
        entries: Map<String, ByteArray>,
        removedLiked: Set<String>,
    ): ImportCounters {
        val counters = ImportCounters()
        actions.forEach { action ->
            when (action) {
                is PlaylistAction.Skip -> {
                    counters.skippedPlaylists++
                    Logger.d(TAG, "Importación: ${action.id} omitida (${action.reason})")
                }

                PlaylistAction.MergeLikedSongs -> {
                    val liked = manifest.playlists.firstOrNull { it.id == LikedSongs.ID }
                    if (liked != null) {
                        counters.mergedLikedTracks += mergeLikedSongs(liked, removedLiked)
                    }
                }

                is PlaylistAction.Create -> {
                    val result = createPlaylist(action.playlist, entries)
                    if (result != null) {
                        counters.importedPlaylists++
                        counters.importedTracks += result.tracks
                        counters.restoredCovers += if (result.coverRestored) 1 else 0
                    } else {
                        counters.skippedPlaylists++
                    }
                }
            }
        }
        return counters
    }

    /** Aplica los borrados tomados que aún vivan en el dispositivo. */
    private fun applyPendingDeletions(
        existingPlaylists: List<PlaylistRecord>,
        deletedIds: Set<String>,
        counters: ImportCounters,
    ) {
        // Las listas que quedaron tomadas (borradas aquí o en el archivo) no
        // deben vivir en el dispositivo, salvo liked_songs.
        existingPlaylists.forEach { existing ->
            if (existing.remoteId in deletedIds && existing.remoteId != LikedSongs.ID) {
                repository.deletePlaylist(existing.remoteId)
                counters.deletedPlaylists++
                Logger.d(TAG, "Importación: aplicado borrado de ${existing.remoteId}")
            }
        }
    }

    // === ESCRITURA ===

    /** Crea una lista con sus pistas y su portada. Null si no se pudo guardar. */
    private fun createPlaylist(
        playlist: ImportedPlaylist,
        entries: Map<String, ByteArray>,
    ): CreateResult? {
        val now = System.currentTimeMillis()
        val tracks = ImportManifest.buildTracks(
            playlistId = playlist.id,
            tracks = playlist.tracks,
            now = now,
        )
        val cover = restoreCover(playlist, entries)

        val saved = repository.savePlaylist(
            playlist = PlaylistRecord(
                remoteId = playlist.id,
                name = playlist.name.ifBlank { playlist.id },
                description = playlist.description,
                trackCount = tracks.size,
                imageUrl = cover,
                lastSyncTime = now,
                source = playlist.source ?: PlaylistSource.UNKNOWN,
                sourceId = playlist.sourceId,
            ),
            tracks = tracks,
        )
        if (!saved) {
            Logger.w(TAG, "No se pudo restaurar la lista ${playlist.id}")
            return null
        }
        return CreateResult(tracks = tracks.size, coverRestored = cover != null)
    }

    /**
     * Fusiona los favoritos del archivo con los del dispositivo y devuelve
     * cuántas pistas se añadieron. La portada del ZIP se ignora: `liked_songs`
     * se dibuja siempre con un corazón, no con una imagen.
     */
    private fun mergeLikedSongs(playlist: ImportedPlaylist, removedKeys: Set<String>): Int =
        repository.mergeLikedSongsTracks(
            tracks = ImportManifest.buildTracks(playlistId = playlist.id, tracks = playlist.tracks),
            removedKeys = removedKeys,
        )

    // === PORTADAS ===

    /**
     * Escribe la portada del ZIP en `covers/<id>.jpg` y devuelve su URI `file://`,
     * o null si no venía portada o no se pudo decodificar. La lista se importa
     * igualmente: perder una imagen no debe abortar la importación.
     */
    private fun restoreCover(
        playlist: ImportedPlaylist,
        entries: Map<String, ByteArray>,
    ): String? {
        val entryName = playlist.coverEntry ?: return null
        val bytes = entries[entryName]
        if (bytes == null) {
            Logger.w(TAG, "La lista ${playlist.id} apunta a una portada ausente: $entryName")
            return null
        }
        if (!isDecodable(bytes)) return null

        return try {
            Files.createDirectories(coversDir)
            val file = coversDir.resolve("${ExportManifest.sanitizeEntryName(playlist.id)}.jpg")
            Files.write(file, bytes)
            file.toUri().toString()
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo guardar la portada de ${playlist.id}: ${e.message}")
            null
        }
    }

    private fun isDecodable(bytes: ByteArray): Boolean = try {
        ImageIO.read(bytes.inputStream()) != null
    } catch (e: Exception) {
        Logger.w(TAG, "Portada ilegible: ${e.message}")
        false
    }

    private class CreateResult(val tracks: Int, val coverRestored: Boolean)

    /** Ids de listas y claves de favoritos recordados como borrados. */
    private class MergedTombstones(
        val deletedPlaylistIds: Set<String>,
        val removedLikedTrackKeys: Set<String>,
    )

    /** Contadores mutables de una importación. */
    private class ImportCounters {
        var importedPlaylists = 0
        var importedTracks = 0
        var restoredCovers = 0
        var mergedLikedTracks = 0
        var skippedPlaylists = 0
        var deletedPlaylists = 0

        fun toSummary() = ImportSummary(
            importedPlaylists = importedPlaylists,
            importedTracks = importedTracks,
            restoredCovers = restoredCovers,
            mergedLikedTracks = mergedLikedTracks,
            skippedPlaylists = skippedPlaylists,
            deletedPlaylists = deletedPlaylists,
        )
    }
}

/** Si el archivo de la carpeta aportó algo que la app no tuviera. */
fun ImportSummary.broughtAnything(): Boolean =
    importedPlaylists > 0 || mergedLikedTracks > 0 || deletedPlaylists > 0

/** Error de lectura de un archivo de copia que sí existe (no se debe pisar). */
internal class UnreadableArchiveException(cause: Throwable) : Exception(cause)
