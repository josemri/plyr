package com.plyr.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del catálogo de traducciones.
 *
 * Verifica que todos los idiomas tengan el mismo conjunto de claves
 * (regresión típica al añadir/quitar traducciones) y que los valores
 * no estén vacíos.
 */
class TranslationCatalogTest {

    private val languages = listOf("español", "english", "català", "日本語")

    @Suppress("UNCHECKED_CAST")
    private fun getTranslationsMap(): Map<String, Map<String, String>> {
        val field = TranslationCatalog::class.java.getDeclaredField("translations")
        field.isAccessible = true
        return field.get(TranslationCatalog) as Map<String, Map<String, String>>
    }

    /**
     * Las claves esenciales de la app deben estar traducidas en los 4 idiomas.
     * Evita regresiones tipo: añadir/eliminar una clave solo en un idioma.
     */
    @Test
    fun coreKeysExistInAllLanguages() {
        val coreKeys = listOf(
            "config_title", "theme", "language",
            "lang_spanish", "lang_english", "lang_catalan", "lang_japanese",
            "exit_message"
        )
        val translations = getTranslationsMap()
        languages.forEach { language ->
            val langMap = translations[language]
            coreKeys.forEach { key ->
                assertTrue("Clave '$key' no encontrada en '$language'", langMap?.containsKey(key) == true)
                assertFalse("Valor vacío para '$key' en '$language'", langMap!!.getValue(key).isBlank())
            }
        }
    }

    /**
     * Los 4 idiomas deben exponer exactamente el mismo conjunto de claves: una
     * clave añadida solo en un idioma (o eliminada de uno) es la regresión de
     * i18n más habitual al tocar la UI.
     */
    @Test
    fun allLanguagesShareTheSameKeys() {
        val translations = getTranslationsMap()
        val reference = translations.getValue("english").keys
        languages.forEach { language ->
            assertEquals("Claves distintas en '$language'", reference, translations.getValue(language).keys)
        }
    }

    @Test
    fun noBlankValues() {
        val translations = getTranslationsMap()
        translations.forEach { (language, langMap) ->
            langMap.forEach { (key, value) ->
                assertFalse("Valor vacío para '$key' en '$language'", value.isBlank())
            }
        }
    }

    @Test
    fun sampleKeysResolveInAllLanguages() {
        val sampleKeys = listOf(
            "config_title", "theme", "language",
            "lang_spanish", "lang_english", "lang_catalan", "lang_japanese",
            "exit_message"
        )
        val translations = getTranslationsMap()
        languages.forEach { language ->
            val langMap = translations[language]
            sampleKeys.forEach { key ->
                assertTrue("Clave '$key' no encontrada en '$language'", langMap?.containsKey(key) == true)
                assertFalse("Valor vacío para '$key' en '$language'", langMap!!.getValue(key).isBlank())
            }
        }
    }

    @Test
    fun unknownKeyReturnsKeyItself() {
        assertEquals("nonexistent_key_xyz", TranslationCatalog.get("english", "nonexistent_key_xyz"))
    }

    @Test
    fun unknownLanguageReturnsKeyItself() {
        assertEquals("config_title", TranslationCatalog.get("klingon", "config_title"))
    }
}
