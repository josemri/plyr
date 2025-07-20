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
    private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_SPOTIFY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_SPOTIFY_TOKEN_EXPIRY = "spotify_token_expiry"
    
    // Valores por defecto
    private const val DEFAULT_THEME = "dark"
    
    // === CONSTANTES PÚBLICAS DE SPOTIFY ===
    
    /** ID de cliente de Spotify (configurado en Spotify Developer Dashboard) */
    const val SPOTIFY_CLIENT_ID = "fa1672edc95445519e1d57db29d2b6e2"
    
    /** Secreto de cliente de Spotify */
    const val SPOTIFY_CLIENT_SECRET = "c059755ebd844251bc7273d0daadbb8b"
    
    /** URI de redirección para OAuth de Spotify */
    const val SPOTIFY_REDIRECT_URI = "plyr://spotify/callback"
    
    /** Permisos solicitados a Spotify */
    const val SPOTIFY_SCOPES = "playlist-read-private playlist-read-collaborative"
    
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
}
