package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * §7.1: dos invariantes que `TranslationsTest` no cubría porque solo miraba los
 * mapas, no el código que los consume.
 *
 * 1. Toda clave pasada a `Translations.get(...)` debe existir en los 4 idiomas.
 *    Antes, `QueueScreen` pasaba el texto inglés `"Player not available"` como
 *    clave: `get` devuelve la propia clave cuando no la encuentra, así que el
 *    texto salía sin traducir y sin ningún aviso.
 * 2. Ninguna clave puede quedarse sin usar: 16 claves estaban muertas en los 4
 *    idiomas (restos de Last.fm, gestos y de la pantalla "home" anterior).
 */
class TranslationKeysUsageTest {

    companion object {
        private val languages = listOf("español", "english", "català", "日本語")

        /** Claves que se resuelven en tiempo de ejecución, no con un literal. */
        private val dynamicKeys = setOf(
            "sync_working" // ConfigScreen.DataActionRow(workingKey = "sync_working")
        )

        private val GET_CALL = Regex("""Translations\.get\(\s*[^,]+,\s*"([^"]+)"\s*\)""")
    }

    @Suppress("UNCHECKED_CAST")
    private fun translations(): Map<String, Map<String, String>> {
        val field = Translations::class.java.getDeclaredField("translations")
        field.isAccessible = true
        return field.get(Translations) as Map<String, Map<String, String>>
    }

    /** Fuentes de `main`, de donde se extraen las claves realmente usadas. */
    private fun sourceFiles(): List<File> {
        val userDir = File("").absoluteFile
        val ancestors = generateSequence(userDir) { it.parentFile ?: return@generateSequence null }
        val dir = ancestors.firstOrNull { File(it, "src/main/java").isDirectory }
            ?: error("No se encuentra src/main/java desde $userDir")
        return File(dir, "src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Translations.kt" }
            .toList()
    }

    private fun referencedKeys(): Set<String> = sourceFiles()
        .flatMap { GET_CALL.findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }
        .toSet()

    @Test
    fun everyReferencedKeyExistsInAllLanguages() {
        val referenced = referencedKeys()
        assertTrue("No se ha encontrado ninguna llamada a Translations.get", referenced.isNotEmpty())
        val maps = translations()
        val missing = referenced.filter { key -> languages.any { maps[it]?.containsKey(key) != true } }
        assertEquals(
            "Claves usadas en el código pero ausentes en algún idioma: $missing",
            emptyList<String>(),
            missing.toList()
        )
    }

    @Test
    fun noUnusedKeys() {
        val referenced = referencedKeys() + dynamicKeys
        val unused = translations().getValue(languages.first()).keys - referenced
        assertEquals(
            "Claves sin usar en los 4 idiomas (bórralas o úsalas): $unused",
            emptySet<String>(),
            unused
        )
    }

    @Test
    fun referencedKeysResolveToTheirOwnTranslation() {
        val maps = translations()
        val key = "player_not_available"
        assertEquals(
            "El mensaje de cola debe salir traducido, no con la clave",
            "reproductor no disponible",
            maps.getValue("español")[key]
        )
        assertEquals("No tracks loaded", maps.getValue("english")["no_tracks_loaded"])
    }

    @Test
    fun noKeyIsEnglishTextUsedAsAKey() {
        val suspicious = translations().getValue("español").keys.filter {
            it.contains(' ') || it.any { c -> c.isUpperCase() || c == '.' }
        }
        assertEquals(
            "Claves que en realidad son textos: $suspicious",
            emptySet<String>(),
            suspicious.toSet()
        )
    }
}
