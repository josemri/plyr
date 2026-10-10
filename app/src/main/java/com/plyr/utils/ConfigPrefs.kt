package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Acceso compartido al fichero de `SharedPreferences` de configuración.
 *
 * Los distintos objetos de configuración ([Config], [SwipeConfig], [FeedConfig],
 * [BackupConfig], [TombstoneConfig]) comparten el mismo fichero, así que el
 * nombre y la apertura viven aquí una sola vez.
 */
internal object ConfigPrefs {

    private const val PREFS_NAME = "plyr_config"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
