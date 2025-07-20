package com.plyr.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.util.Base64
import android.content.Context
import com.plyr.utils.Config

object SpotifyRepository {
    
    private val client = OkHttpClient()
    private val gson = Gson()
    
    // URLs de Spotify
    private const val AUTH_URL = "https://accounts.spotify.com/authorize"
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    private const val API_BASE_URL = "https://api.spotify.com/v1"
    
    // Generar URL de autorización
    fun getAuthorizationUrl(context: Context): String? {
        val clientId = Config.getSpotifyClientId(context)
        return if (clientId != null) {
            "$AUTH_URL?client_id=$clientId&response_type=code&redirect_uri=${Config.SPOTIFY_REDIRECT_URI}&scope=${Config.SPOTIFY_SCOPES.replace(" ", "%20")}"
        } else {
            null
        }
    }
    
    // Iniciar flujo OAuth (abrir browser)
    fun startOAuthFlow(context: Context): Boolean {
        val authUrl = getAuthorizationUrl(context)
        return if (authUrl != null) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(authUrl))
            context.startActivity(intent)
            true
        } else {
            false
        }
    }
    
    // Intercambiar código de autorización por tokens
    fun exchangeCodeForTokens(context: Context, authCode: String, callback: (SpotifyTokens?, String?) -> Unit) {
        if (!Config.hasSpotifyCredentials(context)) {
            callback(null, "Spotify credentials not configured")
            return
        }
        
        val authHeader = createBasicAuthHeader(context)
        if (authHeader == null) {
            callback(null, "Failed to create auth header")
            return
        }
        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", authCode)
            .add("redirect_uri", Config.SPOTIFY_REDIRECT_URI)
            .build()
        
        val request = Request.Builder()
            .url(TOKEN_URL)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Error de red: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val tokens = gson.fromJson(body, SpotifyTokens::class.java)
                        callback(tokens, null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing tokens: ${e.message}")
                    }
                } else {
                    callback(null, "Error HTTP ${response.code}: $body")
                }
            }
        })
    }
    
    // Renovar access token usando refresh token
    fun refreshAccessToken(context: Context, refreshToken: String, callback: (String?, String?) -> Unit) {
        if (!Config.hasSpotifyCredentials(context)) {
            callback(null, "Spotify credentials not configured")
            return
        }
        
        val authHeader = createBasicAuthHeader(context)
        if (authHeader == null) {
            callback(null, "Failed to create auth header")
            return
        }
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        
        val request = Request.Builder()
            .url(TOKEN_URL)
            .addHeader("Authorization", authHeader)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .post(formBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Error de red: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val tokens = gson.fromJson(body, SpotifyTokens::class.java)
                        callback(tokens.accessToken, null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing token: ${e.message}")
                    }
                } else {
                    callback(null, "Error HTTP ${response.code}: $body")
                }
            }
        })
    }
    
    // Obtener playlists del usuario
    fun getUserPlaylists(accessToken: String, callback: (List<SpotifyPlaylist>?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$API_BASE_URL/me/playlists")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Error de red: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val playlistResponse = gson.fromJson(body, SpotifyPlaylistResponse::class.java)
                        callback(playlistResponse.items, null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing playlists: ${e.message}")
                    }
                } else {
                    callback(null, "Error HTTP ${response.code}: $body")
                }
            }
        })
    }
    
    // Obtener tracks de una playlist
    fun getPlaylistTracks(accessToken: String, playlistId: String, callback: (List<SpotifyTrack>?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$API_BASE_URL/playlists/$playlistId/tracks")
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Error de red: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val tracksResponse = gson.fromJson(body, SpotifyTracksResponse::class.java)
                        val tracks = tracksResponse.items.mapNotNull { it.track }
                        callback(tracks, null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing tracks: ${e.message}")
                    }
                } else {
                    callback(null, "Error HTTP ${response.code}: $body")
                }
            }
        })
    }
    
    private fun createBasicAuthHeader(context: Context): String {
        val credentials = "${Config.getSpotifyClientId(context)}:${Config.getSpotifyClientSecret(context)}"
        val encodedCredentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
        return "Basic $encodedCredentials"
    }
}

// Data classes para Spotify API
data class SpotifyTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("scope") val scope: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("refresh_token") val refreshToken: String?
)

data class SpotifyPlaylistResponse(
    val items: List<SpotifyPlaylist>
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val tracks: SpotifyPlaylistTracks?,
    val images: List<SpotifyImage>?
) {
    fun getTrackCount(): String {
        return tracks?.total?.let { "$it songs" } ?: "0 songs"
    }
    
    fun getImageUrl(): String {
        return images?.firstOrNull()?.url ?: ""
    }
}

data class SpotifyPlaylistTracks(
    val total: Int
)

data class SpotifyImage(
    val url: String,
    val height: Int?,
    val width: Int?
)

data class SpotifyTracksResponse(
    val items: List<SpotifyTrackItem>
)

data class SpotifyTrackItem(
    val track: SpotifyTrack?
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>
) {
    fun getArtistNames(): String {
        return artists.joinToString(", ") { it.name }
    }
    
    fun getDisplayName(): String {
        return "$name - ${getArtistNames()}"
    }
}

data class SpotifyArtist(
    val name: String
)
