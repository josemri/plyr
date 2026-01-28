package com.plyr.network

import android.util.Log
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

class SimpleDownloader private constructor() : Downloader() {

    companion object {
        private const val TAG = "SimpleDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val YOUTUBE_RESTRICTED_MODE_COOKIE = "PREF=f2=8000000"
        private const val YOUTUBE_DOMAIN = "youtube.com"

        @Volatile
        private var instance: SimpleDownloader? = null

        fun getInstance(): SimpleDownloader {
            return instance ?: synchronized(this) {
                instance ?: SimpleDownloader().also { instance = it }
            }
        }
    }

    private val cookies = mutableMapOf<String, String>()
    private val client: OkHttpClient

    init {
        // Configurar OkHttp client como lo hace NewPipe
        client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        // Agregar cookie de modo restringido de YouTube por defecto
        setCookie("youtube_restricted_mode_key", YOUTUBE_RESTRICTED_MODE_COOKIE)
        Log.d(TAG, "✅ SimpleDownloader inicializado con OkHttp")
    }

    fun setCookie(key: String, cookie: String) {
        cookies[key] = cookie
    }

    fun getCookie(key: String): String? {
        return cookies[key]
    }

    fun removeCookie(key: String) {
        cookies.remove(key)
    }

    private fun getCookies(url: String): String {
        val youtubeCookie = if (url.contains(YOUTUBE_DOMAIN)) {
            getCookie("youtube_restricted_mode_key")
        } else {
            null
        }

        // Combinar todas las cookies relevantes
        return listOfNotNull(youtubeCookie, getCookie("recaptcha_cookies_key"))
            .flatMap { it.split("; ") }
            .distinct()
            .joinToString("; ")
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        Log.d(TAG, "🌐 Ejecutando petición: ${request.httpMethod()} ${request.url()}")

        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        // Crear RequestBody si hay datos
        val requestBody = if (dataToSend != null) {
            dataToSend.toRequestBody(null)
        } else {
            null
        }

        // Construir request de OkHttp
        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        // Agregar cookies
        val cookiesString = getCookies(url)
        if (cookiesString.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", cookiesString)
            Log.d(TAG, "🍪 Cookies: $cookiesString")
        }

        // Agregar headers personalizados
        headers?.forEach { (headerName, headerValueList) ->
            requestBuilder.removeHeader(headerName)
            headerValueList.forEach { headerValue ->
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        // Log de headers
        val builtRequest = requestBuilder.build()
        Log.d(TAG, "📋 Headers enviados:")
        builtRequest.headers.forEach { (name, value) ->
            Log.d(TAG, "   $name: $value")
        }

        // Ejecutar petición
        return try {
            client.newCall(builtRequest).execute().use { response ->
                val responseCode = response.code
                Log.d(TAG, "📥 Código de respuesta: $responseCode")

                // Detectar reCaptcha challenge
                if (responseCode == 429) {
                    Log.e(TAG, "⚠️ reCaptcha Challenge detectado (429)")
                    throw ReCaptchaException("reCaptcha Challenge requested", url)
                }

                // Leer body
                val responseBodyString: String = response.body?.use { body: ResponseBody ->
                    body.string()
                } ?: ""

                if (responseCode < 400) {
                    Log.d(TAG, "✅ Respuesta exitosa: ${responseBodyString.length} caracteres")
                    // Log adicional para peticiones del player de YouTube
                    if (url.contains("/youtubei/v1/player")) {
                        Log.d(TAG, "🎬 Respuesta del Player API:")
                        // Buscar playabilityStatus en la respuesta
                        if (responseBodyString.contains("playabilityStatus")) {
                            val statusStart = responseBodyString.indexOf("\"playabilityStatus\"")
                            if (statusStart != -1) {
                                val statusEnd = responseBodyString.indexOf("}", statusStart) + 1
                                val status = responseBodyString.substring(statusStart, minOf(statusEnd + 200, responseBodyString.length))
                                Log.d(TAG, "   PlayabilityStatus: ${status.take(500)}")
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Error ($responseCode): ${responseBodyString.take(500)}")
                }

                // Obtener URL final (después de redirecciones)
                val latestUrl = response.request.url.toString()
                if (latestUrl != url) {
                    Log.d(TAG, "🔄 Redirección: $latestUrl")
                }

                // Convertir headers de OkHttp a formato de NewPipe
                val headersMap = mutableMapOf<String, MutableList<String>>()
                response.headers.forEach { (name, value) ->
                    headersMap.getOrPut(name) { mutableListOf() }.add(value)
                }

                Response(
                    responseCode,
                    response.message,
                    headersMap,
                    responseBodyString,
                    latestUrl
                )
            }
        } catch (e: ReCaptchaException) {
            Log.e(TAG, "🚫 ReCaptcha Exception", e)
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "🌐 IOException", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception inesperada", e)
            throw IOException("Error en petición HTTP", e)
        }
    }
}