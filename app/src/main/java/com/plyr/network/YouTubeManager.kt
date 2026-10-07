package com.plyr.network

import com.plyr.utils.NewPipeHolder
import com.plyr.utils.UrlParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.concurrent.ConcurrentHashMap

/**
 * Gestor unificado de YouTube - Búsqueda, extracción de audio y caché de URLs.
 */
object YouTubeManager {
    private const val EXTRACTION_TIMEOUT_MS = 30_000L

    /**
     * Las URLs de audio de googlevideo caducan. Se guardan en caché durante
     * este plazo: suficiente para toda una sesión de escucha (evita re-resolver
     * en cada salto) sin arriesgarse a usar una URL caducada.
     */
    private const val URL_CACHE_TTL_MS = 2 * 60 * 60 * 1000L

    private data class CacheEntry(val url: String, val resolvedAt: Long)

    private val urlCache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Resultado de una extracción compartida.
     *
     * Un `null` a secas no basta para contar lo que pasó: "este vídeo no tiene
     * audio" y "quien lo estaba extrayendo se canceló a mitad y no llegó a
     * extraer nada" se comportan igual al recibirlo, pero hay que actuar distinto
     * ante cada uno. En el segundo caso todavía no se ha intentado de verdad, así
     * que quien espera tiene que hacerlo él; devolver el `null` tal cual hacía que
     * la canción se descartara como un fallo real y el `>>` no encontrara nada que
     * reproducir (B57).
     */
    private sealed interface Extraction {
        /** Terminó. Su `url` es `null` solo si la extracción falló de verdad. */
        data class Finished(val url: String?) : Extraction

        /** Quien la extrajo se canceló antes de tener resultado: no se intentó. */
        data object Interrupted : Extraction
    }

    /**
     * Extracciones en vuelo, por video (B5).
     *
     * Sin esto, dos corrutinas que piden el mismo video lanzaban dos
     * extracciones contra YouTube: la segunda porque la primera se canceló, y
     * la cancelada no se puede interrumpir (OkHttp sobre `Dispatchers.IO`), así
     * que el trabajo se hacía dos veces. Aquí corre una sola y las demás esperan
     * su resultado.
     */
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Extraction>>()

