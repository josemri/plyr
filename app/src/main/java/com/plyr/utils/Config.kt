package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object Config {
    private const val PREFS_NAME = "plyr_config"
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_TOKEN = "api_key"
    private const val KEY_THEME = "theme"
    private const val KEY_SPOTIFY_ACCESS_TOKEN = "spotify_access_token"
    private const val KEY_SPOTIFY_REFRESH_TOKEN = "spotify_refresh_token"
    private const val KEY_SPOTIFY_TOKEN_EXPIRY = "spotify_token_expiry"
    
    // Spotify API configuration
    const val SPOTIFY_CLIENT_ID = "fa1672edc95445519e1d57db29d2b6e2"
    const val SPOTIFY_CLIENT_SECRET = "c059755ebd844251bc7273d0daadbb8b"
    const val SPOTIFY_REDIRECT_URI = "plyr://spotify/callback"
    const val SPOTIFY_SCOPES = "playlist-read-private playlist-read-collaborative"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun setNgrokUrl(context: Context, url: String) {
        getPrefs(context).edit { putString(KEY_API_URL, url) }
    }
    
    fun getNgrokUrl(context: Context): String {
        return getPrefs(context).getString(KEY_API_URL, "https://your-ngrok-url.ngrok.io") ?: "https://your-ngrok-url.ngrok.io"
    }
    
    fun setApiToken(context: Context, token: String) {
        getPrefs(context).edit { putString(KEY_API_TOKEN, token) }
    }
    
    fun getApiToken(context: Context): String {
        return getPrefs(context).getString(KEY_API_TOKEN, "your-api-token-here") ?: "your-api-token-here"
    }
    
    fun setTheme(context: Context, theme: String) {
        getPrefs(context).edit { putString(KEY_THEME, theme) }
    }
    
    fun getTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, "dark") ?: "dark"
    }
    
    // Spotify token management
    fun setSpotifyTokens(context: Context, accessToken: String, refreshToken: String?, expiresIn: Int) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000L)
        getPrefs(context).edit { 
            putString(KEY_SPOTIFY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(KEY_SPOTIFY_REFRESH_TOKEN, it) }
            putLong(KEY_SPOTIFY_TOKEN_EXPIRY, expiryTime)
        }
    }
    
    fun getSpotifyAccessToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_SPOTIFY_ACCESS_TOKEN, null)
        val expiryTime = getPrefs(context).getLong(KEY_SPOTIFY_TOKEN_EXPIRY, 0)
        
        // Check if token is expired
        if (token != null && System.currentTimeMillis() < expiryTime) {
            return token
        }
        return null
    }
    
    fun getSpotifyRefreshToken(context: Context): String? {
        return getPrefs(context).getString(KEY_SPOTIFY_REFRESH_TOKEN, null)
    }
    
    fun clearSpotifyTokens(context: Context) {
        getPrefs(context).edit { 
            remove(KEY_SPOTIFY_ACCESS_TOKEN)
            remove(KEY_SPOTIFY_REFRESH_TOKEN)
            remove(KEY_SPOTIFY_TOKEN_EXPIRY)
        }
    }
    
    fun isSpotifyConnected(context: Context): Boolean {
        return getSpotifyAccessToken(context) != null || getSpotifyRefreshToken(context) != null
    }
}
