package com.plyr.desktop.utils

import com.plyr.core.util.Logger
import com.plyr.desktop.platform.AppPaths
import com.plyr.utils.CoverCacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

private const val TAG = "CoverCache"
private const val DIR_NAME = "export-covers"
private const val CACHE_SUFFIX = ".jpg"
private const val TIMEOUT_SECONDS = 15L

/**
 * Tope de portada cacheada. Una imagen de portada son unos pocos KB con
 * cualquier compresión razonable; este límite solo existe para que una respuesta
 * anómala del servidor no llene el almacenamiento.
 */
private const val MAX_CACHED_BYTES = 2L * 1024 * 1024

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}

/**
 * Caché en disco de las portadas remotas descargadas (M9).
 *
 * Es el equivalente de escritorio de `com.plyr.utils.CoverCache` en Android:
 * descarga una portada remota una sola vez y la guarda en la carpeta de caché
 * del SO ([AppPaths.directories]`.cacheDir`) para no repetir la petición HTTP en
 * cada copia de seguridad. El nombre de cada archivo lo decide [CoverCacheKey] en
 * `:core`, compartido con Android.
 *
 * La carpeta y la descarga se inyectan para poder testear el almacenamiento sin
 * tocar la red ni el disco del usuario.
 */
class CoverCache(
    private val directory: Path = Path.of(AppPaths.directories.cacheDir, DIR_NAME),
    private val download: suspend (String) -> ByteArray? = { httpDownload(it) },
) {

    /**
     * Devuelve los bytes de la portada en [url], descargándola solo si no está
     * ya en la caché. Null si no se pudo obtener; la lista se exporta igual sin
     * portada, así que un fallo aquí nunca debe abortar una sincronización.
     */
    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = cacheFile(url)
        readCached(file)?.let { return@withContext it }

        val downloaded = download(url) ?: return@withContext null
        store(file, downloaded)
        downloaded
    }

    /** Ruta del archivo de caché de [url] (no implica que exista). */
    fun cacheFile(url: String): Path =
        directory.resolve(CoverCacheKey.cacheKey(url) + CACHE_SUFFIX)

    private fun readCached(file: Path): ByteArray? {
        if (!Files.isRegularFile(file)) return null
        val size = runCatching { Files.size(file) }.getOrDefault(0L)
        if (size !in 1..MAX_CACHED_BYTES) return null
        return try {
            Files.readAllBytes(file)
        } catch (e: IOException) {
            Logger.w(TAG, "No se pudo leer la portada cacheada: ${e.message}")
            null
        }
    }

    /**
     * Guarda la portada de forma atómica: se escribe a un temporal y se renombra.
     * Si la app muere a medias, un archivo parcial no debe quedar cacheado como
     * válido para siempre.
     */
    private fun store(file: Path, bytes: ByteArray) {
        try {
            Files.createDirectories(file.parent)
            val temp = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
            Files.write(temp, bytes)
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo cachear la portada: ${e.message}")
        }
    }
}

/** Descarga [url] con OkHttp, devolviendo null ante cualquier fallo o tamaño anómalo. */
private suspend fun httpDownload(url: String): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.w(TAG, "Portada remota ${response.code} para $url")
                return@use null
            }
            val body = response.body
            val declared = body.contentLength()
            if (declared > MAX_CACHED_BYTES) {
                Logger.w(TAG, "Portada demasiado grande ($declared bytes), no se cachea")
                return@use null
            }
            body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_CACHED_BYTES }
        }
    } catch (e: Exception) {
        Logger.w(TAG, "Error descargando portada $url: ${e.message}")
        null
    }
}
