package com.plyr.network

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.plyr.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Song(val title: String, val artist: String)

private val httpClient = OkHttpClient()

suspend fun getSimilarArtists(context: Context, artistName: String, limit: Int = 5): List<Pair<String, Float>> {
    return withContext(Dispatchers.IO) {
        try {
            val apiKey = Config.getLastfmApiKey(context)
            if (apiKey.isNullOrBlank()) {
                Log.e("RecommendationsAPI", "Last.fm API Key not configured")
                return@withContext emptyList()
            }

            val url = "http://ws.audioscrobbler.com/2.0/" +
                    "?method=artist.getsimilar" +
                    "&artist=$artistName" +
                    "&api_key=$apiKey" +
                    "&format=json" +
                    "&limit=$limit"

            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val jsonObject = JsonParser.parseString(body).asJsonObject
            val similarArtists = mutableListOf<Pair<String, Float>>()

            if (jsonObject.has("similarartists")) {
                val similarArtistsObj = jsonObject.getAsJsonObject("similarartists")
                if (similarArtistsObj.has("artist")) {
                    val artists = similarArtistsObj.getAsJsonArray("artist")
                    for (artist in artists) {
                        val artistObj = artist.asJsonObject
                        val name = artistObj.get("name").asString
                        val matchScore = artistObj.get("match").asFloat
                        similarArtists.add(Pair(name, matchScore))
                    }
                }
            }
            similarArtists
        } catch (e: Exception) {
            Log.e("RecommendationsAPI", "Error fetching similar artists for $artistName: ${e.message}")
            emptyList()
        }
    }
}

suspend fun getTopSimilarArtists(
    context: Context,
    artistList: List<String>,
    limitPerArtist: Int = 5,
    topN: Int = 5
): List<String> {
    return try {
        val allSimilar = mutableMapOf<String, Float>()

        for (artist in artistList) {
            val similares = getSimilarArtists(context, artist, limitPerArtist)
            for ((name, score) in similares) {
                allSimilar[name] = maxOf(allSimilar[name] ?: 0f, score)
            }
        }

        allSimilar.toList()
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    } catch (e: Exception) {
        Log.e("RecommendationsAPI", "Error in getTopSimilarArtists: ${e.message}")
        emptyList()
    }
}

suspend fun getRandomTracksFromArtist(artistName: String, token: String, n: Int = 3): List<String> {
    return withContext(Dispatchers.IO) {
        try {
            val searchUrl = "https://api.spotify.com/v1/search?q=$artistName&type=artist&limit=1"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val searchResponse = httpClient.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string() ?: return@withContext emptyList()
            val searchJson = JsonParser.parseString(searchBody).asJsonObject

            if (!searchJson.has("artists") || !searchJson.getAsJsonObject("artists").has("items")) {
                return@withContext emptyList()
            }

            val items = searchJson.getAsJsonObject("artists").getAsJsonArray("items")
            if (items.size() == 0) {
                return@withContext emptyList()
            }

            val artistId = items[0].asJsonObject.get("id").asString

            val topTracksUrl = "https://api.spotify.com/v1/artists/$artistId/top-tracks?market=US"
            val tracksRequest = Request.Builder()
                .url(topTracksUrl)
                .header("Authorization", "Bearer $token")
                .build()

            val tracksResponse = httpClient.newCall(tracksRequest).execute()
            val tracksBody = tracksResponse.body?.string() ?: return@withContext emptyList()
            val tracksJson = JsonParser.parseString(tracksBody).asJsonObject

            if (!tracksJson.has("tracks")) {
                return@withContext emptyList()
            }

            val tracks = tracksJson.getAsJsonArray("tracks")
            if (tracks.size() == 0) {
                return@withContext emptyList()
            }

            tracks.map { it.asJsonObject.get("name").asString }
                .shuffled()
                .take(minOf(n, tracks.size()))
        } catch (e: Exception) {
            Log.e("RecommendationsAPI", "Error fetching tracks from $artistName: ${e.message}")
            emptyList()
        }
    }
}

suspend fun getRecommendations(context: Context, myArtists: List<String>): List<Song> {
    return try {
        if (myArtists.isEmpty()) {
            return emptyList()
        }

        val spotifyToken = Config.getSpotifyAccessToken(context)
        if (spotifyToken == null) {
            Log.e("RecommendationsAPI", "Failed to get Spotify token")
            return emptyList()
        }

        val recommendedArtists = getTopSimilarArtists(context, myArtists, limitPerArtist = 5, topN = 5)
        if (recommendedArtists.isEmpty()) {
            Log.w("RecommendationsAPI", "No similar artists found")
            return emptyList()
        }

        val songs = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<Pair<String, String>>()

        // Obtener canciones de artistas recomendados (5 por artista)
        for (artist in recommendedArtists) {
            if (songs.size >= 10) break
            val tracks = getRandomTracksFromArtist(artist, spotifyToken, n = 5)
            for (track in tracks) {
                val key = Pair(track.lowercase(), artist.lowercase())
                if (key !in seen) {
                    seen.add(key)
                    songs.add(Pair(track, artist))
                }
            }
        }

        // Si faltan canciones, obtener de artistas originales
        if (songs.size < 10) {
            for (artist in myArtists) {
                if (songs.size >= 10) break
                val tracks = getRandomTracksFromArtist(artist, spotifyToken, n = 10)
                for (track in tracks) {
                    val key = Pair(track.lowercase(), artist.lowercase())
                    if (key !in seen) {
                        seen.add(key)
                        songs.add(Pair(track, artist))
                        if (songs.size >= 10) break
                    }
                }
            }
        }

        Log.d("RecommendationsAPI", "Generated ${songs.size} recommendations")
        songs.shuffled().take(10).map { (title, artist) -> Song(title, artist) }
    } catch (e: Exception) {
        Log.e("RecommendationsAPI", "Error in getRecommendations: ${e.message}")
        emptyList()
    }
}
