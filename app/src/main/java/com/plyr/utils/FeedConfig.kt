package com.plyr.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Preferencias del usuario para el sistema de recomendaciones (Feed).
 *
 * Persistidas en el fichero compartido de [ConfigPrefs].
 */
object FeedConfig {

    private const val KEY_USER_NICKNAME = "user_nickname"

    /**
     * Obtiene el nickname del usuario para el sistema de recomendaciones.
     * @param context Contexto de la aplicación
     * @return Nickname del usuario o null si no está configurado
     */
    fun getUserNickname(context: Context): String? {
        return ConfigPrefs.get(context).getString(KEY_USER_NICKNAME, null)
    }

    /**
     * Establece el nickname del usuario para el sistema de recomendaciones.
     * @param context Contexto de la aplicación
     * @param nickname Nickname del usuario
     */
    fun setUserNickname(context: Context, nickname: String) {
        ConfigPrefs.get(context).edit {
            putString(KEY_USER_NICKNAME, nickname.trim())
        }
    }
}
