package com.plyr.utils

import android.content.Context
import android.util.Log
import com.plyr.database.DownloadedTrackEntity
import com.plyr.database.PlaylistDatabase
import com.plyr.network.YouTubeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * DownloadManager - Gestor de descargas de audio
 *
 * Descarga manual con headers correctos para evitar bloqueos de YouTube.
 */
object DownloadManager {

    private const val TAG = "DownloadManager"
    private const val DOWNLOADS_FOLDER = "plyr_downloads"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 2000L
    private const val TIMEOUT_MS = 30000
    private const val BUFFER_SIZE = 8192

    /**
     * Descarga una canción desde YouTube y la guarda localmente.
     */
    suspend fun downloadTrack(
        context: Context,
        spotifyTrackId: String?,
        youtubeVideoId: String,
        trackName: String,
        artists: String,
        onProgress: (Int) -> Unit = {},
        onComplete: (Boolean, String?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Iniciando descarga: $trackName - $artists")

                // Verificar si ya está descargada usando YouTube ID
                val database = PlaylistDatabase.getDatabase(context)
                val existingTrack = database.downloadedTrackDao().getDownloadedTrackByYoutubeId(youtubeVideoId)
                if (existingTrack != null) {
                    Log.d(TAG, "Track ya descargado previamente")
                    withContext(Dispatchers.Main) {
                        onComplete(false, "Track already downloaded")
                    }
                    return@withContext
                }

                onProgress(10)

                // Crear directorio de descargas
                val downloadsDir = File(context.getExternalFilesDir(null), DOWNLOADS_FOLDER)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }

                val safeFileName = "${trackName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}_${System.currentTimeMillis()}.m4a"
                val outputFile = File(downloadsDir, safeFileName)

                // Intentar descargar con reintentos
                var lastError: String? = null
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        Log.d(TAG, "Intento $attempt/$MAX_RETRIES")

                        // Obtener URL fresca de YouTube
                        val audioUrl = YouTubeManager.getAudioUrl(youtubeVideoId)
                        if (audioUrl == null) {
                            lastError = "Failed to get audio URL"
                            Log.e(TAG, "No se pudo obtener URL")
                            if (attempt < MAX_RETRIES) {
                                delay(RETRY_DELAY_MS)
                                continue
                            }
                            break
                        }

                        onProgress(30)
                        Log.d(TAG, "Descargando desde URL (longitud: ${audioUrl.length})")

                        // Configurar conexión con headers que imitan curl/navegador
                        val url = URL(audioUrl)
                        val connection = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = TIMEOUT_MS
                            readTimeout = TIMEOUT_MS

                            // Headers críticos para que YouTube acepte la descarga
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            setRequestProperty("Accept", "*/*")
                            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                            setRequestProperty("Accept-Encoding", "identity")
                            setRequestProperty("Range", "bytes=0-")

                            doInput = true
                            instanceFollowRedirects = true
                        }

                        connection.connect()

                        val responseCode = connection.responseCode
                        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                            lastError = "HTTP error: $responseCode"
                            Log.e(TAG, "Error HTTP: $responseCode")
                            connection.disconnect()
                            if (attempt < MAX_RETRIES) {
                                delay(RETRY_DELAY_MS)
                                continue
                            }
                            break
                        }

                        val fileLength = connection.contentLengthLong
                        Log.d(TAG, "Tamaño del archivo: $fileLength bytes")

                        // Descargar archivo
                        var downloadSuccess = false
                        connection.inputStream.use { input ->
                            FileOutputStream(outputFile).use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytesRead: Int
                                var totalBytesRead = 0L
                                var lastProgress = 30

                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead

                                    if (fileLength > 0) {
                                        val progress = 30 + ((totalBytesRead * 65) / fileLength).toInt()
                                        if (progress != lastProgress) {
                                            withContext(Dispatchers.Main) {
                                                onProgress(progress)
                                            }
                                            lastProgress = progress
                                        }
                                    }
                                }
                                downloadSuccess = true
                            }
                        }

                        connection.disconnect()

                        if (!downloadSuccess || !outputFile.exists() || outputFile.length() == 0L) {
                            lastError = "Download incomplete"
                            Log.e(TAG, "Descarga incompleta")
                            outputFile.delete()
                            if (attempt < MAX_RETRIES) {
                                delay(RETRY_DELAY_MS)
                                continue
                            }
                            break
                        }

                        onProgress(95)

                        // Guardar en base de datos
                        val downloadedTrack = DownloadedTrackEntity(
                            id = "${youtubeVideoId}_${System.currentTimeMillis()}",
                            spotifyTrackId = spotifyTrackId ?: "",
                            name = trackName,
                            artists = artists,
                            youtubeVideoId = youtubeVideoId,
                            localFilePath = outputFile.absolutePath
                        )

                        database.downloadedTrackDao().insertDownloadedTrack(downloadedTrack)

                        onProgress(100)

                        Log.d(TAG, "✓ Descarga completada: ${outputFile.length()} bytes")
                        Log.d(TAG, "✓ Archivo guardado en: ${outputFile.absolutePath}")
                        Log.d(TAG, "✓ Archivo existe: ${outputFile.exists()}")
                        Log.d(TAG, "✓ Archivo legible: ${outputFile.canRead()}")
                        Log.d(TAG, "✓ Guardado en BD con ID: ${downloadedTrack.id}")

                        withContext(Dispatchers.Main) {
                            onComplete(true, null)
                        }
                        return@withContext

                    } catch (e: Exception) {
                        lastError = e.message ?: "Unknown error"
                        Log.e(TAG, "Error en intento $attempt: ${e.message}", e)
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                        if (attempt < MAX_RETRIES) {
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }

                // Todos los intentos fallaron
                Log.e(TAG, "✗ Descarga falló después de $MAX_RETRIES intentos")
                withContext(Dispatchers.Main) {
                    onComplete(false, lastError ?: "Download failed")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error crítico", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Elimina una canción descargada.
     */
    suspend fun deleteDownloadedTrack(
        context: Context,
        track: DownloadedTrackEntity
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(track.localFilePath)
                if (file.exists()) {
                    file.delete()
                }

                val database = PlaylistDatabase.getDatabase(context)
                database.downloadedTrackDao().deleteDownloadedTrack(track)

                Log.d(TAG, "Track eliminado: ${track.name}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando track", e)
                false
            }
        }
    }

    /**
     * Obtiene el directorio de descargas.
     */
    fun getDownloadsDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), DOWNLOADS_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}
