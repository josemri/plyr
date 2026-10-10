package com.plyr.network

import android.util.Log
import com.plyr.BuildConfig
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

class SimpleDownloader private constructor() : Downloader() {

    companion object {
        private const val TAG = "SimpleDownloader"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private val YOUTUBE_RESTRICTED_MODE_COOKIE = BuildConfig.YOUTUBE_RESTRICTED_MODE_COOKIE
        private const val YOUTUBE_DOMAIN = "youtube.com"
        private const val RESTRICTED_MODE_COOKIE_KEY = "youtube_restricted_mode_key"
        private const val RECAPTCHA_COOKIE_KEY = "recaptcha_cookies_key"

        @Volatile
        private var instance: SimpleDownloader? = null

        fun getInstance(): SimpleDownloader {
            return instance ?: synchronized(this) {
                instance ?: SimpleDownloader().also { instance = it }
            }
        }
    }

    /**
     * `ConcurrentHashMap` y no `mutableMapOf`: `setCookie` escribe desde el
     * hilo que inicializa el extractor y `getCookies` lee desde **todos** los
     * hilos de red, así que el mapa estaba sujeto a la misma carrera que un
     * `HashMap` (B32).
     */
    private val cookies = java.util.concurrent.ConcurrentHashMap<String, String>()
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
        setCookie(RESTRICTED_MODE_COOKIE_KEY, YOUTUBE_RESTRICTED_MODE_COOKIE)
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
            getCookie(RESTRICTED_MODE_COOKIE_KEY)
        } else {
            null
        }

        // Combinar todas las cookies relevantes
        return buildCookieHeader(youtubeCookie, getCookie(RECAPTCHA_COOKIE_KEY))
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        Log.d(TAG, "🌐 Ejecutando petición: ${request.httpMethod()} ${request.url()}")

        val url = request.url()
        val builtRequest = buildRequest(request)

        return try {
            client.newCall(builtRequest).execute().use { response -> handleResponse(response, url) }
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

    /** Construye la petición de OkHttp con método, cookies y cabeceras. */
    private fun buildRequest(request: Request): okhttp3.Request {
        val requestBody = request.dataToSend()?.toRequestBody(null)
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), requestBody)
            .url(request.url())
            .addHeader("User-Agent", USER_AGENT)

        addCookies(builder, request.url())
        addCustomHeaders(builder, request.headers())

        // Log de headers (los valores sensibles se ocultan: B32)
        val builtRequest = builder.build()
        Log.d(TAG, "📋 Headers enviados:")
        Log.d(TAG, describeHeaders(builtRequest.headers.toMultimap()))
        return builtRequest
    }

    /** Añade la cabecera `Cookie` si hay cookies relevantes para [url]. */
    private fun addCookies(builder: okhttp3.Request.Builder, url: String) {
        val cookiesString = getCookies(url)
        if (cookiesString.isNotEmpty()) {
            builder.addHeader("Cookie", cookiesString)
            Log.d(TAG, "🍪 Cookies enviadas: ${describeCookieNames(cookiesString)}")
        }
    }

    /** Sustituye las cabeceras homónimas y añade las personalizadas. */
    private fun addCustomHeaders(
        builder: okhttp3.Request.Builder,
        headers: Map<String, List<String>>?
    ) {
        headers?.forEach { (headerName, headerValueList) ->
            builder.removeHeader(headerName)
            headerValueList.forEach { headerValue ->
                builder.addHeader(headerName, headerValue)
            }
        }
    }

    /** Registra la respuesta y la convierte al formato de NewPipe. */
    private fun handleResponse(response: okhttp3.Response, originalUrl: String): Response {
        val responseCode = response.code
        Log.d(TAG, "📥 Código de respuesta: $responseCode")

        // Detectar reCaptcha challenge
        if (responseCode == 429) {
            Log.e(TAG, "⚠️ reCaptcha Challenge detectado (429)")
            throw ReCaptchaException("reCaptcha Challenge requested", originalUrl)
        }

        // Leer body (OkHttp 5: body no nullable)
        val responseBodyString: String = response.body.use { it.string() }
        logBodySummary(responseCode, responseBodyString, originalUrl)

        // Obtener URL final (después de redirecciones)
        val latestUrl = response.request.url.toString()
        if (latestUrl != originalUrl) {
            Log.d(TAG, "🔄 Redirección: $latestUrl")
        }

        // Convertir headers de OkHttp a formato de NewPipe
        val headersMap = mutableMapOf<String, MutableList<String>>()
        response.headers.forEach { (name, value) ->
            headersMap.getOrPut(name) { mutableListOf() }.add(value)
        }

        return Response(responseCode, response.message, headersMap, responseBodyString, latestUrl)
    }

    /**
     * Resumen del body: éxito/error y, para el player de YouTube, solo el estado
     * (nunca el volcado entero, que puede llevar `visitorData` y tokens).
     */
    private fun logBodySummary(responseCode: Int, body: String, url: String) {
        if (responseCode < 400) {
            Log.d(TAG, "✅ Respuesta exitosa: ${body.length} caracteres")
            if (url.contains("/youtubei/v1/player")) {
                playabilityStatusOf(body)?.let { status ->
                    Log.d(TAG, "🎬 PlayabilityStatus: $status")
                }
            }
        } else {
            Log.e(TAG, "❌ Error ($responseCode): ${body.length} caracteres")
        }
    }
}

/** Cabeceras cuyo valor nunca se escribe en el log (B32). */
private val SENSITIVE_HEADERS = setOf(
    "authorization",
    "cookie",
    "set-cookie",
    "proxy-authorization",
    "x-goog-visitor-id",
    "sapientid",
    "x-goog-authuser"
)

private const val REDACTED = "«oculto»"

private val PLAYABILITY_STATUS = Regex("\"status\"\\s*:\\s*\"([A-Z_]+)\"")

/**
 * Une los valores de cookies en una única cabecera, sin repetir pares.
 */
internal fun buildCookieHeader(vararg values: String?): String =
    values.asSequence()
        .filterNotNull()
        .flatMap { it.split("; ") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("; ")

/**
 * Nombres de las cookies presentes en [cookieHeader], sin sus valores: los logs
 * de `adb logcat` acababan con credenciales ajenas al usuario.
 */
internal fun describeCookieNames(cookieHeader: String): String =
    cookieHeader.split("; ")
        .map { it.substringBefore('=').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString("; ")

/**
 * Resumen de las cabeceras enviadas, ocultando las sensibles.
 */
internal fun describeHeaders(headers: Map<String, List<String>>): String =
    headers.entries
        .sortedBy { it.key.lowercase() }
        .joinToString("\n") { (name, values) ->
            val shown = if (name.lowercase() in SENSITIVE_HEADERS) {
                "$REDACTED (${values.sumBy { it.length }} chars)"
            } else {
                values.joinToString(", ")
            }
            "   $name: $shown"
        }

/**
 * Estado de reproducibilidad (`OK`, `LOGIN_REQUIRED`, ...) de una respuesta del
 * Player API, o `null` si no aparece. Evita registrar la respuesta entera.
 */
internal fun playabilityStatusOf(body: String): String? {
    val marker = body.indexOf("\"playabilityStatus\"")
    if (marker == -1) return null
    return PLAYABILITY_STATUS.find(body, marker)?.groupValues?.get(1)
}