package com.plyr.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.service.CoverImageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
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

/**
 * DataExporter - Genera un ZIP autocontenido con todas las listas del usuario:
 *
 * ```
 * plyr-export-<fecha>.zip
 * ├── playlists.json      metadatos de listas y pistas
 * └── covers/<id>.jpg     portada de cada lista
 * ```
 *
 * El archivo se escribe en el [Uri] que devuelve SAF (`CreateDocument`), así que
 * no hace falta ningún permiso de almacenamiento. Las portadas se resuelven desde
 * el archivo local (`file://`, las que el usuario recorta) o se descargan de la
 * URL remota guardada por la app; si una falla, la lista se exporta igualmente
 * sin portada.
 */
object DataExporter {

    private const val TAG = "DataExporter"

    /** Las portadas remotas ya son pequeñas; solo reescalamos las que excedan esto. */
    private const val EXPORT_COVER_MAX_SIDE = 512
    private const val EXPORT_COVER_QUALITY = 85
    private const val COVER_TIMEOUT_SECONDS = 15L

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(COVER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
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
            val repository = PlaylistLocalRepository(context)
            val entities = repository.getAllPlaylists()
            if (entities.isEmpty()) throw EmptyExportException()

            val takenCoverEntries = mutableSetOf<String>()
            val payloads = entities.map { entity ->
                val coverBytes = loadCoverBytes(entity.imageUrl)
                val coverEntry = if (coverBytes != null) {
                    ExportManifest.uniqueCoverEntry(entity.remoteId, takenCoverEntries)
                } else {
                    null
                }
                PlaylistPayload(
                    manifest = ExportPlaylist(
                        id = entity.remoteId,
                        name = entity.name,
                        description = entity.description,
                        coverEntry = coverEntry,
                        tracks = repository.getTracksByPlaylistSync(entity.remoteId).map { it.toExportTrack() }
                    ),
                    coverEntry = coverEntry,
                    coverBytes = coverBytes
                )
            }

            val manifestJson = ExportManifest.build(
                appVersion = appVersion(context),
                exportedAt = System.currentTimeMillis(),
                playlists = payloads.map { it.manifest }
            )

            writeArchive(context, destination, manifestJson, payloads)

            Log.d(TAG, "Exportadas ${payloads.size} listas a $destination")
            ExportSummary(
                playlistCount = payloads.size,
                trackCount = payloads.sumOf { it.manifest.trackCount },
                coverCount = payloads.count { it.coverBytes != null }
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
            imageUrl.startsWith("http://") || imageUrl.startsWith("https://") -> downloadCover(imageUrl)
            else -> null
        }

        if (raw == null || raw.isEmpty()) return null
        return shrinkCover(raw) ?: raw
    }

    private fun readLocalCover(imageUrl: String): ByteArray? = try {
        val path = Uri.parse(imageUrl).path
        path?.let { File(it).takeIf { file -> file.isFile }?.readBytes() }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la portada local: ${e.message}")
        null
    }

    private suspend fun downloadCover(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Portada remota ${response.code} para $url")
                    return@use null
                }
                response.body.bytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error descargando portada $url: ${e.message}")
            null
        }
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

    private fun writeArchive(
        context: Context,
        destination: Uri,
        manifestJson: String,
        payloads: List<PlaylistPayload>
    ) {
        val stream = context.contentResolver.openOutputStream(destination, "w")
            ?: throw IOException("No se pudo abrir $destination para escritura")

        stream.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(ExportManifest.FILE_NAME))
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                payloads.forEach { payload ->
                    val entryName = payload.coverEntry ?: return@forEach
                    val bytes = payload.coverBytes ?: return@forEach
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(bytes)
                    zip.closeEntry()
                }
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

    /** Una lista del ZIP: su entrada en el manifiesto más su portada en bytes. */
    private class PlaylistPayload(
        val manifest: ExportPlaylist,
        val coverEntry: String?,
        val coverBytes: ByteArray?
    )
}
