package com.plyr.network

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import android.util.Base64
import android.content.Context
import com.plyr.utils.Config
import com.plyr.utils.SpotifyTokenManager

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
    
    // Obtener playlists del usuario con renovación automática de tokens
    suspend fun getUserPlaylistsWithAutoRefresh(context: Context, callback: (List<SpotifyPlaylist>?, String?) -> Unit) {
        SpotifyTokenManager.withValidToken(context) { token ->
            getUserPlaylists(token, callback)
        }
    }

    // Obtener playlists del usuario con paginación (versión original para compatibilidad)
    fun getUserPlaylists(accessToken: String, callback: (List<SpotifyPlaylist>?, String?) -> Unit) {
        val maxLimit = 50 // Máximo permitido por Spotify
        var allPlaylists = mutableListOf<SpotifyPlaylist>()
        var pageCount = 0
        
        // Función recursiva para obtener todas las páginas
        fun fetchPage(offset: Int = 0) {
            val request = Request.Builder()
                .url("$API_BASE_URL/me/playlists?limit=$maxLimit&offset=$offset&fields=items(id,name,description,tracks(total),images)")
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
                            android.util.Log.d("SpotifyRepository", "User playlists response (offset=$offset): ${body.take(200)}...")
                            val playlistResponse = gson.fromJson(body, SpotifyPlaylistResponse::class.java)
                            
                            // Debug: log tracks data for cada playlist
                            playlistResponse.items.forEachIndexed { index, playlist ->
                                android.util.Log.d("SpotifyRepository", "Playlist $index - '${playlist.name}': tracks=${playlist.tracks}, tracks.total=${playlist.tracks?.total}")
                            }
                            
                            // Acumular resultados
                            allPlaylists.addAll(playlistResponse.items)
                            pageCount++
                            
                            android.util.Log.d("SpotifyRepository", "Page $pageCount loaded: ${playlistResponse.items.size} playlists, total accumulated: ${allPlaylists.size}")
                            
                            // Enviar resultados actualizados después de cada página
                            callback(allPlaylists.toList(), null)
                            
                            // Verificar si hay más páginas que cargar
                            val hasMorePlaylists = playlistResponse.items.size == maxLimit
                            val nextOffset = offset + maxLimit
                            val wouldExceedLimit = nextOffset >= 1000
                            
                            // Si hay más contenido y no excedemos el límite, continuar paginando
                            if (hasMorePlaylists && !wouldExceedLimit) {
                                android.util.Log.d("SpotifyRepository", "Fetching next playlists page: offset=$nextOffset")
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    fetchPage(nextOffset)
                                }, 200)
                            } else {
                                if (wouldExceedLimit) {
                                    android.util.Log.d("SpotifyRepository", "Playlists pagination stopped: reached API limit (offset would be $nextOffset >= 1000)")
                                } else {
                                    android.util.Log.d("SpotifyRepository", "Playlists pagination completed: no more results available")
                                }
                                android.util.Log.d("SpotifyRepository", "Final playlists count: ${allPlaylists.size}")
                            }
                        } catch (e: Exception) {
                            callback(null, "Error parsing playlists: ${e.message}")
                        }
                    } else {
                        callback(null, "Error HTTP ${response.code}: $body")
                    }
                }
            })
        }
        
        // Iniciar la paginación
        fetchPage(0)
    }
    
    // Obtener tracks de un álbum con paginación
    fun getAlbumTracks(accessToken: String, albumId: String, callback: (List<SpotifyTrack>?, String?) -> Unit) {
        val maxLimit = 50 // Máximo permitido por Spotify
        var allTracks = mutableListOf<SpotifyTrack>()
        var pageCount = 0
        
        // Función recursiva para obtener todas las páginas
        fun fetchPage(offset: Int = 0) {
            val request = Request.Builder()
                .url("$API_BASE_URL/albums/$albumId/tracks?limit=$maxLimit&offset=$offset")
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
                            android.util.Log.d("SpotifyRepository", "Album tracks response (offset=$offset): ${body.take(200)}...")
                            val tracksResponse = gson.fromJson(body, SpotifyTracksSearchResultRaw::class.java)
                            
                            // Acumular resultados (filtrando nulls)
                            allTracks.addAll(tracksResponse.items.filterNotNull())
                            pageCount++
                            
                            android.util.Log.d("SpotifyRepository", "Page $pageCount loaded: ${tracksResponse.items.filterNotNull().size} tracks, total accumulated: ${allTracks.size}")
                            
                            // Enviar resultados actualizados después de cada página
                            callback(allTracks.toList(), null)
                            
                            // Verificar si hay más páginas que cargar
                            val hasMoreTracks = tracksResponse.items.size == maxLimit
                            val nextOffset = offset + maxLimit
                            val wouldExceedLimit = nextOffset >= 1000
                            
                            // Si hay más contenido y no excedemos el límite, continuar paginando
                            if (hasMoreTracks && !wouldExceedLimit) {
                                android.util.Log.d("SpotifyRepository", "Fetching next album tracks page: offset=$nextOffset")
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    fetchPage(nextOffset)
                                }, 200)
                            } else {
                                if (wouldExceedLimit) {
                                    android.util.Log.d("SpotifyRepository", "Album tracks pagination stopped: reached API limit (offset would be $nextOffset >= 1000)")
                                } else {
                                    android.util.Log.d("SpotifyRepository", "Album tracks pagination completed: no more results available")
                                }
                                android.util.Log.d("SpotifyRepository", "Final album tracks count: ${allTracks.size}")
                            }
                        } catch (e: Exception) {
                            callback(null, "Error parsing album tracks: ${e.message}")
                        }
                    } else {
                        callback(null, "Error HTTP ${response.code}: $body")
                    }
                }
            })
        }
        
        // Iniciar la paginación
        fetchPage(0)
    }
    
    // Obtener tracks de una playlist con paginación
    fun getPlaylistTracks(accessToken: String, playlistId: String, callback: (List<SpotifyPlaylistTrack>?, String?) -> Unit) {
        val maxLimit = 50 // Máximo permitido por Spotify
        var allTracks = mutableListOf<SpotifyPlaylistTrack>()
        var pageCount = 0
        
        // Función recursiva para obtener todas las páginas
        fun fetchPage(offset: Int = 0) {
            val request = Request.Builder()
                .url("$API_BASE_URL/playlists/$playlistId/tracks?limit=$maxLimit&offset=$offset")
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
                            android.util.Log.d("SpotifyRepository", "Playlist tracks response (offset=$offset): ${body.take(200)}...")
                            val tracksResponse = gson.fromJson(body, SpotifyPlaylistTracksResponseRaw::class.java)
                            
                            // Acumular resultados (filtrando nulls)
                            allTracks.addAll(tracksResponse.items.filterNotNull())
                            pageCount++
                            
                            android.util.Log.d("SpotifyRepository", "Page $pageCount loaded: ${tracksResponse.items.filterNotNull().size} tracks, total accumulated: ${allTracks.size}")
                            
                            // Enviar resultados actualizados después de cada página
                            callback(allTracks.toList(), null)
                            
                            // Verificar si hay más páginas que cargar
                            val hasMoreTracks = tracksResponse.items.size == maxLimit
                            val nextOffset = offset + maxLimit
                            val wouldExceedLimit = nextOffset >= 1000
                            
                            // Si hay más contenido y no excedemos el límite, continuar paginando
                            if (hasMoreTracks && !wouldExceedLimit) {
                                android.util.Log.d("SpotifyRepository", "Fetching next playlist tracks page: offset=$nextOffset")
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    fetchPage(nextOffset)
                                }, 200)
                            } else {
                                if (wouldExceedLimit) {
                                    android.util.Log.d("SpotifyRepository", "Playlist tracks pagination stopped: reached API limit (offset would be $nextOffset >= 1000)")
                                } else {
                                    android.util.Log.d("SpotifyRepository", "Playlist tracks pagination completed: no more results available")
                                }
                                android.util.Log.d("SpotifyRepository", "Final playlist tracks count: ${allTracks.size}")
                            }
                        } catch (e: Exception) {
                            callback(null, "Error parsing playlist tracks: ${e.message}")
                        }
                    } else {
                        callback(null, "Error HTTP ${response.code}: $body")
                    }
                }
            })
        }
        
        // Iniciar la paginación
        fetchPage(0)
    }
    
    // Obtener álbumes de un artista con paginación
    fun getArtistAlbums(accessToken: String, artistId: String, callback: (List<SpotifyAlbum>?, String?) -> Unit) {
        val maxLimit = 50 // Máximo permitido por Spotify
        var allAlbums = mutableListOf<SpotifyAlbum>()
        var pageCount = 0
        
        // Función recursiva para obtener todas las páginas
        fun fetchPage(offset: Int = 0) {
            val request = Request.Builder()
                .url("$API_BASE_URL/artists/$artistId/albums?include_groups=album,single&limit=$maxLimit&offset=$offset")
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
                            android.util.Log.d("SpotifyRepository", "Artist albums response (offset=$offset): ${body.take(200)}...")
                            val albumsResponse = gson.fromJson(body, SpotifyAlbumsSearchResultRaw::class.java)
                            
                            // Acumular resultados (filtrando nulls)
                            allAlbums.addAll(albumsResponse.items.filterNotNull())
                            pageCount++
                            
                            android.util.Log.d("SpotifyRepository", "Page $pageCount loaded: ${albumsResponse.items.filterNotNull().size} albums, total accumulated: ${allAlbums.size}")
                            
                            // Enviar resultados actualizados después de cada página
                            callback(allAlbums.toList(), null)
                            
                            // Verificar si hay más páginas que cargar
                            val hasMoreAlbums = albumsResponse.items.size == maxLimit
                            val nextOffset = offset + maxLimit
                            val wouldExceedLimit = nextOffset >= 1000
                            
                            // Si hay más contenido y no excedemos el límite, continuar paginando
                            if (hasMoreAlbums && !wouldExceedLimit) {
                                android.util.Log.d("SpotifyRepository", "Fetching next artist albums page: offset=$nextOffset")
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    fetchPage(nextOffset)
                                }, 200)
                            } else {
                                if (wouldExceedLimit) {
                                    android.util.Log.d("SpotifyRepository", "Artist albums pagination stopped: reached API limit (offset would be $nextOffset >= 1000)")
                                } else {
                                    android.util.Log.d("SpotifyRepository", "Artist albums pagination completed: no more results available")
                                }
                                android.util.Log.d("SpotifyRepository", "Final artist albums count: ${allAlbums.size}")
                            }
                        } catch (e: Exception) {
                            callback(null, "Error parsing artist albums: ${e.message}")
                        }
                    } else {
                        callback(null, "Error HTTP ${response.code}: $body")
                    }
                }
            })
        }
        
        // Iniciar la paginación
        fetchPage(0)
    }
    
    // Buscar todo tipo de contenido en Spotify (canciones, álbumes, artistas, playlists)
    fun searchAll(accessToken: String, query: String, callback: (SpotifySearchAllResponse?, String?) -> Unit) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("$API_BASE_URL/search?q=$encodedQuery&type=track,album,artist,playlist&limit=50")
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
                        android.util.Log.d("SpotifyRepository", "Search all response: $body")
                        val searchResponse = gson.fromJson(body, SpotifySearchAllResponse::class.java)
                        callback(searchResponse, null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing search results: ${e.message}")
                    }
                } else {
                    callback(null, "Error HTTP ${response.code}: $body")
                }
            }
        })
    }
    
    // Buscar canciones en Spotify (función mantenida para compatibilidad)
    fun searchTracks(accessToken: String, query: String, callback: (List<SpotifyTrack>?, String?) -> Unit) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("$API_BASE_URL/search?q=$encodedQuery&type=track&limit=20")
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
                        android.util.Log.d("SpotifyRepository", "Search response: $body")
                        val searchResponse = gson.fromJson(body, SpotifySearchResponseRaw::class.java)
                        callback(searchResponse.tracks.items.filterNotNull(), null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing search results: ${e.message}")
                    }
                } else {
                    callback(null, "Error HTTP ${response.code}: $body")
                }
            }
        })
    }

    // Seguir una playlist en Spotify
    fun followPlaylist(accessToken: String, playlistId: String, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/playlists/$playlistId/followers")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .put(RequestBody.create("application/json".toMediaType(), "{}"))
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Error de red: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, null)
                } else {
                    val errorBody = response.body?.string()
                    callback(false, "Error HTTP ${response.code}: $errorBody")
                }
            }
        })
    }

    // Añadir una canción a una playlist en Spotify
    fun addTrackToPlaylist(accessToken: String, playlistId: String, trackId: String, callback: (Boolean, String?) -> Unit) {
        val jsonBody = gson.toJson(mapOf("uris" to listOf("spotify:track:$trackId")))
        val requestBody = RequestBody.create("application/json".toMediaType(), jsonBody)

        val request = Request.Builder()
            .url("$API_BASE_URL/playlists/$playlistId/tracks")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Error de red: ${e.message}")
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, null)
                } else {
                    val errorBody = response.body?.string()
                    callback(false, "Error HTTP ${response.code}: $errorBody")
                }
            }
        })
    }

    // Buscar todo tipo de contenido en Spotify con paginación automática
    fun searchAllWithPagination(accessToken: String, query: String, callback: (SpotifySearchAllResponse?, String?) -> Unit) {
        // Por ahora, usar la función searchAll existente que ya maneja los resultados
        searchAll(accessToken, query, callback)
    }

    // Guardar álbum en la biblioteca del usuario
    fun saveAlbum(accessToken: String, albumId: String, callback: (Boolean, String?) -> Unit) {
        val jsonBody = gson.toJson(mapOf("ids" to listOf(albumId)))
        val requestBody = RequestBody.create("application/json".toMediaType(), jsonBody)

        val request = Request.Builder()
            .url("$API_BASE_URL/me/albums")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Error de red: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, null)
                } else {
                    val errorBody = response.body?.string()
                    callback(false, "Error HTTP ${response.code}: $errorBody")
                }
            }
        })
    }

    // Añadir múltiples tracks a una playlist en Spotify
    fun addTracksToPlaylist(accessToken: String, playlistId: String, trackUris: List<String>, callback: (Boolean, String?) -> Unit) {
        val jsonBody = gson.toJson(mapOf("uris" to trackUris))
        val requestBody = RequestBody.create("application/json".toMediaType(), jsonBody)

        val request = Request.Builder()
            .url("$API_BASE_URL/playlists/$playlistId/tracks")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Error de red: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, null)
                } else {
                    val errorBody = response.body?.string()
                    callback(false, "Error HTTP ${response.code}: $errorBody")
                }
            }
        })
    }

    // Crear una nueva playlist en Spotify
    fun createPlaylist(accessToken: String, name: String, description: String, isPublic: Boolean, callback: (Boolean, String?) -> Unit) {
        // Primero necesitamos obtener el ID del usuario
        getUserProfile(accessToken) { userId, error ->
            if (userId != null) {
                val playlistData = mapOf(
                    "name" to name,
                    "description" to description,
                    "public" to isPublic
                )
                val jsonBody = gson.toJson(playlistData)
                val requestBody = RequestBody.create("application/json".toMediaType(), jsonBody)

                val request = Request.Builder()
                    .url("$API_BASE_URL/users/$userId/playlists")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback(false, "Error de red: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            callback(true, null)
                        } else {
                            val errorBody = response.body?.string()
                            callback(false, "Error HTTP ${response.code}: $errorBody")
                        }
                    }
                })
            } else {
                callback(false, error ?: "Error obteniendo perfil de usuario")
            }
        }
    }

    // Obtener el perfil del usuario (necesario para crear playlists)
    private fun getUserProfile(accessToken: String, callback: (String?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$API_BASE_URL/me")
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
                        val userProfile = gson.fromJson(body, SpotifyUserProfile::class.java)
                        callback(userProfile.id, null)
                    } catch (e: Exception) {
                        callback(null, "Error parsing user profile: ${e.message}")
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
    val href: String?,
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
    val artists: List<SpotifyArtist>,
    @SerializedName("duration_ms") val durationMs: Int? = null
) {
    fun getArtistNames(): String {
        return artists.joinToString(", ") { it.name }
    }
    
    fun getDisplayName(): String {
        return "$name - ${getArtistNames()}"
    }
    
    fun getDurationText(): String {
        return durationMs?.let {
            val minutes = it / 60000
            val seconds = (it % 60000) / 1000
            "${minutes}:${seconds.toString().padStart(2, '0')}"
        } ?: "0:00"
    }
}

data class SpotifySearchResponse(
    val tracks: SpotifyTracksSearchResult
)

data class SpotifySearchResponseRaw(
    val tracks: SpotifyTracksSearchResultRaw
)

data class SpotifySearchAllResponse(
    val tracks: SpotifyTracksSearchResult,
    val albums: SpotifyAlbumsSearchResult,
    val artists: SpotifyArtistsSearchResult,
    val playlists: SpotifyPlaylistsSearchResult
)

data class SpotifyTracksSearchResult(
    val items: List<SpotifyTrack>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyAlbumsSearchResult(
    val items: List<SpotifyAlbum>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyArtistsSearchResult(
    val items: List<SpotifyArtistFull>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyPlaylistsSearchResult(
    val items: List<SpotifyPlaylist>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyAlbum(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>,
    val images: List<SpotifyImage>?,
    @SerializedName("release_date") val release_date: String? = null,
    @SerializedName("total_tracks") val total_tracks: Int? = null
) {
    fun getArtistNames(): String {
        return artists.joinToString(", ") { it.name }
    }
    
    fun getDisplayName(): String {
        return "$name - ${getArtistNames()}"
    }
    
    fun getImageUrl(): String {
        return images?.firstOrNull()?.url ?: ""
    }
}

data class SpotifyArtistFull(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>?,
    val followers: SpotifyFollowers?,
    val genres: List<String>?
) {
    fun getImageUrl(): String {
        return images?.firstOrNull()?.url ?: ""
    }
    
    fun getFollowersCount(): String {
        return followers?.total?.let { "${it} seguidores" } ?: "0 seguidores"
    }
    
    fun getGenresText(): String {
        return genres?.joinToString(", ") ?: "Sin géneros"
    }
}

data class SpotifyFollowers(
    val total: Int
)

data class SpotifyArtist(
    val name: String
)

// Data classes para contenido interno
data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrack>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyPlaylistTracksResponseRaw(
    val items: List<SpotifyPlaylistTrack?>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyPlaylistTrack(
    val track: SpotifyTrack?
)

// Data classes auxiliares para parsing (con nullable items)
data class SpotifyTracksSearchResultRaw(
    val items: List<SpotifyTrack?>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyAlbumsSearchResultRaw(
    val items: List<SpotifyAlbum?>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyArtistsSearchResultRaw(
    val items: List<SpotifyArtistFull?>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifyPlaylistsSearchResultRaw(
    val items: List<SpotifyPlaylist?>,
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
    val next: String? = null
)

data class SpotifySearchAllResponseRaw(
    val tracks: SpotifyTracksSearchResultRaw,
    val albums: SpotifyAlbumsSearchResultRaw,
    val artists: SpotifyArtistsSearchResultRaw,
    val playlists: SpotifyPlaylistsSearchResultRaw
)

// Data class para perfil de usuario de Spotify
data class SpotifyUserProfile(
    val id: String,
    @SerializedName("display_name") val displayName: String?,
    val email: String?,
    val images: List<SpotifyImage>?
)

