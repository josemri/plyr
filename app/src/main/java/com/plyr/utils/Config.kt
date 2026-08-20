package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Config - Objeto singleton para gestión de configuración de la aplicación
 * 
 * Maneja:
 * - Configuración de temas (claro/oscuro)
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
    private const val KEY_SEARCH_ENGINE_MIGRATED = "search_engine_migrated"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_LASTFM_API_KEY = "lastfm_api_key"
    private const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
    private const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"

    // Clave para configuración de shake
    private const val KEY_SHAKE_ACTION = "shake_action"

    // Clave para configuración de orientación
    private const val KEY_ORIENTATION_ACTION = "orientation_action"

    // Clave para el nickname del usuario en Feed
    private const val KEY_USER_NICKNAME = "user_nickname"

    // Clave para el modo de API keys (automatic/manual)
    private const val KEY_API_KEY_MODE = "api_key_mode"

    // Valores por defecto
    private const val DEFAULT_THEME = "system" // Por defecto en nuevas instalaciones seguir el tema del sistema
    private const val DEFAULT_SEARCH_ENGINE = "youtube"
    private const val DEFAULT_REPEAT_MODE = "off"
    private const val DEFAULT_LANGUAGE = "english"
    private const val DEFAULT_SWIPE_LEFT_ACTION = "add_to_queue"
    private const val DEFAULT_SWIPE_RIGHT_ACTION = "add_to_liked_songs"
    private const val DEFAULT_SHAKE_ACTION = "off"
    private const val DEFAULT_ORIENTATION_ACTION = "off"

    // Valor por defecto para modo de API keys
    private const val DEFAULT_API_KEY_MODE = "automatic"

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

    // === CONSTANTES PÚBLICAS DE ACCIONES DE SHAKE ===

    /** Acciones de shake disponibles */
    const val SHAKE_ACTION_OFF = "off"
    const val SHAKE_ACTION_NEXT = "next"
    const val SHAKE_ACTION_PREVIOUS = "previous"
    const val SHAKE_ACTION_PLAY_PAUSE = "play_pause"

    // === CONSTANTES PÚBLICAS DE ACCIONES DE ORIENTACIÓN ===

    /** Acciones de orientación disponibles (knob rotativo) */
    const val ORIENTATION_ACTION_OFF = "off"
    const val ORIENTATION_ACTION_VOLUME = "volume"
    const val ORIENTATION_ACTION_SKIP = "skip"

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

    // === GESTIÓN DE MOTOR DE BÚSQUEDA ===
    
    /**
     * Establece el motor de búsqueda predeterminado.
     * @param context Contexto de la aplicación
     * @param searchEngine Motor de búsqueda a establecer ("youtube")
     */
    fun setSearchEngine(context: Context, searchEngine: String) {
        getPrefs(context).edit { 
            putString(KEY_SEARCH_ENGINE, searchEngine) 
        }
    }
    
    /**
     * Obtiene el motor de búsqueda actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Motor de búsqueda actual (por defecto "youtube")
     */
    fun getSearchEngine(context: Context): String {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_SEARCH_ENGINE_MIGRATED, false)) {
            val stored = prefs.getString(KEY_SEARCH_ENGINE, null)
            val migrated = stored ?: DEFAULT_SEARCH_ENGINE
            prefs.edit {
                putString(KEY_SEARCH_ENGINE, migrated)
                putBoolean(KEY_SEARCH_ENGINE_MIGRATED, true)
            }
            return migrated
        }
        return prefs.getString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE) ?: DEFAULT_SEARCH_ENGINE
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
        val action = getPrefs(context).getString(KEY_SWIPE_LEFT_ACTION, DEFAULT_SWIPE_LEFT_ACTION) ?: DEFAULT_SWIPE_LEFT_ACTION
        // Migración: la antigua acción "download" del feature local ya no existe
        if (action == "download") {
            setSwipeLeftAction(context, DEFAULT_SWIPE_LEFT_ACTION)
            return DEFAULT_SWIPE_LEFT_ACTION
        }
        return action
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
        val action = getPrefs(context).getString(KEY_SWIPE_RIGHT_ACTION, DEFAULT_SWIPE_RIGHT_ACTION) ?: DEFAULT_SWIPE_RIGHT_ACTION
        // Migración: la antigua acción "download" del feature local ya no existe
        if (action == "download") {
            setSwipeRightAction(context, DEFAULT_SWIPE_RIGHT_ACTION)
            return DEFAULT_SWIPE_RIGHT_ACTION
        }
        return action
    }

    // === GESTIÓN DE ACCIÓN DE SHAKE ===

    /**
     * Establece la acción para el gesto de shake.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer ("off", "next", "previous", "play_pause")
     */
    fun setShakeAction(context: Context, action: String) {
        getPrefs(context).edit {
            putString(KEY_SHAKE_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el gesto de shake.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "off")
     */
    fun getShakeAction(context: Context): String {
        val action = getPrefs(context).getString(KEY_SHAKE_ACTION, DEFAULT_SHAKE_ACTION) ?: DEFAULT_SHAKE_ACTION
        // Migración: la antigua acción "assistant" del asistente de voz ya no existe
        if (action == "assistant") {
            setShakeAction(context, DEFAULT_SHAKE_ACTION)
            return DEFAULT_SHAKE_ACTION
        }
        return action
    }

    // === GESTIÓN DE ACCIÓN DE ORIENTACIÓN ===

    /**
     * Establece la acción para el sensor de orientación.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer ("off", "volume_up", "volume_down", "next", "previous")
     */
    fun setOrientationAction(context: Context, action: String) {
        getPrefs(context).edit {
            putString(KEY_ORIENTATION_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el sensor de orientación.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "off")
     */
    fun getOrientationAction(context: Context): String {
        return getPrefs(context).getString(KEY_ORIENTATION_ACTION, DEFAULT_ORIENTATION_ACTION) ?: DEFAULT_ORIENTATION_ACTION
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

    // === GESTIÓN DE NICKNAME DEL USUARIO PARA FEED ===

    /**
     * Obtiene el nickname del usuario para el sistema de recomendaciones.
     * @param context Contexto de la aplicación
     * @return Nickname del usuario o null si no está configurado
     */
    fun getUserNickname(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_NICKNAME, null)
    }

    /**
     * Establece el nickname del usuario para el sistema de recomendaciones.
     * @param context Contexto de la aplicación
     * @param nickname Nickname del usuario
     */
    fun setUserNickname(context: Context, nickname: String) {
        getPrefs(context).edit {
            putString(KEY_USER_NICKNAME, nickname.trim())
        }
    }

    /**
     * Verifica si el usuario tiene un nickname configurado.
     * @param context Contexto de la aplicación
     * @return true si tiene nickname configurado, false en caso contrario
     */
    fun hasUserNickname(context: Context): Boolean {
        val nickname = getPrefs(context).getString(KEY_USER_NICKNAME, null)
        return !nickname.isNullOrBlank()
    }

    // === GESTIÓN DE MODO DE API KEYS ===

    /**
     * Obtiene el modo actual de API keys (automatic/manual).
     * @param context Contexto de la aplicación
     * @return Modo actual (por defecto "manual")
     */
    fun getApiKeyMode(context: Context): String {
        return getPrefs(context).getString(KEY_API_KEY_MODE, DEFAULT_API_KEY_MODE) ?: DEFAULT_API_KEY_MODE
    }

    /**
     * Establece el modo de API keys.
     * @param context Contexto de la aplicación
     * @param mode Modo a establecer ("automatic" o "manual")
     */
    fun setApiKeyMode(context: Context, mode: String) {
        getPrefs(context).edit {
            putString(KEY_API_KEY_MODE, mode)
        }
    }

    /**
     * Limpia todas las API keys almacenadas.
     * @param context Contexto de la aplicación
     */
    fun clearAllApiKeys(context: Context) {
        getPrefs(context).edit {
            remove(KEY_LASTFM_API_KEY)
        }
    }

 }
