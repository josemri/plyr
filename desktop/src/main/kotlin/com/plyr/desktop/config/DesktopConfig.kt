package com.plyr.desktop.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plyr.domain.RepeatMode

/**
 * Modos de tema soportados por el escritorio. Los ids coinciden con los de
 * Android (`Config` en `:app`) para que M9 pueda unificar el backend sin
 * migración de valores.
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
}

/**
 * Configuración de escritorio con **backend temporal**.
 *
 * Vive en memoria y usa estado de Compose para que la UI reaccione al cambiar
 * tema o idioma. Es intencionadamente simple: el backend real (persistente y
 * compartido con Android) llega en **M9** con `multiplatform-settings`. Hasta
 * entonces basta para el esqueleto de M5.
 */
object DesktopConfig {

    var theme by mutableStateOf(ThemeMode.SYSTEM)
    var language by mutableStateOf(Language.ENGLISH)
    var repeatMode by mutableStateOf(RepeatMode.OFF)
    var searchEngine by mutableStateOf("youtube")

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
}
