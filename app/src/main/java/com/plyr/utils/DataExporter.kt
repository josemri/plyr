package com.plyr.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.service.CoverImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Resultado de una exportación completada. */
data class ExportSummary(
    val playlistCount: Int,
    val trackCount: Int,
    val coverCount: Int
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
    val trackCount: Int
)

/**
 * DataExporter - Genera un ZIP autocontenido con todas las listas del usuario:
 *
 * ```
 * plyr-export-<fecha>.zip
 * ├── playlists.json      metadatos de listas y pistas
 * └── covers/<id>.jpg     portada de cada lista
 * ```
 *
 * El trabajo se separa en dos fases ([buildBundle] y [writeTo]) para que el
 * mismo contenido sirva tanto para el botón de exportar manual (que escribe en
 * un Uri de SAF de un solo uso) como para la copia automática (que lo escribe en
 * una carpeta persistente, ver [DataSync]).
 *
 * Las portadas se resuelven desde el archivo local (`file://`, las que el
 * usuario recorta) o se descargan de la URL remota guardada por la app —pasando
 * por [CoverCache] para no volver a bajarlas en cada sincronización—. Si una
 * falla, la lista se exporta igualmente sin portada.
 */
object DataExporter {

    private const val TAG = "DataExporter"

    /** Las portadas remotas ya son pequeñas; solo reescalamos las que excedan esto. */
    private const val EXPORT_COVER_MAX_SIDE = 512
    private const val EXPORT_COVER_QUALITY = 85

    /**
     * Recoge el estado actual de la app y lo deja listo para escribir: el JSON
     * del manifiesto, las portadas en bytes y una huella del contenido.
     *
     * @throws EmptyExportException si no hay ninguna lista.
     */
    suspend fun buildBundle(context: Context): ExportBundle = withContext(Dispatchers.IO) {
        val repository = PlaylistLocalRepository(context)
        val entities = repository.getAllPlaylists()
        if (entities.isEmpty()) throw EmptyExportException()

        val accumulator = ExportDigest.Accumulator()
        val takenCoverEntries = mutableSetOf<String>()
        val manifests = mutableListOf<ExportPlaylist>()
        val covers = mutableListOf<CoverPayload>()
        val deletedPlaylistIds = Config.getDeletedPlaylistIds(context)
        // Los borrados de favoritos viajan igual que los de listas (B51).
        val removedLikedTrackKeys = Config.getRemovedLikedTrackKeys(context)

        entities.forEach { entity ->
            val coverBytes = loadCoverBytes(context, entity.imageUrl)
            val coverEntry = if (coverBytes != null) {
                ExportManifest.uniqueCoverEntry(entity.remoteId, takenCoverEntries)
            } else {
                null
            }
            if (coverEntry != null && coverBytes != null) {
                covers += CoverPayload(coverEntry, coverBytes)
            }

            val playlist = ExportPlaylist(
                id = entity.remoteId,
                name = entity.name,
                description = entity.description,
                coverEntry = coverEntry,
                tracks = repository.getTracksByPlaylistSync(entity.remoteId).map { it.toExportTrack() }
            )
            manifests += playlist
            accumulator.addPlaylist(playlist, coverBytes)
        }
        accumulator.addDeletedPlaylistIds(deletedPlaylistIds)
        accumulator.addRemovedLikedTrackKeys(removedLikedTrackKeys)

        ExportBundle(
            manifestJson = ExportManifest.build(
                appVersion = appVersion(context),
                exportedAt = System.currentTimeMillis(),
                playlists = manifests,
                deletedPlaylistIds = deletedPlaylistIds,
                removedLikedTrackKeys = removedLikedTrackKeys
            ),
            covers = covers,
            contentHash = accumulator.hex(),
            playlistCount = manifests.size,
            trackCount = manifests.sumOf { it.trackCount }
        )
    }

    /**
     * Escribe el ZIP de exportación en [destination].
     *
     * @return [Result.success] con el resumen, o [Result.failure] con
     *   [EmptyExportException] si no hay listas, o con la causa del error.
     */
    suspend fun exportTo(
        context: Context,
        destination: Uri
    ): Result<ExportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = buildBundle(context)
            val stream = context.contentResolver.openOutputStream(destination, "w")
                ?: throw IOException("No se pudo abrir $destination para escritura")
            stream.use { writeTo(it, bundle) }

            Log.d(TAG, "Exportadas ${bundle.playlistCount} listas a $destination")
            ExportSummary(
                playlistCount = bundle.playlistCount,
                trackCount = bundle.trackCount,
                coverCount = bundle.covers.size
            )
        }
    }

    // === PORTADAS ===

    /**
     * Devuelve los bytes JPEG de la portada, o null si no hay portada o no se
     * pudo obtener. Las imágenes grandes se reescalan para no inflar el ZIP.
     */
    private suspend fun loadCoverBytes(context: Context, imageUrl: String?): ByteArray? {
        if (imageUrl.isNullOrBlank()) return null

        val raw = when {
            imageUrl.startsWith("file://") || imageUrl.startsWith("/") -> readLocalCover(imageUrl)
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") ->
                CoverCache.load(context, imageUrl)
            else -> null
        }

        if (raw == null || raw.isEmpty()) return null
        return shrinkCover(raw) ?: raw
    }

    private fun readLocalCover(imageUrl: String): ByteArray? = try {
        val path = imageUrl.toUri().path
        path?.let { File(it).takeIf { file -> file.isFile }?.readBytes() }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la portada local: ${e.message}")
        null
    }

    /**
     * Reescala la portada a un cuadrado de [EXPORT_COVER_MAX_SIDE] px. Devuelve
     * null si no hace falta tocar la imagen, para escribir los bytes originales
     * tal cual.
     */
    private fun shrinkCover(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longestSide <= EXPORT_COVER_MAX_SIDE) return null

        var sample = 1
        while (longestSide / (sample * 2) >= EXPORT_COVER_MAX_SIDE) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val output = ByteArrayOutputStream()
        val square = CoverImageManager.resizeToSquare(cropToSquare(decoded), EXPORT_COVER_MAX_SIDE)
        val compressed = square.compress(Bitmap.CompressFormat.JPEG, EXPORT_COVER_QUALITY, output)
        if (!compressed) {
            Log.w(TAG, "No se pudo recomprimir la portada")
            return null
        }
        return output.toByteArray()
    }

    /** Recorta por el centro el mayor cuadrado posible de [bitmap]. */
    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (side == bitmap.width && side == bitmap.height) return bitmap
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    // === ESCRITURA DEL ZIP ===

    /**
     * Vuelca [bundle] como ZIP en [output], que queda abierto: el cierre es de
     * quien lo pasó. Así el mismo contenido sirve para un Uri de SAF y para un
     * archivo temporal en la carpeta de copia.
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

    // === UTILIDADES ===

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfoCompat(context.packageName).versionName ?: "unknown"
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la versión de la app: ${e.message}")
        "unknown"
    }

    private fun TrackEntity.toExportTrack() = ExportTrack(
        position = position,
        name = name,
        artists = ExportManifest.parseArtists(artists),
        remoteTrackId = remoteTrackId,
        youtubeVideoId = youtubeVideoId
    )
}
