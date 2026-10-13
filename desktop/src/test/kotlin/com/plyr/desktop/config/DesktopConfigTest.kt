package com.plyr.desktop.config

import com.plyr.domain.RepeatMode
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lógica de [DesktopConfig]: ciclos de repetición/tema, resolución de oscuro,
 * persistencia y migraciones de claves heredadas de Android.
 *
 * Cada test instala su propio almacén en memoria para no tocar las preferencias
 * reales del usuario.
 */
class DesktopConfigTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
    }

    @Test
    fun nextRepeatMode_ciclaOffOneAll() {
        assertEquals(RepeatMode.ONE, DesktopConfig.nextRepeatMode(RepeatMode.OFF))
        assertEquals(RepeatMode.ALL, DesktopConfig.nextRepeatMode(RepeatMode.ONE))
        assertEquals(RepeatMode.OFF, DesktopConfig.nextRepeatMode(RepeatMode.ALL))
    }

    @Test
    fun isDark_respetaElTema() {
        DesktopConfig.theme = ThemeMode.DARK
        assertTrue(DesktopConfig.isDark(systemDark = false))

        DesktopConfig.theme = ThemeMode.LIGHT
        assertFalse(DesktopConfig.isDark(systemDark = true))

        DesktopConfig.theme = ThemeMode.SYSTEM
        assertTrue(DesktopConfig.isDark(systemDark = true))
        assertFalse(DesktopConfig.isDark(systemDark = false))

        // En escritorio AUTO sigue el sistema (no hay sensor de luz).
        DesktopConfig.theme = ThemeMode.AUTO
        assertTrue(DesktopConfig.isDark(systemDark = true))
        assertFalse(DesktopConfig.isDark(systemDark = false))
    }

    @Test
    fun nextTheme_ciclaTodasLasOpciones() {
        assertEquals(ThemeMode.LIGHT, DesktopConfig.nextTheme(ThemeMode.DARK))
        assertEquals(ThemeMode.SYSTEM, DesktopConfig.nextTheme(ThemeMode.LIGHT))
        assertEquals(ThemeMode.AUTO, DesktopConfig.nextTheme(ThemeMode.SYSTEM))
        assertEquals(ThemeMode.DARK, DesktopConfig.nextTheme(ThemeMode.AUTO))
    }

    @Test
    fun themeMode_fromIdConFallback() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("desconocido"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(null))
    }

    @Test
    fun valoresPorDefecto() {
        assertEquals(ThemeMode.SYSTEM, DesktopConfig.theme)
        assertEquals(Language.ENGLISH, DesktopConfig.language)
        assertEquals(RepeatMode.OFF, DesktopConfig.repeatMode)
        assertEquals("youtube", DesktopConfig.searchEngine)
    }

    @Test
    fun persisteYRecargaComoTrasReiniciar() {
        val store = MapSettings()
        DesktopConfig.installSettings(store)

        DesktopConfig.theme = ThemeMode.LIGHT
        DesktopConfig.language = Language.JAPANESE
        DesktopConfig.repeatMode = RepeatMode.ALL
        DesktopConfig.searchEngine = "youtube"

        // "Reiniciar": se relee el mismo almacén desde cero.
        DesktopConfig.installSettings(store)

        assertEquals(ThemeMode.LIGHT, DesktopConfig.theme)
        assertEquals(Language.JAPANESE, DesktopConfig.language)
        assertEquals(RepeatMode.ALL, DesktopConfig.repeatMode)
        assertEquals("youtube", DesktopConfig.searchEngine)
    }

    @Test
    fun migraElIdiomaLegacyJapanese() {
        val store = MapSettings()
        store.putString("language", "japanese")

        DesktopConfig.installSettings(store)

        assertEquals(Language.JAPANESE, DesktopConfig.language)
        assertEquals(Language.JAPANESE.id, store.getStringOrNull("language"))
    }

    @Test
    fun inicializaElBuscadorYMarcaLoComoMigrado() {
        val store = MapSettings()

        DesktopConfig.installSettings(store)

        assertEquals("youtube", DesktopConfig.searchEngine)
        assertTrue(store.getBoolean("search_engine_migrated", false))
        assertEquals("youtube", store.getStringOrNull("search_engine"))
    }

    @Test
    fun respetaElBuscadorYaMigrado() {
        val store = MapSettings()
        store.putString("search_engine", "youtube")
        store.putBoolean("search_engine_migrated", true)

        DesktopConfig.installSettings(store)

        assertEquals("youtube", DesktopConfig.searchEngine)
    }

    @Test
    fun language_fromIdConFallback() {
        assertEquals(Language.SPANISH, Language.fromId("español"))
        assertEquals(Language.CATALAN, Language.fromId("català"))
        assertEquals(Language.ENGLISH, Language.fromId("desconocido"))
        assertEquals(Language.ENGLISH, Language.fromId(null))
    }
}
