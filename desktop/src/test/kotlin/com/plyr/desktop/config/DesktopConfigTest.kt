package com.plyr.desktop.config

import com.plyr.domain.RepeatMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Lógica pura de [DesktopConfig]: ciclos de repetición/tema y resolución de oscuro. */
class DesktopConfigTest {

    private var savedTheme = ThemeMode.SYSTEM

    @Before
    fun setUp() {
        savedTheme = DesktopConfig.theme
    }

    @After
    fun tearDown() {
        DesktopConfig.theme = savedTheme
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
}
