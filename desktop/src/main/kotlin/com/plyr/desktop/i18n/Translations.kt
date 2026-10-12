package com.plyr.desktop.i18n

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.i18n.TranslationCatalog

/**
 * Acceso a traducciones para escritorio.
 *
 * Espejo de `com.plyr.utils.Translations` en Android: los mapas puros viven en
 * `:core` (`[TranslationCatalog]`) y aquí solo se resuelve el idioma actual,
 * que en escritorio sale de [DesktopConfig]. Si el idioma no tiene traducción
 * para una clave, `TranslationCatalog.get` devuelve la propia clave.
 */
object Translations {

    /** Traducción para [key] según el idioma configurado. */
    fun get(key: String): String = TranslationCatalog.get(DesktopConfig.language.id, key)

    /** Traducción para [key] en un idioma concreto. */
    fun get(language: Language, key: String): String = TranslationCatalog.get(language.id, key)
}
