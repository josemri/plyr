package com.plyr.utils

import android.content.Context
import android.util.Log
import com.plyr.database.PlaylistLocalRepository
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
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(playlistUri)
            ?: return@withContext Result.failure(Exception("Invalid Spotify playlist URL"))

        onProgress("Fetching playlist from Spotify...")
        val playlist = fetchPlaylistFromEmbed(playlistId).getOrElse { e ->
            return@withContext Result.failure(e)
        }

        onProgress("Found ${playlist.tracks.size} tracks. Searching YouTube...")

        val searchManager = YouTubeSearchManager(context)
        val localRepository = PlaylistLocalRepository(context)
        val dbPlaylistId = "youtube_$playlistId"
        val trackEntities = mutableListOf<TrackEntity>()
        var foundCount = 0

        for ((index, track) in playlist.tracks.withIndex()) {
            val query = "${track.name} - ${track.artists.joinToString(", ")}"
            onProgress("Searching ${index + 1}/${playlist.tracks.size}: ${track.name}")

            try {
                val videoId = searchManager.searchSingleVideoId(query)
                if (videoId != null) foundCount++
                trackEntities.add(
                    TrackEntity(
                        id = "${dbPlaylistId}_$index",
                        playlistId = dbPlaylistId,
                        remoteTrackId = "spotify_${track.name.hashCode()}_${track.artists.hashCode()}_$index",
                        name = track.name,
                        artists = track.artists.joinToString(", "),
                        youtubeVideoId = videoId,
                        audioUrl = null,
                        position = index
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error searching YouTube for: ${track.name}", e)
                trackEntities.add(
                    TrackEntity(
                        id = "${dbPlaylistId}_$index",
                        playlistId = dbPlaylistId,
                        remoteTrackId = "spotify_${track.name.hashCode()}_${track.artists.hashCode()}_$index",
                        name = track.name,
                        artists = track.artists.joinToString(", "),
                        youtubeVideoId = null,
                        audioUrl = null,
                        position = index
                    )
                )
            }

            if (index < playlist.tracks.lastIndex) {
                delay(YOUTUBE_SEARCH_DELAY_MS)
            }
        }

        onProgress("Saving playlist...")

        localRepository.saveCreatedYouTubePlaylist(
            playlistId = playlistId,
            title = playlist.name,
            description = "Imported from Spotify",
            imageUrl = playlist.imageUrl,
            tracks = trackEntities
        )

        val message = "Imported '${playlist.name}' ($foundCount/${playlist.tracks.size} tracks matched)"
        Log.d(TAG, message)
        Result.success(message)
    }

    private suspend fun fetchPlaylistFromEmbed(playlistId: String): Result<SpotifyPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://open.spotify.com/embed/playlist/$playlistId")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string()
                response.close()

                if (!response.isSuccessful || html.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Failed to fetch playlist"))
                }

                val scriptPattern = Regex("""<script\s+id="__NEXT_DATA__"\s+type="application/json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                val scriptMatch = scriptPattern.find(html)
                    ?: return@withContext Result.failure(Exception("Could not parse Spotify page"))

                val root = JSONObject(scriptMatch.groupValues[1].trim())
                val pageProps = root.getJSONObject("props").getJSONObject("pageProps")
                val state = pageProps.optJSONObject("state") ?: pageProps
                val data = state.optJSONObject("data") ?: state
                val entity = data.optJSONObject("entity")
                    ?: return@withContext Result.failure(Exception("Playlist data not found"))

                val name = entity.optString("name", "Spotify Playlist")
                val imageUrl = entity.optJSONObject("coverArt")
                    ?.optJSONArray("sources")
                    ?.let { if (it.length() > 0) it.getJSONObject(0).optString("url") else null }

                val trackList = entity.optJSONArray("trackList") ?: JSONArray()
                val tracks = mutableListOf<SpotifyTrack>()
                for (i in 0 until trackList.length()) {
                    val t = trackList.getJSONObject(i)
                    val title = t.optString("title", null) ?: continue
                    val subtitle = t.optString("subtitle", "")
                    val artists = if (subtitle.isNotBlank()) {
                        subtitle.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    } else listOf("Unknown Artist")
                    tracks.add(SpotifyTrack(title, artists, t.optLong("duration", 0)))
                }

                Result.success(SpotifyPlaylist(playlistId, name, imageUrl, tracks))
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching embed playlist: ${e.message}", e)
                Result.failure(e)
            }
        }
}
