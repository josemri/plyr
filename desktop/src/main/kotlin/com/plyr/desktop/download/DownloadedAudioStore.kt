package com.plyr.desktop.download

import com.plyr.core.util.Logger
import com.plyr.desktop.platform.AppPaths
import kotlinx.coroutines.CancellationException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Almacén de audio descargado para reproducción offline (M16).
 *
 * Es el equivalente de escritorio de `com.plyr.utils.DownloadedAudioStore`: en
 * Android vive dentro de la carpeta SAF de sincronización, y aquí son ficheros
 * sueltos (`<videoId>.m4a`) bajo la carpeta de datos del SO
 * ([AppPaths.directories]`.dataDir/audio`), gestionados con `java.nio`.
 *
 * Igual que en Android, **"ya descargado" = existe el fichero**: no hay tabla
 * nueva ni índice que mantener. La carpeta se inyecta para poder testear el
 * almacén en un directorio temporal sin tocar los datos del usuario.
 */
class DownloadedAudioStore(
    /** Carpeta que contiene los `.m4a` (por defecto, `dataDir/audio`). */
    val directory: Path = Path.of(AppPaths.directories.dataDir, DIR_NAME),
) {

    private companion object {
        const val DIR_NAME = "audio"
        const val EXTENSION = "m4a"
        const val SUFFIX = ".$EXTENSION"
        const val TAG = "DownloadedAudioStore"
    }

    /** Resumen del audio offline: cuántas pistas y cuántos bytes ocupan. */
    data class Summary(val count: Int, val bytes: Long)

    /** Fichero que correspondería a [videoId] (exista o no). */
    fun fileFor(videoId: String): Path = directory.resolve("$videoId.$EXTENSION")

    /** ¿Está [videoId] descargado? Basta con que exista el fichero. */
    fun isDownloaded(videoId: String): Boolean =
        videoId.isNotBlank() && Files.isRegularFile(fileFor(videoId))

    /** Ruta del fichero de [videoId] si está descargado, o `null`. */
    fun localPath(videoId: String): Path? = fileFor(videoId).takeIf { isDownloaded(videoId) }

    /** URI `file://` reproducible de [videoId] si está descargado, o `null`. */
    fun localUri(videoId: String): String? = localPath(videoId)?.toUri()?.toString()

    /**
     * Escribe el audio de [videoId] de forma atómica: [produce] vuelca en el
     * stream temporal y devuelve `true` si completó; solo entonces se renombra
     * al fichero final. Un fallo, una cancelación o un volcado vacío no dejan un
     * `.m4a` a medias que luego parezca "descargado".
     */
    suspend fun write(videoId: String, produce: suspend (OutputStream) -> Boolean): Boolean {
        if (videoId.isBlank()) return false
        return try {
            Files.createDirectories(directory)
            val target = fileFor(videoId)
            val temp = Files.createTempFile(directory, "$videoId-", ".part")
            commitOrDiscard(produce, temp, target, videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo preparar la carpeta de audio: ${e.message}")
            false
        }
    }

    /** Volcado al temporal + renombrado atómico, limpiando siempre el temporal. */
    private suspend fun commitOrDiscard(
        produce: suspend (OutputStream) -> Boolean,
        temp: Path,
        target: Path,
        videoId: String,
    ): Boolean = try {
        val completed = Files.newOutputStream(temp).use { produce(it) }
        val size = runCatching { Files.size(temp) }.getOrDefault(0L)
        if (!completed || size == 0L) {
            Files.deleteIfExists(temp)
            false
        } else {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            true
        }
    } catch (e: CancellationException) {
        Files.deleteIfExists(temp)
        throw e
    } catch (e: Exception) {
        Files.deleteIfExists(temp)
        Logger.w(TAG, "Fallo escribiendo audio de $videoId: ${e.message}")
        false
    }

    /** Cuántas pistas hay descargadas y cuánto ocupan (para el diálogo de gestión). */
    fun summary(): Summary {
        val files = audioFiles()
        return Summary(files.size, files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) })
    }

    /** Borra el audio de [videoId] si existe. Devuelve si había algo que borrar. */
    fun delete(videoId: String): Boolean =
        runCatching { Files.deleteIfExists(fileFor(videoId)) }.getOrDefault(false)

    /** Borra **todo** el audio offline. Devuelve si se pudo borrar todo. */
    fun deleteAll(): Boolean = audioFiles().map { file ->
        runCatching { Files.deleteIfExists(file) }.getOrDefault(false)
    }.all { it }

    /** Todos los `.m4a` de la carpeta (vacío si aún no existe). */
    private fun audioFiles(): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(SUFFIX) }
                .toList()
        }
    }
}
