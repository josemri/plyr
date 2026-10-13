package com.plyr.desktop.config

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

/**
 * Construye el almacén persistente de preferencias del escritorio.
 *
 * Usa `multiplatform-settings` sobre `java.util.prefs`, de modo que los ajustes
 * sobreviven a los reinicios en Windows, Linux y macOS sin depender de Android.
 * El nodo usa el mismo identificador que el paquete para que las preferencias se
 * puedan inspeccionar (en Linux, bajo `~/.java/.userPrefs`).
 *
 * La clave del contenido (tema, idioma, repetición, buscador) es la **misma** que
 * usa `com.plyr.utils.Config` en Android, para que las migraciones sean idénticas.
 */
object DesktopSettings {

    private const val NODE = "com/plyr/desktop"

    fun create(): Settings = PreferencesSettings(Preferences.userRoot().node(NODE))
}
