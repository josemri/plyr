package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object Config {
    private const val PREFS_NAME = "plyr_config"
    private const val KEY_API_URL = "api_url"
    private const val KEY_API_TOKEN = "api_key"
    private const val KEY_THEME = "theme"
    
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
}
