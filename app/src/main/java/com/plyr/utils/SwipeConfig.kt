package com.plyr.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Preferencias de las acciones de swipe en las listas de pistas.
 *
 * Persistidas en el fichero compartido de [ConfigPrefs].
 */
object SwipeConfig {

    private const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
    private const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"

    private const val DEFAULT_SWIPE_LEFT_ACTION = "add_to_queue"
    private const val DEFAULT_SWIPE_RIGHT_ACTION = "add_to_liked_songs"

    /**
     * Establece la acción para el swipe izquierdo.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer
     */
    fun setSwipeLeftAction(context: Context, action: String) {
        ConfigPrefs.get(context).edit {
            putString(KEY_SWIPE_LEFT_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el swipe izquierdo.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "add_to_queue")
     */
    fun getSwipeLeftAction(context: Context): String {
        val action = ConfigPrefs.get(context).getString(KEY_SWIPE_LEFT_ACTION, DEFAULT_SWIPE_LEFT_ACTION)
            ?: DEFAULT_SWIPE_LEFT_ACTION
        // Migración: la antigua acción "download" del feature local ya no existe
        if (action == "download") {
            setSwipeLeftAction(context, DEFAULT_SWIPE_LEFT_ACTION)
            return DEFAULT_SWIPE_LEFT_ACTION
        }
        return action
    }

    /**
     * Establece la acción para el swipe derecho.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer
     */
    fun setSwipeRightAction(context: Context, action: String) {
        ConfigPrefs.get(context).edit {
            putString(KEY_SWIPE_RIGHT_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el swipe derecho.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "add_to_liked_songs")
     */
    fun getSwipeRightAction(context: Context): String {
        val action = ConfigPrefs.get(context).getString(KEY_SWIPE_RIGHT_ACTION, DEFAULT_SWIPE_RIGHT_ACTION)
            ?: DEFAULT_SWIPE_RIGHT_ACTION
        // Migración: la antigua acción "download" del feature local ya no existe
        if (action == "download") {
            setSwipeRightAction(context, DEFAULT_SWIPE_RIGHT_ACTION)
            return DEFAULT_SWIPE_RIGHT_ACTION
        }
        return action
    }
}
