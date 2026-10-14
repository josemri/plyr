package com.plyr.network

import com.plyr.core.util.Logger
import com.plyr.model.AudioItem
import com.plyr.utils.NewPipeHolder
import com.plyr.utils.UrlParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Ficha de una lista (playlist) de YouTube.
 *
 * Es lo mínimo que la UI necesita para pintar el resultado y abrir el detalle:
 * el id de la lista (PL/UU/FL/RD...), su título, quién la subió, cuántos vídeos
 * tiene y su portada. Equivale a lo que Android guarda al vuelo desde
 * `PlaylistInfoItem`, pero sin depender de Android.
 */
data class YouTubePlaylistInfo(
    val playlistId: String,
    val title: String,
    val uploader: String,
    val videoCount: Int,
    val thumbnailUrl: String?,
)

/** Resultado de [YouTubeManager.searchAll]: los vídeos y las listas de una búsqueda. */
data class YouTubeSearchAllResult(
    val videos: List<AudioItem> = emptyList(),
    val playlists: List<YouTubePlaylistInfo> = emptyList(),
    /** `true` si YouTube aún tiene una página más detrás de esta (M21). */
    val hasMore: Boolean = false,
)

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
     * Busca vídeos en YouTube y devuelve sus metadatos.
     *
     * Es la variante "con resultados" de [searchVideoId]: en vez solo del primer
     * id, devuelve la lista de [AudioItem] (título, id de vídeo, canal y
     * duración) que la UI de escritorio pinta para elegir qué reproducir. La
     * URL de audio no se extrae aquí: se resuelve al vuelo con [getAudioUrl]
     * cuando la pista se reproduce.
     *
     * La parte pura (NewPipe + [UrlParser]) ya vivía en `:app`
     * (`YouTubeSearchManager`); esta es la versión portable que comparten los
     * targets JVM. Si algo falla devuelve una lista vacía, nunca lanza.
     */
    suspend fun searchVideos(query: String, maxResults: Int = 25): List<AudioItem> =
        withContext(Dispatchers.IO) {
            try {
                NewPipeHolder.ensureInitialized()

                val extractor = ServiceList.YouTube.getSearchExtractor(query)
                extractor.fetchPage()

                extractor.initialPage.items
                    .asSequence()
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { audioItemFrom(it) }
                    .take(maxResults)
                    .toList()
            } catch (e: Exception) {
                Logger.e("YouTubeManager", "❌ Error buscando '$query': ${e.message}")
                emptyList()
            }
        }

    /**
     * Busca en YouTube y devuelve **vídeos y listas** de una sola pasada.
     *
     * Es la variante "completa" de [searchVideos]: NewPipe devuelve resultados
     * mezclados ([StreamInfoItem] y [PlaylistInfoItem]) y aquí se separan en dos
     * cubos, respetando sendos topes. La UI de escritorio (M19) muestra las
     * listas junto a los vídeos y abre su detalle. Si algo falla devuelve el
     * resultado vacío, nunca lanza.
     *
     * Desde M21 [page] permite pedir páginas siguientes (1 = la primera): la
     * búsqueda de escritorio ofrece «cargar más» y va **añadiendo** páginas.
     * [YouTubeSearchAllResult.hasMore] indica si YouTube aún tiene una página
     * detrás de la devuelta. Si se pide una página que ya no existe, se devuelve
     * la última disponible.
     */
    suspend fun searchAll(
        query: String,
        page: Int = 1,
        maxVideos: Int = 25,
        maxPlaylists: Int = 10,
    ): YouTubeSearchAllResult = withContext(Dispatchers.IO) {
        try {
            NewPipeHolder.ensureInitialized()

            val extractor = ServiceList.YouTube.getSearchExtractor(query)
            extractor.fetchPage()

            // Avanza hasta la página pedida: la 1 es `initialPage`; las demás se
            // piden con `getPage(nextPage)`. Si YouTube se queda sin páginas
            // antes de llegar, se devuelve la última disponible.
            var current = extractor.initialPage
            var fetched = 1
            while (fetched < page && current.hasNextPage()) {
                current = extractor.getPage(current.nextPage)
                fetched++
            }

            val (videos, playlists) = collectSearchItems(current.items, maxVideos, maxPlaylists)
            YouTubeSearchAllResult(
                videos = videos,
                playlists = playlists,
                hasMore = current.hasNextPage(),
            )
        } catch (e: Exception) {
            Logger.e("YouTubeManager", "❌ Error buscando '$query': ${e.message}")
            YouTubeSearchAllResult()
        }
    }

    /** Separa los items de una página en vídeos y listas, respetando los topes. */
    private fun collectSearchItems(
        items: List<InfoItem>,
        maxVideos: Int,
        maxPlaylists: Int,
    ): Pair<List<AudioItem>, List<YouTubePlaylistInfo>> {
        val videos = mutableListOf<AudioItem>()
        val playlists = mutableListOf<YouTubePlaylistInfo>()
        for (item in items) {
            when (item) {
                is StreamInfoItem -> if (videos.size < maxVideos) {
                    audioItemFrom(item)?.let { videos.add(it) }
                }
                is PlaylistInfoItem -> if (playlists.size < maxPlaylists) {
                    playlistInfoFrom(item)?.let { playlists.add(it) }
                }
            }
            if (videos.size >= maxVideos && playlists.size >= maxPlaylists) break
        }
        return videos to playlists
    }

    /**
     * Ficha de una lista de YouTube por su id. Devuelve `null` si falla.
     *
     * La portada no viene aquí (la ficha de una lista no siempre la trae): la UI
     * usa la del primer vídeo o deja el hueco vacío.
     */
    suspend fun getPlaylistInfo(playlistId: String): YouTubePlaylistInfo? =
        withContext(Dispatchers.IO) {
            try {
                NewPipeHolder.ensureInitialized()

                val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl(playlistId))
                extractor.fetchPage()

                YouTubePlaylistInfo(
                    playlistId = playlistId,
                    title = extractor.name.orEmpty(),
                    uploader = extractor.uploaderName.orEmpty(),
                    videoCount = extractor.streamCount.toInt(),
                    thumbnailUrl = null,
                )
            } catch (e: Exception) {
                Logger.e("YouTubeManager", "❌ Error obteniendo la lista $playlistId: ${e.message}")
                null
            }
        }

    /**
     * Pistas de una lista de YouTube, ya como [AudioItem].
     *
     * Igual que [searchVideos], no extrae la URL de audio: se resuelve al
     * reproducir. Si algo falla devuelve una lista vacía, nunca lanza.
     */
    suspend fun getPlaylistVideos(playlistId: String, maxVideos: Int = 100): List<AudioItem> =
        withContext(Dispatchers.IO) {
            try {
                NewPipeHolder.ensureInitialized()

                val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl(playlistId))
                extractor.fetchPage()

                extractor.initialPage.items
                    .asSequence()
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { audioItemFrom(it) }
                    .take(maxVideos)
                    .toList()
            } catch (e: Exception) {
                Logger.e("YouTubeManager", "❌ Error obteniendo los vídeos de $playlistId: ${e.message}")
                emptyList()
            }
        }

    /** URL canónica de una lista; NewPipe la necesita para construir el extractor. */
    private fun playlistUrl(playlistId: String) = "https://www.youtube.com/playlist?list=$playlistId"

    /**
     * Convierte un resultado de vídeo a [AudioItem], o `null` si no tiene un id
     * de YouTube válido (los resultados promocionados a veces no lo traen).
     */
    private fun audioItemFrom(item: StreamInfoItem): AudioItem? {
        val videoId = UrlParser.extractYoutubeVideoId(item.url)
        if (videoId == null || videoId.length != 11) return null
        return AudioItem(
            title = item.name,
            url = "",
            videoId = videoId,
            channel = item.uploaderName.orEmpty(),
            duration = formatDurationSeconds(item.duration),
        )
    }

    /** Convierte un resultado de lista a [YouTubePlaylistInfo], o `null` sin id. */
    private fun playlistInfoFrom(item: PlaylistInfoItem): YouTubePlaylistInfo? {
        val playlistId = UrlParser.extractYoutubePlaylistId(item.url) ?: return null
        return YouTubePlaylistInfo(
            playlistId = playlistId,
            title = item.name,
            uploader = item.uploaderName.orEmpty(),
            videoCount = item.streamCount.toInt(),
            thumbnailUrl = item.thumbnails.firstOrNull()?.url,
        )
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
                Logger.e("YouTubeManager", "❌ Sin audio streams para $videoId")
                return null
            }

            val firstStream = audioStreams.firstOrNull() ?: return null

            val audioUrl = firstStream.content
            if (audioUrl.isNullOrEmpty()) {
                @Suppress("DEPRECATION")
                val deprecatedUrl = firstStream.url
                if (deprecatedUrl != null) {
                    Logger.w("YouTubeManager", "⚠️ Usando URL deprecated para $videoId")
                    return deprecatedUrl
                }
                return null
            }

            Logger.d("YouTubeManager", "✅ URL de audio resuelta para $videoId")
            audioUrl

        } catch (e: Exception) {
            Logger.e("YouTubeManager", "❌ Error extrayendo audio de $videoId: ${e.message}")
            null
        }
    }

    /**
     * Duración de la búsqueda en formato "M:SS" o "H:MM:SS" (vacío si se desconoce).
     *
     * Es privada para no chocar con el `com.plyr.utils.formatDurationSeconds` de
     * `:app`, que además localiza "En vivo" y no se usa en `:core`.
     */
    private fun formatDurationSeconds(totalSeconds: Long): String {
        if (totalSeconds <= 0) return ""
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        }
    }
}
