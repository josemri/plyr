package com.plyr.desktop.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plyr.domain.RepeatMode
import com.russhwolf.settings.Settings

/**
 * Modos de tema soportados por el escritorio. Los ids coinciden con los de
 * Android (`Config` en `:app`) para que el backend comparta claves y valores.
 */
enum class ThemeMode(val id: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system"),
    AUTO("auto"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Idiomas soportados. El id es la clave que usa `TranslationCatalog` en `:core`
 * (`"español"`, `"english"`, `"català"`, `"日本語"`).
 */
enum class Language(val id: String) {
    SPANISH("español"),
    ENGLISH("english"),
    CATALAN("català"),
    JAPANESE("日本語"),
    ;

    companion object {
        fun fromId(id: String?): Language = entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}

/**
 * Configuración de escritorio, persistente entre reinicios.
 *
 * Mantiene el mismo contrato que el `Config` de Android pero con un backend
 * multiplataforma ([Settings] sobre `java.util.prefs`): mismas claves
 * (`theme`, `language`, `repeat_mode`, `search_engine`) y las mismas migraciones
 * (el buscador se inicializa una sola vez y el idioma legacy `"japanese"` pasa a
 * `"日本語"`). Los valores viven en `mutableStateOf` para que la UI de Compose
 * reaccione al cambiarlos y se escriben al almacén en el mismo setter.
 *
 * En tests se puede sustituir el almacén con [installSettings] para no tocar las
 * preferencias reales del usuario.
 */
object DesktopConfig {

    // Claves compartidas con `com.plyr.utils.Config` de Android.
    private const val KEY_THEME = "theme"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_SEARCH_ENGINE_MIGRATED = "search_engine_migrated"

    // Valores por defecto (idénticos a Android).
    private const val DEFAULT_THEME = "system"
    private const val DEFAULT_REPEAT_MODE = RepeatMode.OFF
    private const val DEFAULT_SEARCH_ENGINE = "youtube"
    private const val LEGACY_LANGUAGE_JAPANESE = "japanese"

    private var settings: Settings = DesktopSettings.create()

    private var themeState by mutableStateOf(ThemeMode.fromId(settings.getStringOrNull(KEY_THEME)))
    private var languageState by mutableStateOf(readLanguage(settings))
    private var repeatModeState by mutableStateOf(
        settings.getStringOrNull(KEY_REPEAT_MODE) ?: DEFAULT_REPEAT_MODE
    )
    private var searchEngineState by mutableStateOf(readSearchEngine(settings))

    /** Tema actual, persistido bajo la clave `theme`. */
    var theme: ThemeMode
        get() = themeState
        set(value) {
            themeState = value
            settings.putString(KEY_THEME, value.id)
        }

    /** Idioma actual, persistido bajo la clave `language`. */
    var language: Language
        get() = languageState
        set(value) {
            languageState = value
            settings.putString(KEY_LANGUAGE, value.id)
        }

    /** Modo de repetición (`off`/`one`/`all`), persistido bajo `repeat_mode`. */
    var repeatMode: String
        get() = repeatModeState
        set(value) {
            repeatModeState = value
            settings.putString(KEY_REPEAT_MODE, value)
        }

    /** Buscador por defecto, persistido bajo `search_engine`. */
    var searchEngine: String
        get() = searchEngineState
        set(value) {
            searchEngineState = value
            settings.putString(KEY_SEARCH_ENGINE, value)
        }

    // === TESTING ===

    /**
     * Cambia el almacén de preferencias (p. ej. `MapSettings` en tests) y relee
     * todos los valores, aplicando de nuevo las migraciones.
     */
    internal fun installSettings(newSettings: Settings) {
        settings = newSettings
        themeState = ThemeMode.fromId(settings.getStringOrNull(KEY_THEME))
        languageState = readLanguage(settings)
        repeatModeState = settings.getStringOrNull(KEY_REPEAT_MODE) ?: DEFAULT_REPEAT_MODE
        searchEngineState = readSearchEngine(settings)
    }

    // === TEMAS ===

    /** Siguiente tema en el ciclo (para un control que cicla). */
    fun nextTheme(current: ThemeMode): ThemeMode {
        val values = ThemeMode.entries
        return values[(values.indexOf(current) + 1) % values.size]
    }

    /**
     * `true` si toca pintar en oscuro. `AUTO` y `SYSTEM` siguen el tema del
     * sistema; en escritorio no hay sensor de luz, así que `AUTO` == `SYSTEM`.
     */
    fun isDark(systemDark: Boolean): Boolean = when (theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM, ThemeMode.AUTO -> systemDark
    }

    // === REPETICIÓN ===

    /** Siguiente modo en el ciclo off -> one -> all -> off. */
    fun nextRepeatMode(current: String): String = when (current) {
        RepeatMode.OFF -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    // === MIGRACIONES ===

    /** Idioma almacenado, migrando el valor legacy `"japanese"` una sola vez. */
    private fun readLanguage(settings: Settings): Language {
        val raw = settings.getStringOrNull(KEY_LANGUAGE)
        if (raw == LEGACY_LANGUAGE_JAPANESE) {
            settings.putString(KEY_LANGUAGE, Language.JAPANESE.id)
            return Language.JAPANESE
        }
        return Language.fromId(raw)
    }

    /**
     * Buscador almacenado. La primera vez fija el valor por defecto y marca la
     * migración, igual que `Config.getSearchEngine` en Android.
     */
    private fun readSearchEngine(settings: Settings): String {
        if (!settings.getBoolean(KEY_SEARCH_ENGINE_MIGRATED, false)) {
            val migrated = settings.getStringOrNull(KEY_SEARCH_ENGINE) ?: DEFAULT_SEARCH_ENGINE
            settings.putString(KEY_SEARCH_ENGINE, migrated)
            settings.putBoolean(KEY_SEARCH_ENGINE_MIGRATED, true)
            return migrated
        }
        return settings.getStringOrNull(KEY_SEARCH_ENGINE) ?: DEFAULT_SEARCH_ENGINE
    }
}
