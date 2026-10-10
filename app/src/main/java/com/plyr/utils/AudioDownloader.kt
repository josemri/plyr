package com.plyr.utils

import android.content.Context
import android.util.Log
import com.plyr.network.YouTubeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga el audio de un vídeo de YouTube al [DownloadedAudioStore].
 *
 * Portado del antiguo `DownloadManager` (borrado junto al feature "local"):
 * headers de navegador y `Range: bytes=0-` para que YouTube acepte la descarga,
 * con reintentos. Resuelve la URL fresca en cada intento porque las URLs de
 * googlevideo caducan.
 */
object AudioDownloader {

    private const val TAG = "AudioDownloader"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 2000L
    private const val TIMEOUT_MS = 30000
    private const val BUFFER_SIZE = 8192

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Descarga [videoId] si no estaba ya. [onProgress] recibe 0..1 dentro del
     * fichero actual. Devuelve true si el audio queda en el almacén (ya fuera o
     * recién bajado).
     */
    suspend fun download(context: Context, videoId: String, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (DownloadedAudioStore.localUri(context, videoId) != null) {
                onProgress(1f)
                return@withContext true
            }

            for (attempt in 1..MAX_RETRIES) {
                if (!currentCoroutineContext().isActive) return@withContext false
                when (downloadOnce(context, videoId, onProgress)) {
                    true -> {
                        onProgress(1f)
                        return@withContext true
                    }
                    // `false` no se usa: todos los fallos son reintentables (null)
                    false -> if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                    null -> if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS)
                }
            }

            Log.e(TAG, "Descarga fallida para $videoId")
            false
        }

    /**
     * Un intento de descarga. `true` = éxito; `null` = fallo (reintentable).
     * Se usa un `Boolean?` para no arrastrar un flag de "definitivo": todos los
     * fallos salvo la cancelación se pueden reintentar.
     */
    private suspend fun downloadOnce(
        context: Context,
        videoId: String,
        onProgress: (Float) -> Unit
    ): Boolean? {
        val audioUrl = YouTubeManager.getAudioUrl(videoId) ?: return null
        val connection = openConnection(audioUrl)
        return try {
            connection.connect()
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                null
            } else {
                val contentLength = connection.contentLengthLong
                val ok = connection.inputStream.use { input ->
                    DownloadedAudioStore.write(context, videoId) { out ->
                        copy(input, out, contentLength, onProgress)
                    }
                }
                if (ok) true else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Intento falló para $videoId: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun copy(
        input: InputStream,
        output: OutputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var lastPercent = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
            if (contentLength > 0) {
                val percent = ((total * 100) / contentLength).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent / 100f)
                }
            }
        }
    }

    private fun openConnection(audioUrl: String): HttpURLConnection =
        (URL(audioUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", "bytes=0-")
            doInput = true
            instanceFollowRedirects = true
        }
}