package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Config - Objeto singleton para gestión de configuración de la aplicación
 * 
 * Maneja:
 * - Configuración de temas (claro/oscuro)
 * - Tokens y autenticación de Spotify
 * - Constantes de API de Spotify
 * - Persistencia de preferencias usando SharedPreferences
 * 
 * Todos los datos se almacenan de forma segura en SharedPreferences
 * y se accede a través de métodos thread-safe.
 */
object Config {
    
    // === CONSTANTES PRIVADAS ===
    
    /** Nombre del archivo de preferencias */
    private const val PREFS_NAME = "plyr_config"
    
    // Claves para SharedPreferences
    private const val KEY_THEME = "theme"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_AUDIO_QUALITY = "audio_quality"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_SPOTIFY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_SPOTIFY_TOKEN_EXPIRY = "spotify_token_expiry"
    private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
    private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
    
    // Valores por defecto
    private const val DEFAULT_THEME = "dark"
    private const val DEFAULT_SEARCH_ENGINE = "spotify"
    private const val DEFAULT_AUDIO_QUALITY = "medium"
    private const val DEFAULT_REPEAT_MODE = "off"

    // === CONSTANTES PÚBLICAS DE SPOTIFY ===

    /** URI de redirección para OAuth de Spotify */
    const val SPOTIFY_REDIRECT_URI = "plyr://spotify/callback"
    
    /** Permisos solicitados a Spotify */
    const val SPOTIFY_SCOPES = "playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-library-modify user-library-read"
    
    // === CONSTANTES PÚBLICAS DE CALIDAD DE AUDIO ===

    /** Calidades de audio disponibles */
    const val AUDIO_QUALITY_WORST = "worst"
    const val AUDIO_QUALITY_MEDIUM = "medium"
    const val AUDIO_QUALITY_BEST = "best"

    // === CONSTANTES PÚBLICAS DE MODO DE REPETICIÓN ===

    /** Modos de repetición disponibles */
    const val REPEAT_MODE_OFF = "off"        // Sin repetición
    const val REPEAT_MODE_ONE = "one"        // Repetir una sola vez
    const val REPEAT_MODE_ALL = "all"        // Repetir indefinidamente

    // === MÉTODOS PRIVADOS ===
    
    /**
     * Obtiene la instancia de SharedPreferences para la aplicación.
     * @param context Contexto de la aplicación
     * @return SharedPreferences configurado con el nombre correcto
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // === GESTIÓN DE TEMAS ===
    
    /**
     * Establece el tema de la aplicación.
     * @param context Contexto de la aplicación
     * @param theme Tema a establecer ("dark", "light")
     */
    fun setTheme(context: Context, theme: String) {
        getPrefs(context).edit { 
            putString(KEY_THEME, theme) 
        }
    }
    
