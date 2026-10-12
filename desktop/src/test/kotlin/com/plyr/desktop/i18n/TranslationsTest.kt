package com.plyr.desktop.i18n

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Comprueba el envoltorio de traducciones del escritorio sobre `TranslationCatalog`. */
class TranslationsTest {

    private var savedLanguage = Language.ENGLISH

    @Before
    fun setUp() {
        savedLanguage = DesktopConfig.language
    }

    @After
    fun tearDown() {
        DesktopConfig.language = savedLanguage
    }

    @Test
    fun devuelveLaClaveSiNoExiste() {
        assertEquals("clave_inexistente", Translations.get(Language.ENGLISH, "clave_inexistente"))
    }

    @Test
    fun traduceSegunElIdiomaIndicado() {
        assertEquals("plyr_search", Translations.get(Language.ENGLISH, "search_title"))
        assertEquals("plyr_buscar", Translations.get(Language.SPANISH, "search_title"))
        assertEquals("plyr_cercar", Translations.get(Language.CATALAN, "search_title"))
    }

    @Test
    fun getUsaElIdiomaConfigurado() {
        DesktopConfig.language = Language.SPANISH
        assertEquals("plyr_buscar", Translations.get("search_title"))

        DesktopConfig.language = Language.ENGLISH
        assertEquals("plyr_search", Translations.get("search_title"))
    }
}
