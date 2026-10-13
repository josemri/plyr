package com.plyr.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Importación de listas de Spotify, desacoplada de Android y del repositorio
 * (M11).
 *
 * En `:app` esta lógica vivía en `SpotifyImporter` atada a `Context` y a Room.
 * Aquí queda lo reutilizable por ambas plataformas: el parseo del HTML embebido
 * de Spotify, la descarga y la construcción del "plan" de importación (buscar
 * cada pista en YouTube y numerarla). Ni el almacenamiento ni el motor de
 * búsqueda concretos entran: la búsqueda llega inyectada como función y quien
 * llama decide dónde guardar el [ImportPlan].
 *
 * Es lógica pura salvo [fetchPlaylist]; por eso puede testearse en `:core`
 * (`jvmTest`) con un `searchTrack` falso y un HTML de muestra.
 */
object SpotifyImport {

    const val YOUTUBE_SEARCH_DELAY_MS = 1500L

    private const val SPOTIFY_EMBED_URL = "https://open.spotify.com/embed/playlist/"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val spotifyIdPattern = Regex("""^[a-zA-Z0-9]{22}$""")
    private val playlistUrlPatterns = listOf(
        Regex("""open\.spotify\.com/playlist/([a-zA-Z0-9]+)"""),
        Regex("""spotify:playlist:([a-zA-Z0-9]+)"""),
        Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)"""),
    )
    private val nextDataPattern = Regex(
        """<script\s+id="__NEXT_DATA__"\s+type="application/json"[^>]*>(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    data class SpotifyTrack(
        val name: String,
        val artists: List<String>,
        val durationMs: Long,
    )

    data class SpotifyPlaylist(
        val id: String,
        val name: String,
        val imageUrl: String?,
        val tracks: List<SpotifyTrack>,
    )

    /** Una pista ya resuelta contra YouTube y lista para persistir. */
    data class MatchedTrack(
        val position: Int,
        val remoteTrackId: String,
        val name: String,
        val artists: String,
        val youtubeVideoId: String?,
        val durationMs: Long,
    )

    /** Resultado de construir el plan de importación. */
    data class ImportPlan(
        val spotifyId: String,
        val dbPlaylistId: String,
        val name: String,
        val description: String,
        val imageUrl: String?,
        val tracks: List<MatchedTrack>,
        val matchedCount: Int,
        val totalCount: Int,
        val message: String,
    )

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val skipped: Boolean = false,
    )

    /** Id local con el que se guardan las listas de Spotify, como en Android. */
    fun dbPlaylistId(spotifyId: String): String = "youtube_$spotifyId"

    /**
     * Identidad estable de una pista de Spotify, con el mismo formato que usaba
     * Android (`spotify_<hashTítulo>_<hashArtistas>_<índice>`).
     */
    fun remoteTrackId(name: String, artists: List<String>, index: Int): String =
        "spotify_${name.hashCode()}_${artists.hashCode()}_$index"

    /**
     * Extrae el id de una lista de Spotify de una URL, una URI `spotify:` o un
     * id suelto de 22 caracteres. Devuelve `null` si no lo reconoce.
     */
    fun extractPlaylistId(input: String): String? {
        val cleaned = input.trim()
        for (pattern in playlistUrlPatterns) {
            pattern.find(cleaned)?.let { return it.groupValues[1] }
        }
        if (spotifyIdPattern.matches(cleaned)) return cleaned
        return null
    }

    /**
     * Extrae la lista del JSON embebido en la página de Spotify. Es pura: recibe
     * el HTML ya descargado, así que se testea sin red.
     */
    fun parseEmbedHtml(playlistId: String, html: String): Result<SpotifyPlaylist> {
        val scriptMatch = nextDataPattern.find(html)
            ?: return Result.failure(Exception("Could not parse Spotify page"))

        return try {
            val root = JSONObject(scriptMatch.groupValues[1].trim())
            val pageProps = root.getJSONObject("props").getJSONObject("pageProps")
            val state = pageProps.optJSONObject("state") ?: pageProps
            val data = state.optJSONObject("data") ?: state
            val entity = data.optJSONObject("entity")
                ?: return Result.failure(Exception("Playlist data not found"))

            val name = entity.optString("name", "Spotify Playlist")
            val imageUrl = entity.optJSONObject("coverArt")
                ?.optJSONArray("sources")
                ?.let { if (it.length() > 0) it.getJSONObject(0).optString("url") else null }

            val tracks = parseTracks(entity.optJSONArray("trackList") ?: JSONArray())
            Result.success(SpotifyPlaylist(playlistId, name, imageUrl, tracks))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Descarga el HTML embebido de Spotify y extrae la lista. */
    suspend fun fetchPlaylist(playlistId: String): Result<SpotifyPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$SPOTIFY_EMBED_URL$playlistId")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()

                client.newCall(request).execute().use { response ->
                    val html = response.body.string()
                    if (!response.isSuccessful || html.isNullOrBlank()) {
                        return@withContext Result.failure(Exception("Failed to fetch playlist"))
                    }
                    parseEmbedHtml(playlistId, html)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Busca cada pista en YouTube con [searchTrack] y construye el plan.
     *
     * [searchTrack] recibe la consulta (`"título - artistas"`) y devuelve el id
     * de vídeo o `null`. [onProgress] recibe `(actual, total, pista)`.
     */
    suspend fun buildPlan(
        playlist: SpotifyPlaylist,
        searchTrack: suspend (query: String) -> String?,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
        searchDelayMs: Long = YOUTUBE_SEARCH_DELAY_MS,
    ): ImportPlan {
        val matched = mutableListOf<MatchedTrack>()
        var matchedCount = 0
        val total = playlist.tracks.size

        for ((index, track) in playlist.tracks.withIndex()) {
            onProgress(index + 1, total, track.name)
            val videoId = searchTrack(searchQuery(track))
            if (videoId != null) matchedCount++
            matched.add(
                MatchedTrack(
                    position = index,
                    remoteTrackId = remoteTrackId(track.name, track.artists, index),
                    name = track.name,
                    artists = track.artists.joinToString(", "),
                    youtubeVideoId = videoId,
                    durationMs = track.durationMs,
                )
            )
            if (index < playlist.tracks.lastIndex && searchDelayMs > 0) delay(searchDelayMs)
        }

        return ImportPlan(
            spotifyId = playlist.id,
            dbPlaylistId = dbPlaylistId(playlist.id),
            name = playlist.name,
            description = "Imported from Spotify",
            imageUrl = playlist.imageUrl,
            tracks = matched,
            matchedCount = matchedCount,
            totalCount = total,
            message = importedMessage(playlist.name, matchedCount, total),
        )
    }

    /** Mensaje de éxito, idéntico al que mostraba Android. */
    fun importedMessage(name: String, matched: Int, total: Int): String =
        "Imported '$name' ($matched/$total matched)"

    /** Mensaje del caso "ya estaba importada". */
    fun alreadyImportedMessage(name: String): String = "'$name' already imported"

    private fun parseTracks(trackList: JSONArray): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        for (i in 0 until trackList.length()) {
            val t = trackList.getJSONObject(i)
            val title = t.optString("title", "").takeIf { it.isNotBlank() } ?: continue
            val subtitle = t.optString("subtitle", "")
            val artists = if (subtitle.isNotBlank()) {
                subtitle.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                listOf("Unknown Artist")
            }
            tracks.add(SpotifyTrack(title, artists, t.optLong("duration", 0)))
        }
        return tracks
    }

    private fun searchQuery(track: SpotifyTrack): String =
        "${track.name} - ${track.artists.joinToString(", ")}"
}
