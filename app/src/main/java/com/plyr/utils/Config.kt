package com.plyr.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Config - Configuración general de la aplicación (tema, búsqueda, repetición e idioma).
 *
 * Persistencia mediante `SharedPreferences` (ver [ConfigPrefs]). Las preferencias
 * de swipe, backup y tombstones de borrado viven en objetos aparte:
 * [SwipeConfig], [BackupConfig] y [TombstoneConfig].
 */
object Config {

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

    // Claves para SharedPreferences
    private const val KEY_THEME = "theme"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_SEARCH_ENGINE_MIGRATED = "search_engine_migrated"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_LANGUAGE = "language"

    // Valores por defecto
    private const val DEFAULT_THEME = "system" // Por defecto en nuevas instalaciones seguir el tema del sistema
    private const val DEFAULT_SEARCH_ENGINE = "youtube"
    private const val DEFAULT_REPEAT_MODE = "off"
    private const val DEFAULT_LANGUAGE = "english"

    // === GESTIÓN DE TEMAS ===

    /**
     * Establece el tema de la aplicación.
     * @param context Contexto de la aplicación
     * @param theme Tema a establecer ("dark", "light")
     */
    fun setTheme(context: Context, theme: String) {
        ConfigPrefs.get(context).edit {
            putString(KEY_THEME, theme)
        }
    }

    /**
     * Obtiene el tema actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Tema actual (por defecto "system")
     */
    fun getTheme(context: Context): String {
        return ConfigPrefs.get(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    // === GESTIÓN DE MOTOR DE BÚSQUEDA ===

    /**
     * Establece el motor de búsqueda predeterminado.
     * @param context Contexto de la aplicación
     * @param searchEngine Motor de búsqueda a establecer ("youtube")
     */
    fun setSearchEngine(context: Context, searchEngine: String) {
        ConfigPrefs.get(context).edit {
            putString(KEY_SEARCH_ENGINE, searchEngine)
        }
    }

    /**
     * Obtiene el motor de búsqueda actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Motor de búsqueda actual (por defecto "youtube")
     */
    fun getSearchEngine(context: Context): String {
        val prefs = ConfigPrefs.get(context)
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
        ConfigPrefs.get(context).edit {
            putString(KEY_REPEAT_MODE, repeatMode)
        }
    }

    /**
     * Obtiene el modo de repetición actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Modo de repetición actual (por defecto "off")
     */
    fun getRepeatMode(context: Context): String {
        return ConfigPrefs.get(context).getString(KEY_REPEAT_MODE, DEFAULT_REPEAT_MODE) ?: DEFAULT_REPEAT_MODE
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
        ConfigPrefs.get(context).edit {
            putString(KEY_LANGUAGE, language)
        }
    }

    /**
     * Obtiene el idioma actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Idioma actual (por defecto "english")
     */
    fun getLanguage(context: Context): String {
        val prefs = ConfigPrefs.get(context)
        val stored = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        // Migrar valor legacy "japanese" (ASCII) a la clave usada en Translations ("日本語")
        if (stored == "japanese") {
            // Actualizar la preferencia para futuras lecturas
            setLanguage(context, LANGUAGE_JAPANESE)
            return LANGUAGE_JAPANESE
        }
        return stored
    }
}