    /**
     * Obtiene el tema actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Tema actual (por defecto "dark")
     */
    fun getTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }
    
    // === GESTIÓN DE TOKENS DE SPOTIFY ===
    
    /**
     * Almacena los tokens de autenticación de Spotify.
     * Calcula automáticamente el tiempo de expiración basado en expiresIn.
     * 
     * @param context Contexto de la aplicación
     * @param accessToken Token de acceso para API calls
     * @param refreshToken Token para renovar el acceso (puede ser null)
     * @param expiresIn Tiempo de vida del token en segundos
     */
    fun setSpotifyTokens(context: Context, accessToken: String, refreshToken: String?, expiresIn: Int) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000L)
        getPrefs(context).edit { 
            putString(KEY_SPOTIFY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(KEY_SPOTIFY_REFRESH_TOKEN, it) }
            putLong(KEY_SPOTIFY_TOKEN_EXPIRY, expiryTime)
        }
    }
    
    /**
     * Obtiene el token de acceso de Spotify si es válido.
     * Si el token ha expirado pero existe un refresh token válido,
     * automáticamente renueva el token y devuelve el nuevo.
     *
     * @param context Contexto de la aplicación
     * @return Token de acceso válido o null si no se pudo obtener/renovar
     */
    fun getSpotifyAccessToken(context: Context): String? {
        val prefs = getPrefs(context)
        val token = prefs.getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
        val expiryTime = prefs.getLong(KEY_SPOTIFY_TOKEN_EXPIRY, 0)

        // Verificar que el token no haya expirado
        if (token != null && System.currentTimeMillis() < expiryTime) {
            return token
        }

        // Token expirado o no existe, intentar renovar con el refresh token
        val refreshToken = prefs.getString(KEY_SPOTIFY_REFRESH_TOKEN, null)
        if (refreshToken == null) {
            android.util.Log.d("Config", "No hay refresh token disponible para renovar")
            return null
        }

        android.util.Log.d("Config", "Token expirado, renovando automáticamente...")

        // Renovar el token de forma síncrona
        return runBlocking {
            withContext(Dispatchers.IO) {
                suspendCoroutine<String?> { continuation ->
                    com.plyr.network.SpotifyRepository.refreshAccessToken(context, refreshToken) { newAccessToken, error ->
                        if (error != null) {
                            android.util.Log.e("Config", "Error renovando token: $error")
                            continuation.resume(null)
                        } else if (newAccessToken != null) {
                            // Guardar el nuevo token
                            val expiresIn = 3600 // Spotify tokens duran 1 hora
                            setSpotifyTokens(context, newAccessToken, refreshToken, expiresIn)
                            android.util.Log.d("Config", "Token renovado exitosamente")
                            continuation.resume(newAccessToken)
                        } else {
                            android.util.Log.e("Config", "Respuesta inesperada al renovar token")
                            continuation.resume(null)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Obtiene el token de renovación de Spotify.
     * @param context Contexto de la aplicación
     * @return Token de renovación o null si no existe
     */
    fun getSpotifyRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_SPOTIFY_REFRESH_TOKEN, null)
    }
    
    /**
     * Elimina todos los tokens de Spotify almacenados.
     * Útil para cerrar sesión o limpiar autenticación.
     * 
     * @param context Contexto de la aplicación
     */
    fun clearSpotifyTokens(context: Context) {
        getPrefs(context).edit { 
            remove(KEY_SPOTIFY_ACCESS_TOKEN)
            remove(KEY_SPOTIFY_REFRESH_TOKEN)
            remove(KEY_SPOTIFY_TOKEN_EXPIRY)
        }
    }
    
    /**
     * Verifica si hay una conexión válida con Spotify.
     * Considera válida la conexión si existe un token de acceso válido
     * o un token de renovación (que puede usarse para obtener nuevos tokens).
     * 
     * @param context Contexto de la aplicación
     * @return true si hay conexión con Spotify, false en caso contrario
     */
    fun isSpotifyConnected(context: Context): Boolean {
        val hasValidAccessToken = getSpotifyAccessToken(context) != null
        val hasRefreshToken = getSpotifyRefreshToken(context) != null
        return hasValidAccessToken || hasRefreshToken
    }
    
    /**
     * Obtiene el Client ID de Spotify configurado por el usuario.
     * @param context Contexto de la aplicación
     * @return Client ID del usuario o null si no está configurado
     */
    fun getSpotifyClientId(context: Context): String? {
        return getPrefs(context).getString(KEY_SPOTIFY_CLIENT_ID, null)
    }
    
    /**
     * Obtiene el Client Secret de Spotify configurado por el usuario.
     * @param context Contexto de la aplicación
     * @return Client Secret del usuario o null si no está configurado
     */
    fun getSpotifyClientSecret(context: Context): String? {
        return getPrefs(context).getString(KEY_SPOTIFY_CLIENT_SECRET, null)
    }
    
    /**
     * Establece el Client ID de Spotify del usuario.
     * @param context Contexto de la aplicación
     * @param clientId Client ID del usuario
     */
    fun setSpotifyClientId(context: Context, clientId: String) {
        getPrefs(context).edit {
            putString(KEY_SPOTIFY_CLIENT_ID, clientId.trim())
        }
    }
    
    /**
     * Establece el Client Secret de Spotify del usuario.
     * @param context Contexto de la aplicación
     * @param clientSecret Client Secret del usuario
     */
    fun setSpotifyClientSecret(context: Context, clientSecret: String) {
        getPrefs(context).edit {
            putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret.trim())
        }
    }

    /**
     * Verifica si el usuario tiene credenciales de Spotify configuradas.
     * @param context Contexto de la aplicación
     * @return true si tiene credenciales configuradas, false en caso contrario
     */
    fun hasSpotifyCredentials(context: Context): Boolean {
        val prefs = getPrefs(context)
        val clientId = prefs.getString(KEY_SPOTIFY_CLIENT_ID, null)
        val clientSecret = prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, null)
        return !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    }

    // === GESTIÓN DE MOTOR DE BÚSQUEDA ===
    
    /**
     * Establece el motor de búsqueda predeterminado.
     * @param context Contexto de la aplicación
     * @param searchEngine Motor de búsqueda a establecer ("spotify", "youtube")
     */
    fun setSearchEngine(context: Context, searchEngine: String) {
        getPrefs(context).edit { 
            putString(KEY_SEARCH_ENGINE, searchEngine) 
        }
    }
    
    /**
     * Obtiene el motor de búsqueda actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Motor de búsqueda actual (por defecto "spotify")
     */
    fun getSearchEngine(context: Context): String {
        return getPrefs(context).getString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE) ?: DEFAULT_SEARCH_ENGINE
    }

    // === GESTIÓN DE CALIDAD DE AUDIO ===

    /**
     * Establece la calidad de audio predeterminada.
     * @param context Contexto de la aplicación
     * @param quality Calidad de audio a establecer ("worst", "medium", "best")
     */
    fun setAudioQuality(context: Context, quality: String) {
        getPrefs(context).edit {
            putString(KEY_AUDIO_QUALITY, quality)
        }
    }

    /**
     * Obtiene la calidad de audio actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Calidad de audio actual (por defecto "medium")
     */
    fun getAudioQuality(context: Context): String {
        return getPrefs(context).getString(KEY_AUDIO_QUALITY, DEFAULT_AUDIO_QUALITY) ?: DEFAULT_AUDIO_QUALITY
    }

    // === GESTIÓN DE MODO DE REPETICIÓN ===

    /**
     * Establece el modo de repetición.
     * @param context Contexto de la aplicación
     * @param repeatMode Modo de repetición a establecer ("off", "one", "all")
     */
    fun setRepeatMode(context: Context, repeatMode: String) {
        getPrefs(context).edit {
            putString(KEY_REPEAT_MODE, repeatMode)
        }
    }

    /**
     * Obtiene el modo de repetición actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Modo de repetición actual (por defecto "off")
     */
    fun getRepeatMode(context: Context): String {
        return getPrefs(context).getString(KEY_REPEAT_MODE, DEFAULT_REPEAT_MODE) ?: DEFAULT_REPEAT_MODE
    }

    /**
     * Obtiene el siguiente modo de repetición en el ciclo.
     * @param currentMode Modo actual
     * @return Siguiente modo en el ciclo off -> one -> all -> off
     */
    fun getNextRepeatMode(currentMode: String): String {
        return when (currentMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_OFF
            else -> REPEAT_MODE_OFF
        }
    }

    // === GESTIÓN DE TIMESTAMPS DE TOKENS ===

    /**
     * Obtiene el timestamp de cuando se guardó el token actual.
     * @param context Contexto de la aplicación
     * @return Timestamp en milisegundos o 0 si no existe
     */
    fun getSpotifyTokenTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_SPOTIFY_TOKEN_EXPIRY, 0L) - (3600 * 1000L) // Restar la duración del token
    }

    /**
     * Obtiene el tiempo de expiración en segundos del token actual.
     * @return Tiempo de expiración en segundos (por defecto 3600 = 1 hora)
     */
    fun getSpotifyTokenExpiresIn(): Int {
        // Los tokens de Spotify duran 1 hora por defecto
        return 3600
    }

}
