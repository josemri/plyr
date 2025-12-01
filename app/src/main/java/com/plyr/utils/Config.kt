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
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_SPOTIFY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_SPOTIFY_TOKEN_EXPIRY = "spotify_token_expiry"
    private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
    private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
    private const val KEY_SPOTIFY_USER_NAME = "spotify_user_name"
    private const val KEY_ACOUSTID_API_KEY = "acoustid_api_key"
    private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
    private const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
    private const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"

    // Valores por defecto
    private const val DEFAULT_THEME = "system" // Por defecto en nuevas instalaciones seguir el tema del sistema
    private const val DEFAULT_SEARCH_ENGINE = "spotify"
    private const val DEFAULT_AUDIO_QUALITY = "high"
    private const val DEFAULT_REPEAT_MODE = "off"
    private const val DEFAULT_LANGUAGE = "english"
    private const val DEFAULT_SWIPE_LEFT_ACTION = "add_to_queue"
    private const val DEFAULT_SWIPE_RIGHT_ACTION = "add_to_liked_songs"

    // === CONSTANTES PÚBLICAS DE SPOTIFY ===

    /** URI de redirección para OAuth de Spotify */
    const val SPOTIFY_REDIRECT_URI = "plyr://spotify/callback"
    
    /** Permisos solicitados a Spotify */
    const val SPOTIFY_SCOPES = "playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-library-modify user-library-read user-follow-modify user-follow-read user-read-private"

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

    // === CONSTANTES PÚBLICAS DE IDIOMAS ===

    /** Idiomas disponibles */
    const val LANGUAGE_SPANISH = "español"
    const val LANGUAGE_ENGLISH = "english"
    const val LANGUAGE_CATALAN = "català"
    // Ajuste: usar la misma clave que en Translations ("日本語") para que coincida la búsqueda
    const val LANGUAGE_JAPANESE = "日本語"

    // === CONSTANTES PÚBLICAS DE ACCIONES DE SWIPE ===

    /** Acciones de swipe disponibles */
    const val SWIPE_ACTION_ADD_TO_QUEUE = "add_to_queue"
    const val SWIPE_ACTION_ADD_TO_LIKED = "add_to_liked_songs"
    const val SWIPE_ACTION_ADD_TO_PLAYLIST = "add_to_playlist"
    const val SWIPE_ACTION_SHARE = "share"
    const val SWIPE_ACTION_DOWNLOAD = "download"

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
     * @return Tema actual (por defecto "system")
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
            return null
        }

        // Renovar el token de forma síncrona
        return runBlocking {
            withContext(Dispatchers.IO) {
                suspendCoroutine { continuation ->
                    com.plyr.network.SpotifyRepository.refreshAccessToken(context, refreshToken) { newAccessToken, error ->
                        if (error != null) {
                            continuation.resume(null)
                        } else if (newAccessToken != null) {
                            // Guardar el nuevo token
                            val expiresIn = 3600 // Spotify tokens duran 1 hora
                            setSpotifyTokens(context, newAccessToken, refreshToken, expiresIn)
                            continuation.resume(newAccessToken)
                        } else {
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

    // === GESTIÓN DE IDIOMA ===

    /**
     * Establece el idioma de la aplicación.
     * @param context Contexto de la aplicación
     * @param language Idioma a establecer ("español", "english", "català")
     */
    fun setLanguage(context: Context, language: String) {
        getPrefs(context).edit {
            putString(KEY_LANGUAGE, language)
        }
    }

    /**
     * Obtiene el idioma actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Idioma actual (por defecto "español")
     */
    fun getLanguage(context: Context): String {
        val prefs = getPrefs(context)
        val stored = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        // Migrar valor legacy "japanese" (ASCII) a la clave usada en Translations ("日本語")
        if (stored == "japanese") {
            // Actualizar la preferencia para futuras lecturas
            setLanguage(context, LANGUAGE_JAPANESE)
            return LANGUAGE_JAPANESE
        }
        return stored
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

    // === GESTIÓN DE ACCIONES DE SWIPE ===

    /**
     * Establece la acción para el swipe izquierdo.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer
     */
    fun setSwipeLeftAction(context: Context, action: String) {
        getPrefs(context).edit {
            putString(KEY_SWIPE_LEFT_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el swipe izquierdo.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "add_to_queue")
     */
    fun getSwipeLeftAction(context: Context): String {
        return getPrefs(context).getString(KEY_SWIPE_LEFT_ACTION, DEFAULT_SWIPE_LEFT_ACTION) ?: DEFAULT_SWIPE_LEFT_ACTION
    }

    /**
     * Establece la acción para el swipe derecho.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer
     */
    fun setSwipeRightAction(context: Context, action: String) {
        getPrefs(context).edit {
            putString(KEY_SWIPE_RIGHT_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el swipe derecho.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "add_to_liked_songs")
     */
    fun getSwipeRightAction(context: Context): String {
        return getPrefs(context).getString(KEY_SWIPE_RIGHT_ACTION, DEFAULT_SWIPE_RIGHT_ACTION) ?: DEFAULT_SWIPE_RIGHT_ACTION
    }

    // === GESTIÓN DE ACOUSTID API KEY ===

    /**
     * Obtiene la API Key de AcoustID configurada por el usuario.
     * @param context Contexto de la aplicación
     * @return API Key de AcoustID o null si no está configurada
     */
    fun getAcoustidApiKey(context: Context): String? {
        return getPrefs(context).getString(KEY_ACOUSTID_API_KEY, null)
    }

    /**
     * Establece la API Key de AcoustID del usuario.
     * @param context Contexto de la aplicación
     * @param apiKey API Key de AcoustID
     */
    fun setAcoustidApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit {
            putString(KEY_ACOUSTID_API_KEY, apiKey.trim())
        }
    }

    /**
     * Verifica si el usuario tiene una API Key de AcoustID configurada.
     * @param context Contexto de la aplicación
     * @return true si tiene la API Key configurada, false en caso contrario
     */
    fun hasAcoustidApiKey(context: Context): Boolean {
        val apiKey = getPrefs(context).getString(KEY_ACOUSTID_API_KEY, null)
        return !apiKey.isNullOrBlank()
    }

    // === GESTIÓN DE LASTFM API KEY ===

    /**
     * Obtiene la API Key de Last.fm configurada por el usuario.
     * @param context Contexto de la aplicación
     * @return API Key de Last.fm o null si no está configurada
     */
    fun getLastfmApiKey(context: Context): String? {
        return getPrefs(context).getString(KEY_LASTFM_API_KEY, null)
    }

    /**
     * Establece la API Key de Last.fm del usuario.
     * @param context Contexto de la aplicación
     * @param apiKey API Key de Last.fm
     */
    fun setLastfmApiKey(context: Context, apiKey: String) {
        getPrefs(context).edit {
            putString(KEY_LASTFM_API_KEY, apiKey.trim())
        }
    }

    /**
     * Verifica si el usuario tiene una API Key de Last.fm configurada.
     * @param context Contexto de la aplicación
     * @return true si tiene la API Key configurada, false en caso contrario
     */
    fun hasLastfmApiKey(context: Context): Boolean {
        val apiKey = getPrefs(context).getString(KEY_LASTFM_API_KEY, null)
        return !apiKey.isNullOrBlank()
    }

    // === GESTIÓN DE NOMBRE DE USUARIO DE SPOTIFY ===

    /**
     * Obtiene el nombre de usuario de Spotify almacenado.
     * @param context Contexto de la aplicación
     * @return Nombre de usuario de Spotify o null si no está configurado
     */
    fun getSpotifyUserName(context: Context): String? {
        return getPrefs(context).getString(KEY_SPOTIFY_USER_NAME, null)
    }

    /**
     * Establece el nombre de usuario de Spotify.
     * @param context Contexto de la aplicación
     * @param userName Nombre de usuario de Spotify
     */
    fun setSpotifyUserName(context: Context, userName: String) {
        getPrefs(context).edit {
            putString(KEY_SPOTIFY_USER_NAME, userName.trim())
        }
    }

    /**
     * Elimina el nombre de usuario de Spotify almacenado.
     * @param context Contexto de la aplicación
     */
    fun clearSpotifyUserName(context: Context) {
        getPrefs(context).edit {
            remove(KEY_SPOTIFY_USER_NAME)
        }
    }

}
