package com.plyr.desktop.sync

import com.plyr.core.util.Logger
import com.plyr.database.TrackRecord
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.database.TombstoneStore
import com.plyr.desktop.utils.CoverCache
import com.plyr.utils.ExportDigest
import com.plyr.utils.ExportManifest
import com.plyr.utils.ExportPlaylist
import com.plyr.utils.ExportTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val TAG = "DataExporter"

/** Resultado de una exportación completada. */
data class ExportSummary(
    val playlistCount: Int,
    val trackCount: Int,
    val coverCount: Int,
)

/** Se lanza cuando el usuario no tiene ninguna lista que exportar. */
class EmptyExportException : Exception("nothing_to_export")

/** Una portada ya resuelta, lista para escribir en el ZIP. */
data class CoverPayload(val entryName: String, val bytes: ByteArray)

/**
 * Todo lo necesario para escribir un ZIP, más la huella de su contenido.
 *
 * [contentHash] es lo que permite a [DataSync] no reescribir el archivo en la
 * carpeta cuando los datos no han cambiado: cubre el contenido real (listas,
 * pistas y bytes de las portadas) pero no la marca de tiempo, que cambia en
 * cada ejecución y siempre daría "ha cambiado".
 */
class ExportBundle(
    val manifestJson: String,
    val covers: List<CoverPayload>,
    val contentHash: String,
    val playlistCount: Int,
    val trackCount: Int,
)

/**
 * DataExporter - Genera un ZIP autocontenido con todas las listas del usuario:
 *
 * ```
 * plyr-sync.zip
 * ├── playlists.json      metadatos de listas y pistas
 * └── covers/<id>.jpg     portada de cada lista
 * ```
 *
 * Es el equivalente de escritorio de `DataExporter` de Android, pero sin SAF:
 * trabaja con `java.nio`. El trabajo se separa en dos fases ([buildBundle] y
 * [writeTo]) para que el mismo contenido sirva tanto para la exportación manual
 * como para la copia automática (ver [DataSync]).
 *
 * Las portadas se resuelven desde el archivo local (`file://`, las que el usuario
 * recorta) o se descargan de la URL remota guardada por la app —pasando por
 * [CoverCache] para no volver a bajarlas en cada sincronización—. Si una falla,
 * la lista se exporta igualmente sin portada.
 */
class DataExporter(
    private val repository: PlaylistRepository,
    private val tombstones: TombstoneStore,
    private val coverCache: CoverCache = CoverCache(),
    private val appVersion: String = "desktop",
) {

    /**
     * Recoge el estado actual y lo deja listo para escribir: el JSON del
     * manifiesto, las portadas en bytes y una huella del contenido.
     *
     * @throws EmptyExportException si no hay ninguna lista.
     */
    suspend fun buildBundle(): ExportBundle = withContext(Dispatchers.IO) {
        val playlists = repository.allPlaylists()
        if (playlists.isEmpty()) throw EmptyExportException()

        val accumulator = ExportDigest.Accumulator()
        val takenCoverEntries = mutableSetOf<String>()
        val manifests = mutableListOf<ExportPlaylist>()
        val covers = mutableListOf<CoverPayload>()
        val deletedPlaylistIds = tombstones.deletedPlaylistIds()
        // Los borrados de favoritos viajan igual que los de listas (B51).
        val removedLikedTrackKeys = tombstones.removedLikedTrackKeys()

        playlists.forEach { playlist ->
            val coverBytes = loadCoverBytes(playlist.imageUrl)
            val coverEntry = if (coverBytes != null) {
                ExportManifest.uniqueCoverEntry(playlist.remoteId, takenCoverEntries)
            } else {
                null
            }
            if (coverEntry != null && coverBytes != null) {
                covers += CoverPayload(coverEntry, coverBytes)
            }

            val exported = ExportPlaylist(
                id = playlist.remoteId,
                name = playlist.name,
                description = playlist.description,
                coverEntry = coverEntry,
                tracks = repository.tracksByPlaylist(playlist.remoteId).map { it.toExportTrack() },
                source = playlist.source.name,
                sourceId = playlist.sourceId,
            )
            manifests += exported
            accumulator.addPlaylist(exported, coverBytes)
        }
        accumulator.addDeletedPlaylistIds(deletedPlaylistIds)
        accumulator.addRemovedLikedTrackKeys(removedLikedTrackKeys)

        ExportBundle(
            manifestJson = ExportManifest.build(
                appVersion = appVersion,
                exportedAt = System.currentTimeMillis(),
                playlists = manifests,
                deletedPlaylistIds = deletedPlaylistIds,
                removedLikedTrackKeys = removedLikedTrackKeys,
            ),
            covers = covers,
            contentHash = accumulator.hex(),
            playlistCount = manifests.size,
            trackCount = manifests.sumOf { it.trackCount },
        )
    }

    /**
     * Escribe el ZIP de exportación en [destination].
     *
     * @return [Result.success] con el resumen, o [Result.failure] con
     *   [EmptyExportException] si no hay listas, o con la causa del error.
     */
    suspend fun exportTo(destination: Path): Result<ExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = buildBundle()
            Files.newOutputStream(destination).use { writeTo(it, bundle) }

            Logger.d(TAG, "Exportadas ${bundle.playlistCount} listas a $destination")
            ExportSummary(
                playlistCount = bundle.playlistCount,
                trackCount = bundle.trackCount,
                coverCount = bundle.covers.size,
            )
        }
    }

    // === PORTADAS ===

    /**
     * Devuelve los bytes JPEG de la portada, o null si no hay portada o no se
     * pudo obtener. Las imágenes grandes se reescalan para no inflar el ZIP.
     */
    private suspend fun loadCoverBytes(imageUrl: String?): ByteArray? {
        if (imageUrl.isNullOrBlank()) return null

        val raw = when {
            imageUrl.startsWith("file://") || imageUrl.startsWith("/") -> readLocalCover(imageUrl)
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> coverCache.load(imageUrl)
            else -> null
        }

        if (raw == null || raw.isEmpty()) return null
        return CoverImages.shrinkToSquare(raw) ?: raw
    }

    private fun readLocalCover(imageUrl: String): ByteArray? = try {
        val path = when {
            imageUrl.startsWith("file://") -> Path.of(URI(imageUrl))
            else -> Path.of(imageUrl)
        }
        if (Files.isRegularFile(path)) Files.readAllBytes(path) else null
    } catch (e: Exception) {
        Logger.w(TAG, "No se pudo leer la portada local: ${e.message}")
        null
    }

    // === ESCRITURA DEL ZIP ===

    /**
     * Vuelca [bundle] como ZIP en [output], que queda abierto: el cierre es de
     * quien lo pasó. Así el mismo contenido sirve para un archivo suelto y para
     * el temporal de la carpeta de copia.
     */
    fun writeTo(output: OutputStream, bundle: ExportBundle) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(ExportManifest.FILE_NAME))
            zip.write(bundle.manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            bundle.covers.forEach { cover ->
                zip.putNextEntry(ZipEntry(cover.entryName))
                zip.write(cover.bytes)
                zip.closeEntry()
            }
        }
    }

    private fun TrackRecord.toExportTrack() = ExportTrack(
        position = position,
        name = name,
        artists = ExportManifest.parseArtists(artists),
        remoteTrackId = remoteTrackId,
        youtubeVideoId = youtubeVideoId,
    )
}
