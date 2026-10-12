package com.plyr.utils

import android.content.Context
import com.plyr.i18n.TranslationCatalog

/**
 * Acceso a traducciones para Android.
 *
 * Los mapas puros viven en `:core` (`[TranslationCatalog]`) para poder
 * compartirse con el escritorio; aquí solo queda la resolución del idioma
 * actual a partir de las preferencias ([Config]), que depende de `Context`.
 *
 * Se mantiene el nombre y las firmas originales para no tocar las ~100 llamadas
 * `Translations.get(context, key)` de las pantallas.
 */
object Translations {

    /** Traducción para la clave según el idioma configurado en las preferencias. */
    fun get(context: Context, key: String): String =
        TranslationCatalog.get(Config.getLanguage(context), key)

    /** Traducción para la clave en un idioma concreto. */
    fun get(language: String, key: String): String =
        TranslationCatalog.get(language, key)
}
