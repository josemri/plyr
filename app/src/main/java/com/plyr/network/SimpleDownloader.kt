package com.plyr.network

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SimpleDownloader : Downloader() {

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val TIMEOUT = 30000 // 30 segundos
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection

        return try {
            // Configurar conexión
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.requestMethod = request.httpMethod()

            // Agregar headers personalizados
            request.headers()?.forEach { (key, values) ->
                values.forEach { value ->
                    connection.addRequestProperty(key, value)
                }
            }

            // Enviar datos si es necesario (POST, PUT, etc.)
            request.dataToSend()?.let { data ->
                connection.doOutput = true
                connection.outputStream.use { it.write(data) }
            }

            // Obtener respuesta
            val responseCode = connection.responseCode
            val responseBody = try {
                if (responseCode < 400) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: ""
                }
            } catch (_: Exception) {
                ""
            }

            Response(
                responseCode,
                connection.responseMessage ?: "",
                connection.headerFields.filterKeys { it != null },
                responseBody,
                request.url()
            )

        } finally {
            connection.disconnect()
        }
    }
}