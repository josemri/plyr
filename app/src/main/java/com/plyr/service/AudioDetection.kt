package com.plyr.service

import android.content.Context
import com.plyr.utils.Config
import com.geecko.fpcalc.FpCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import android.net.Uri
import java.io.FileOutputStream

data class AudioDetectionResult(
    val title: String,
    val artist: String,
    val precision: Int,
    val recordedDuration: Int,
    val realDuration: Int
)

suspend fun detectAudioFromFile(context: Context, audioFile: File): AudioDetectionResult? = withContext(Dispatchers.IO) {
    try {
        // Verificar que haya API Key configurada
        val apiKey = Config.getAcoustidApiKey(context) ?: return@withContext null

        // 1. Extraer fingerprint usando fpcalc
        val args = arrayOf("-length", "16", audioFile.absolutePath)
        val result = FpCalc.fpCalc(args)

        // 2. Parsear el resultado de fpcalc
        val lines = result.split("\n")
        var fingerprint = ""
        var duration = 0

        for (line in lines) {
            when {
                line.startsWith("FINGERPRINT=") -> {
                    fingerprint = line.substringAfter("FINGERPRINT=").trim()
                }
                line.startsWith("DURATION=") -> {
                    duration = line.substringAfter("DURATION=").trim().toIntOrNull() ?: 0
                }
            }
        }

        if (fingerprint.isEmpty() || duration == 0) {
            return@withContext null
        }

        // 3. Enviar a AcoustID API
        val client = OkHttpClient()
        val url = "https://api.acoustid.org/v2/lookup?" +
                "client=$apiKey" +
                "&meta=recordings+releasegroups+compress" +
                "&duration=$duration" +
                "&fingerprint=$fingerprint"

        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()

        if (!response.isSuccessful || body.isNullOrEmpty()) {
            return@withContext null
        }

        // 4. Parsear respuesta JSON
        val jsonResponse = JSONObject(body)
        val results = jsonResponse.getJSONArray("results")

        if (results.length() == 0) {
            return@withContext null
        }

        val bestResult = results.getJSONObject(0)
        val score = bestResult.getDouble("score")

        val recordings = bestResult.getJSONArray("recordings")
        if (recordings.length() == 0) {
            return@withContext null
        }

        val recording = recordings.getJSONObject(0)
        val songTitle = recording.getString("title")
        val songDuration = recording.getInt("duration")

        val artists = recording.getJSONArray("artists")
        val artistName = if (artists.length() > 0) {
            artists.getJSONObject(0).getString("name")
        } else {
            "Unknown"
        }

        // 5. Retornar resultado
        return@withContext AudioDetectionResult(
            title = songTitle,
            artist = artistName,
            precision = (score * 100).toInt(),
            recordedDuration = duration,
            realDuration = songDuration
        )
    } catch (e: Exception) {
        return@withContext null
    }

}

suspend fun detectAudioFromUri(context: Context, uri: Uri): AudioDetectionResult? = withContext(Dispatchers.IO) {
    try {
        // Crear archivo temporal para análisis
        val tempFile = File.createTempFile("temp_audio_", ".tmp", context.cacheDir)

        try {
            // Copiar contenido del URI al archivo temporal
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Usar la función existente con el archivo temporal
            return@withContext detectAudioFromFile(context, tempFile)
        } finally {
            // Limpiar archivo temporal
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    } catch (e: Exception) {
        return@withContext null
    }
}