    /**
     * Busca un video en YouTube y devuelve su ID
     */
    fun searchVideoId(query: String): String? {
        return try {
            NewPipeHolder.ensureInitialized()

            val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
            searchExtractor.fetchPage()

            searchExtractor.initialPage.items
                .filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
                .firstOrNull()?.url
                ?.let { UrlParser.extractYoutubeVideoId(it) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resuelve el ID de YouTube de una pista, buscando en YouTube si no lo tiene.
     */
    suspend fun resolveVideoId(name: String, artists: String, youtubeVideoId: String?): String? {
        if (!youtubeVideoId.isNullOrBlank()) return youtubeVideoId
        return withContext(Dispatchers.IO) {
            searchVideoId("$name $artists")
        }
    }

    /**
     * Extrae la URL de audio de un video de YouTube.
     *
     * Cache-first: si ya se resolvió este video y la entrada sigue vigente,
     * devuelve la URL al instante sin tocar la red. Esto es lo que hace que
     * saltar a la siguiente canción no dependa de una extracción nueva.
     *
     * [forceRefresh] salta la caché: lo usa el reintento tras un 403/410, que
     * si no volvería a recibir la URL muerta que acaba de fallar (B4).
     *
     * La extracción es bloqueante (NewPipe/OkHttp), por lo que se ejecuta en IO
     * con un timeout explícito: si no responde, devuelve null en lugar de colgarse.
     */
    suspend fun getAudioUrl(videoId: String, forceRefresh: Boolean = false): String? =
        getAudioUrl(videoId, forceRefresh) { extractAudioUrl(videoId) }

    /**
     * Núcleo de [getAudioUrl] con la extracción inyectada, para poder ejercitar
     * la deduplicación y el salto de caché sin red.
     */
    internal suspend fun getAudioUrl(
        videoId: String,
        forceRefresh: Boolean,
        extract: () -> String?
    ): String? {
        // Se reconsulta la caché en cada vuelta: si el productor anterior se
        // canceló, quizá otro ya extrajo este vídeo mientras tanto.
        while (true) {
            if (!forceRefresh) cachedUrl(videoId)?.let { return it }

            val mine = CompletableDeferred<Extraction>()
            val pending = inFlight.putIfAbsent(videoId, mine)
            if (pending != null) {
                when (val result = pending.await()) {
                    is Extraction.Finished -> return result.url
                    // El productor se fue sin extraer nada: no hay nada que
                    // esperar, así que lo intenta este. Devolver `null` aquí
                    // descartaría la canción como si hubiera fallado (B57).
                    Extraction.Interrupted -> continue
                }
            }

            var url: String? = null
            try {
                url = withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) { extract() }
                }
            } finally {
                // `withContext` no puede interrumpir `extract()`, así que una
                // cancelación se nota al volver: o lanza, o devuelve el
                // resultado igualmente. Sin URL y con la corrutina cancelada el
                // intento no llegó a serlo, y contarlo como `null` normal hacía
                // que quien esperaba descartara la pista sin reintentar (B57).
                val cancelled = !currentCoroutineContext().isActive
                inFlight.remove(videoId, mine)
                when {
                    url != null -> {
                        urlCache[videoId] = CacheEntry(url, System.currentTimeMillis())
                        mine.complete(Extraction.Finished(url))
                    }
                    cancelled -> mine.complete(Extraction.Interrupted)
                    else -> mine.complete(Extraction.Finished(null))
                }
            }
            return url
        }
    }

    /** URL cacheada aún vigente, o `null` si no hay o ha caducado. */
    fun cachedUrl(videoId: String): String? {
        val entry = urlCache[videoId] ?: return null
        val age = System.currentTimeMillis() - entry.resolvedAt
        if (age > URL_CACHE_TTL_MS) {
            urlCache.remove(videoId)
            return null
        }
        return entry.url
    }

    /**
     * Descarta la URL cacheada de un video (p. ej. tras un 403), para que el
     * siguiente intento la vuelva a extraer.
     */
    fun invalidate(videoId: String) {
        urlCache.remove(videoId)
    }

    /**
     * Vacía la caché (opcionalmente solo las entradas caducadas).
     *
     * La producción nunca la llama: la caché solo se invalida por `videoId` con
     * [invalidate]. Se mantiene porque `AudioUrlExtractionTest` la usa para
     * partir de una caché limpia entre tests.
     */
    fun clearCache(onlyExpired: Boolean = false) {
        if (!onlyExpired) {
            urlCache.clear()
            return
        }
        val now = System.currentTimeMillis()
        urlCache.entries.removeAll { now - it.value.resolvedAt > URL_CACHE_TTL_MS }
    }

    private fun extractAudioUrl(videoId: String): String? {
        return try {
            NewPipeHolder.ensureInitialized()

            val extractor = ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=$videoId")

            YoutubeStreamExtractor.setFetchIosClient(true)
            extractor.fetchPage()

            val audioStreams = extractor.audioStreams
            if (audioStreams.isEmpty()) {
                android.util.Log.e("YouTubeManager", "❌ Sin audio streams para $videoId")
                return null
            }

            val firstStream = audioStreams.firstOrNull() ?: return null

            val audioUrl = firstStream.content
            if (audioUrl.isNullOrEmpty()) {
                @Suppress("DEPRECATION")
                val deprecatedUrl = firstStream.url
                if (deprecatedUrl != null) {
                    android.util.Log.w("YouTubeManager", "⚠️ Usando URL deprecated para $videoId")
                    return deprecatedUrl
                }
                return null
            }

            android.util.Log.d("YouTubeManager", "✅ URL de audio resuelta para $videoId")
            audioUrl

        } catch (e: Exception) {
            android.util.Log.e("YouTubeManager", "❌ Error extrayendo audio de $videoId: ${e.message}")
            null
        }
    }
}
