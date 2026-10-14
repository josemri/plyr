package com.plyr.desktop.download

import com.plyr.core.util.Logger
import com.plyr.network.YouTubeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Vuelca el audio de [url] en [sink] y devuelve `true` si completó.
 *
 * Se inyecta para que los tests puedan simular descargas (éxito, fallo, corte a
 * medias) sin tocar la red, y para separar el transporte (OkHttp en producción)
 * de la orquestación de [AudioDownloader].
 */
fun interface AudioFetcher {
    suspend fun fetch(url: String, sink: OutputStream, onProgress: (Float) -> Unit): Boolean
}

private const val TAG = "AudioDownloader"

private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 2000L
private const val BUFFER_SIZE = 8192
private const val CONNECT_TIMEOUT_S = 30L
private const val READ_TIMEOUT_S = 60L

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * Descarga el audio de un vídeo de YouTube a [DownloadedAudioStore] (M16).
 *
 * Es el port de `com.plyr.utils.AudioDownloader`: resuelve una URL fresca en
 * cada intento (las URLs de `googlevideo` caducan), la descarga con OkHttp y
 * escribe el fichero de forma atómica. Si el audio ya estaba descargado, no
 * vuelve a bajarlo.
 *
 * [resolveAudioUrl] y [fetcher] se inyectan para poder testear reintentos,
 * progreso y cancelación sin red.
 */
class AudioDownloader(
    private val store: DownloadedAudioStore = DownloadedAudioStore(),
    private val resolveAudioUrl: suspend (String) -> String? = { YouTubeManager.getAudioUrl(it) },
    private val fetcher: AudioFetcher = OkHttpAudioFetcher(),
    private val maxRetries: Int = MAX_RETRIES,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {

    /**
     * Descarga [videoId] si no estaba ya. [onProgress] recibe 0..1 dentro del
     * fichero actual. Devuelve `true` si el audio queda en el almacén (ya fuera
     * o recién bajado). Se puede cancelar tirando de la corrutina que llama.
     */
    suspend fun download(videoId: String, onProgress: (Float) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            if (store.isDownloaded(videoId)) {
                onProgress(1f)
                return@withContext true
            }

            repeat(maxRetries) { attempt ->
                if (!currentCoroutineContext().isActive) return@withContext false
                if (downloadOnce(videoId, onProgress)) {
                    onProgress(1f)
                    return@withContext true
                }
                if (attempt < maxRetries - 1) delay(retryDelayMs)
            }

            Logger.e(TAG, "Descarga fallida para $videoId")
            false
        }

    /** Un intento: resuelve la URL y vuelca el cuerpo al almacén. */
    private suspend fun downloadOnce(videoId: String, onProgress: (Float) -> Unit): Boolean {
        val audioUrl = resolveAudioUrl(videoId) ?: return false
        return try {
            store.write(videoId) { sink -> fetcher.fetch(audioUrl, sink, onProgress) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "Intento falló para $videoId: ${e.message}")
            false
        }
    }
}

/**
 * Implementación de [AudioFetcher] sobre OkHttp, con las mismas cabeceras que
 * usaba el descargador de Android (`Range: bytes=0-` para que YouTube acepte la
 * petición y `Accept-Encoding: identity` para poder medir el progreso).
 */
class OkHttpAudioFetcher(
    private val client: OkHttpClient = defaultHttpClient(),
) : AudioFetcher {

    override suspend fun fetch(url: String, sink: OutputStream, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity")
                .header("Range", "bytes=0-")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logger.w(TAG, "Respuesta ${response.code} al descargar $url")
                        false
                    } else {
                        val contentLength = response.body.contentLength()
                        response.body.byteStream().use { input ->
                            copy(input, sink, contentLength, onProgress)
                        }
                        true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "Error descargando $url: ${e.message}")
                false
            }
        }

    private fun copy(
        input: InputStream,
        output: OutputStream,
        contentLength: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        var lastPercent = -1
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
}

/** Cliente OkHttp para descargas: sin timeout de llamada (un audio puede tardar). */
private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
    .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
    .build()
