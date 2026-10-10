package com.plyr.utils

import android.content.Context
import android.util.Log
import com.plyr.database.CreatedPlaylist
import com.plyr.database.PlaylistDatabase
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackEntity
import com.plyr.service.YouTubeSearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SpotifyImporter {

    private const val TAG = "SpotifyImporter"
    private const val YOUTUBE_SEARCH_DELAY_MS = 1500L

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class SpotifyTrack(
        val name: String,
        val artists: List<String>,
        val durationMs: Long
    )

    data class SpotifyPlaylist(
        val id: String,
        val name: String,
        val imageUrl: String?,
        val tracks: List<SpotifyTrack>
    )

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val skipped: Boolean = false
    )

    fun extractPlaylistId(input: String): String? {
        val cleaned = input.trim()
        val patterns = listOf(
            Regex("""open\.spotify\.com/playlist/([a-zA-Z0-9]+)"""),
            Regex("""spotify:playlist:([a-zA-Z0-9]+)"""),
            Regex("""spotify\.com/playlist/([a-zA-Z0-9]+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(cleaned)
            if (match != null) return match.groupValues[1]
        }
        if (Regex("^[a-zA-Z0-9]{22}$").matches(cleaned)) return cleaned
        return null
    }

    suspend fun importPlaylistByUri(
        context: Context,
        playlistUri: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(playlistUri)
            ?: return@withContext Result.failure(Exception("Invalid Spotify playlist URL"))

        onProgress(0, 1, "Fetching playlist...")
        val playlist = fetchPlaylistFromEmbed(playlistId).getOrElse { e ->
            return@withContext Result.failure(e)
        }

        val dbPlaylistId = "youtube_$playlistId"
        val database = PlaylistDatabase.getDatabase(context)

        if (isAlreadyImported(database, dbPlaylistId, playlist)) {
            return@withContext Result.success(
                ImportResult(
                    success = true,
                    message = "'${playlist.name}' already imported",
                    skipped = true
                )
            )
        }

        val searchManager = YouTubeSearchManager(context)
        val (trackEntities, foundCount) = matchTracks(searchManager, playlist, dbPlaylistId, onProgress)

        onProgress(playlist.tracks.size, playlist.tracks.size, "Saving...")

        PlaylistLocalRepository(context).saveCreatedYouTubePlaylist(
            created = CreatedPlaylist(
                playlistId = playlistId,
                title = playlist.name,
                description = "Imported from Spotify",
                imageUrl = playlist.imageUrl
            ),
            tracks = trackEntities,
            source = PlaylistSource.SPOTIFY
        )

        val message = "Imported '${playlist.name}' ($foundCount/${playlist.tracks.size} matched)"
        Log.d(TAG, message)
        Result.success(ImportResult(success = true, message = message))
    }

    /** ¿El dispositivo ya tiene esta playlist (por id o por mismo nombre y contenido)? */
    private suspend fun isAlreadyImported(
        database: PlaylistDatabase,
        dbPlaylistId: String,
        playlist: SpotifyPlaylist
    ): Boolean {
        if (database.playlistDao().getPlaylistById(dbPlaylistId) != null &&
            contentMatches(database, dbPlaylistId, playlist.tracks)
        ) {
            return true
        }
        val sameName = database.playlistDao().getAllPlaylistsSync().find {
            it.name.equals(playlist.name, ignoreCase = true) && it.remoteId != dbPlaylistId
        } ?: return false
        return contentMatches(database, sameName.remoteId, playlist.tracks)
    }

    private suspend fun contentMatches(
        database: PlaylistDatabase,
        playlistId: String,
        tracks: List<SpotifyTrack>
    ): Boolean {
        val existing = database.trackDao().getTracksByPlaylistSync(playlistId)
        if (existing.size != tracks.size) return false
        return existing.take(5).mapIndexed { i, t ->
            t.name.equals(tracks.getOrNull(i)?.name, ignoreCase = true)
        }.all { it }
    }

    /** Busca cada pista en YouTube y construye las entidades de Room. */
    private suspend fun matchTracks(
        searchManager: YouTubeSearchManager,
        playlist: SpotifyPlaylist,
        dbPlaylistId: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): Pair<List<TrackEntity>, Int> {
        val tracks = mutableListOf<TrackEntity>()
        var foundCount = 0
        val total = playlist.tracks.size

        for ((index, track) in playlist.tracks.withIndex()) {
            onProgress(index + 1, total, track.name)
            val videoId = searchTrack(searchManager, track)
            if (videoId != null) foundCount++
            tracks.add(trackEntity(dbPlaylistId, index, track, videoId))
            if (index < playlist.tracks.lastIndex) delay(YOUTUBE_SEARCH_DELAY_MS)
        }

        return tracks to foundCount
    }

    private suspend fun searchTrack(searchManager: YouTubeSearchManager, track: SpotifyTrack): String? =
        try {
            searchManager.searchSingleVideoId("${track.name} - ${track.artists.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e(TAG, "Error searching YouTube for: ${track.name}", e)
            null
        }

    private fun trackEntity(
        dbPlaylistId: String,
        index: Int,
        track: SpotifyTrack,
        videoId: String?
    ): TrackEntity = TrackEntity(
        id = "${dbPlaylistId}_$index",
        playlistId = dbPlaylistId,
        remoteTrackId = "spotify_${track.name.hashCode()}_${track.artists.hashCode()}_$index",
        name = track.name,
        artists = track.artists.joinToString(", "),
        youtubeVideoId = videoId,
        audioUrl = null,
        position = index
    )

    internal suspend fun fetchPlaylistFromEmbed(playlistId: String): Result<SpotifyPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://open.spotify.com/embed/playlist/$playlistId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body.string()
                response.close()

                if (!response.isSuccessful || html.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Failed to fetch playlist"))
                }

                parseEmbedHtml(playlistId, html)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching embed playlist: ${e.message}", e)
                Result.failure(e)
            }
        }

    /** Extrae la playlist del JSON embebido en la página de Spotify. */
    private fun parseEmbedHtml(playlistId: String, html: String): Result<SpotifyPlaylist> {
        val scriptPattern = Regex(
            """<script\s+id="__NEXT_DATA__"\s+type="application/json"[^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val scriptMatch = scriptPattern.find(html)
            ?: return Result.failure(Exception("Could not parse Spotify page"))

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
        return Result.success(SpotifyPlaylist(playlistId, name, imageUrl, tracks))
    }

    private fun parseTracks(trackList: JSONArray): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        for (i in 0 until trackList.length()) {
            val t = trackList.getJSONObject(i)
            val title = t.optString("title", null) ?: continue
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
}
