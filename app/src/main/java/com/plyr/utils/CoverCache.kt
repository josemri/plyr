package com.plyr.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * CoverCache - Caché en disco de las portadas remotas descargadas.
 *
 * Existe por un motivo concreto: sin ella, cada sincronización volvería a
 * descargar por red **todas** las portadas de todas las listas. Con la copia
 * automática eso es insostenible (una lista de 200 canciones son 200 peticiones
 * HTTP cada vez que el usuario sale de la app), así que lo que se descarga una
 * vez se guarda en `filesDir/export-covers` y se reutiliza mientras exista.
 *
 * Solo cachea URLs remotas: las portadas locales ya están en el disco y
 * [DataExporter] las lee directamente.
 */
object CoverCache {

    private const val TAG = "CoverCache"
    private const val DIR_NAME = "export-covers"
    private const val CACHE_SUFFIX = ".jpg"
    private const val TIMEOUT_SECONDS = 15L

    /**
     * Tope de portada cacheada. Una imagen de portada son unos pocos KB con
     * cualquier compresión razonable; este límite solo existe para que una
     * respuesta anómala del servidor no llene el almacenamiento.
     */
    private const val MAX_CACHED_BYTES = 2L * 1024 * 1024

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Devuelve los bytes de la portada en [url], descargándola solo si no está
     * ya en la caché. Null si no se pudo obtener; la lista se exporta igual sin
     * portada, así que un fallo aquí nunca debe abortar una sincronización.
     */
    suspend fun load(context: Context, url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = cacheFile(context, url)
        if (file != null && file.isFile && file.length() in 1..MAX_CACHED_BYTES) {
            try {
                return@withContext file.readBytes()
            } catch (e: IOException) {
                Log.w(TAG, "No se pudo leer la portada cacheada: ${e.message}")
            }
        }

        val downloaded = download(url) ?: return@withContext null
        store(file, downloaded)
        downloaded
    }

    /**
     * Nombre del archivo de caché para [url]. Se usa SHA-256 porque las URLs de
     * portada de YouTube son largas y contienen caracteres que no valen como
     * nombre de archivo.
     *
     * Expuesto para tests: es la única lógica de este objeto sin Android.
     */
    fun cacheKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
    }

    // === INTERNOS ===

    private fun cacheFile(context: Context, url: String): File? = try {
        File(context.filesDir, DIR_NAME).apply { mkdirs() }.let { dir ->
            File(dir, cacheKey(url) + CACHE_SUFFIX)
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo preparar la carpeta de cache: ${e.message}")
        null
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Portada remota ${response.code} para $url")
                    return@use null
                }
                val body = response.body
                val declared = body.contentLength()
                if (declared > MAX_CACHED_BYTES) {
                    Log.w(TAG, "Portada demasiado grande ($declared bytes), no se cachea")
                    return@use null
                }
                body.bytes().takeIf { it.isNotEmpty() && it.size <= MAX_CACHED_BYTES }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error descargando portada $url: ${e.message}")
            null
        }
    }

    /**
     * Guarda la portada en disco de forma atómica: se escribe a un temporal y
     * se renombra. Si la app muere a medias, el rename de un archivo parcial
     * dejaría una portada corrupta cacheada para siempre.
     */
    private fun store(file: File?, bytes: ByteArray) {
        if (file == null) return
        try {
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                // renameTo falla si el destino existe en algunos FS
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cachear la portada: ${e.message}")
        }
    }
}
