package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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
    private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_SPOTIFY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_SPOTIFY_TOKEN_EXPIRY = "spotify_token_expiry"
    private const val KEY_SPOTIFY_CLIENT_ID = "spotify_client_id"
    private const val KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret"
    
    // Valores por defecto
    private const val DEFAULT_THEME = "dark"
    private const val DEFAULT_SEARCH_ENGINE = "spotify"

    // === CONSTANTES PÚBLICAS DE SPOTIFY ===

    /** URI de redirección para OAuth de Spotify */
    const val SPOTIFY_REDIRECT_URI = "plyr://spotify/callback"
    
    /** Permisos solicitados a Spotify */
    const val SPOTIFY_SCOPES = "playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private user-library-modify user-library-read"
    
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
     * Verifica automáticamente si el token ha expirado.
     * 
     * @param context Contexto de la aplicación
     * @return Token de acceso válido o null si expiró o no existe
     */
    fun getSpotifyAccessToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
        val expiryTime = getPrefs(context).getLong(KEY_SPOTIFY_TOKEN_EXPIRY, 0)
        
        // Verificar que el token no haya expirado
        return if (token != null && System.currentTimeMillis() < expiryTime) {
            token
        } else {
            null
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
     * Establece las credenciales de Spotify API del usuario.
     * @param context Contexto de la aplicación
     * @param clientId Client ID del usuario
     * @param clientSecret Client Secret del usuario
     */
    fun setSpotifyCredentials(context: Context, clientId: String, clientSecret: String) {
        getPrefs(context).edit {
            putString(KEY_SPOTIFY_CLIENT_ID, clientId.trim())
            putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret.trim())
        }
    }
    
    /**
     * Limpia las credenciales de Spotify del usuario.
     * @param context Contexto de la aplicación
     */
    fun clearSpotifyCredentials(context: Context) {
        getPrefs(context).edit {
            remove(KEY_SPOTIFY_CLIENT_ID)
            remove(KEY_SPOTIFY_CLIENT_SECRET)
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
    
    /**
     * Verifica si Spotify está completamente configurado (credenciales + conexión).
     * @param context Contexto de la aplicación
     * @return true si está completamente configurado, false en caso contrario
     */
    fun isSpotifyFullyConfigured(context: Context): Boolean {
        return hasSpotifyCredentials(context) && isSpotifyConnected(context)
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
}
