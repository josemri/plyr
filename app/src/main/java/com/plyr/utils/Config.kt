package com.plyr.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Config - Objeto singleton para gestión de configuración de la aplicación
 * 
 * Maneja:
 * - Configuración de temas (claro/oscuro)
 * - Persistencia de preferencias usando SharedPreferences
 * 
 * Todos los datos se almacenan de forma segura en SharedPreferences
 * y se accede a través de métodos thread-safe.
 */
object Config {
    
    // === CONSTANTES PRIVADAS ===
    
    /** Nombre del archivo de preferencias */
    private const val PREFS_NAME = "plyr_config"
    
    // Claves para SharedPreferences
    private const val KEY_THEME = "theme"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_SEARCH_ENGINE_MIGRATED = "search_engine_migrated"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"
    private const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"

    // Copia de seguridad automática: carpeta SAF elegida, documento interno
    // cacheado y huella del último contenido escrito (ver `DataSync`).
    private const val KEY_BACKUP_TREE_URI = "backup_tree_uri"
    private const val KEY_BACKUP_DOC_ID = "backup_doc_id"
    private const val KEY_BACKUP_HASH = "backup_hash"
    private const val KEY_AUTO_SYNC = "auto_sync"

    // Clave para el nickname del usuario en Feed
    private const val KEY_USER_NICKNAME = "user_nickname"

    // Valores por defecto
    private const val DEFAULT_THEME = "system" // Por defecto en nuevas instalaciones seguir el tema del sistema
    private const val DEFAULT_SEARCH_ENGINE = "youtube"
    private const val DEFAULT_REPEAT_MODE = "off"
    private const val DEFAULT_LANGUAGE = "english"
    private const val DEFAULT_SWIPE_LEFT_ACTION = "add_to_queue"
    private const val DEFAULT_SWIPE_RIGHT_ACTION = "add_to_liked_songs"

    // === CONSTANTES PÚBLICAS DE MODO DE REPETICIÓN ===

    /** Modos de repetición disponibles */
    const val REPEAT_MODE_OFF = "off"        // Sin repetición
    const val REPEAT_MODE_ONE = "one"        // Repetir una sola vez
    const val REPEAT_MODE_ALL = "all"        // Repetir indefinidamente

    // === CONSTANTES PÚBLICAS DE IDIOMAS ===

    /** Idiomas disponibles */
    const val LANGUAGE_SPANISH = "español"
    const val LANGUAGE_ENGLISH = "english"
    const val LANGUAGE_CATALAN = "català"
    // Ajuste: usar la misma clave que en Translations ("日本語") para que coincida la búsqueda
    const val LANGUAGE_JAPANESE = "日本語"

    // === CONSTANTES PÚBLICAS DE ACCIONES DE SWIPE ===

    /** Acciones de swipe disponibles */
    const val SWIPE_ACTION_ADD_TO_QUEUE = "add_to_queue"
    const val SWIPE_ACTION_ADD_TO_LIKED = "add_to_liked_songs"
    const val SWIPE_ACTION_ADD_TO_PLAYLIST = "add_to_playlist"
    const val SWIPE_ACTION_SHARE = "share"

    // === MÉTODOS PRIVADOS ===
    
    /**
     * Obtiene la instancia de SharedPreferences para la aplicación.
     * @param context Contexto de la aplicación
     * @return SharedPreferences configurado con el nombre correcto
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // === GESTIÓN DE TEMAS ===
    
    /**
     * Establece el tema de la aplicación.
     * @param context Contexto de la aplicación
     * @param theme Tema a establecer ("dark", "light")
     */
    fun setTheme(context: Context, theme: String) {
        getPrefs(context).edit { 
            putString(KEY_THEME, theme) 
        }
    }
    
    /**
     * Obtiene el tema actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Tema actual (por defecto "system")
     */
    fun getTheme(context: Context): String {
        return getPrefs(context).getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    // === GESTIÓN DE MOTOR DE BÚSQUEDA ===
    
    /**
     * Establece el motor de búsqueda predeterminado.
     * @param context Contexto de la aplicación
     * @param searchEngine Motor de búsqueda a establecer ("youtube")
     */
    fun setSearchEngine(context: Context, searchEngine: String) {
        getPrefs(context).edit { 
            putString(KEY_SEARCH_ENGINE, searchEngine) 
        }
    }
    
    /**
     * Obtiene el motor de búsqueda actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Motor de búsqueda actual (por defecto "youtube")
     */
    fun getSearchEngine(context: Context): String {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_SEARCH_ENGINE_MIGRATED, false)) {
            val stored = prefs.getString(KEY_SEARCH_ENGINE, null)
            val migrated = stored ?: DEFAULT_SEARCH_ENGINE
            prefs.edit {
                putString(KEY_SEARCH_ENGINE, migrated)
                putBoolean(KEY_SEARCH_ENGINE_MIGRATED, true)
            }
            return migrated
        }
        return prefs.getString(KEY_SEARCH_ENGINE, DEFAULT_SEARCH_ENGINE) ?: DEFAULT_SEARCH_ENGINE
    }

    // === GESTIÓN DE MODO DE REPETICIÓN ===

    /**
     * Establece el modo de repetición.
     * @param context Contexto de la aplicación
     * @param repeatMode Modo de repetición a establecer ("off", "one", "all")
     */
    fun setRepeatMode(context: Context, repeatMode: String) {
        getPrefs(context).edit {
            putString(KEY_REPEAT_MODE, repeatMode)
        }
    }

    /**
     * Obtiene el modo de repetición actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Modo de repetición actual (por defecto "off")
     */
    fun getRepeatMode(context: Context): String {
        return getPrefs(context).getString(KEY_REPEAT_MODE, DEFAULT_REPEAT_MODE) ?: DEFAULT_REPEAT_MODE
    }

    /**
     * Obtiene el siguiente modo de repetición en el ciclo.
     * @param currentMode Modo actual
     * @return Siguiente modo en el ciclo off -> one -> all -> off
     */
    fun getNextRepeatMode(currentMode: String): String {
        return when (currentMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_OFF
            else -> REPEAT_MODE_OFF
        }
    }

    // === GESTIÓN DE IDIOMA ===

    /**
     * Establece el idioma de la aplicación.
     * @param context Contexto de la aplicación
     * @param language Idioma a establecer ("español", "english", "català")
     */
    fun setLanguage(context: Context, language: String) {
        getPrefs(context).edit {
            putString(KEY_LANGUAGE, language)
        }
    }

    /**
     * Obtiene el idioma actual de la aplicación.
     * @param context Contexto de la aplicación
     * @return Idioma actual (por defecto "español")
     */
    fun getLanguage(context: Context): String {
        val prefs = getPrefs(context)
        val stored = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        // Migrar valor legacy "japanese" (ASCII) a la clave usada en Translations ("日本語")
        if (stored == "japanese") {
            // Actualizar la preferencia para futuras lecturas
            setLanguage(context, LANGUAGE_JAPANESE)
            return LANGUAGE_JAPANESE
        }
        return stored
    }

    // === GESTIÓN DE ACCIONES DE SWIPE ===

    /**
     * Establece la acción para el swipe izquierdo.
     * @param context Contexto de la aplicación
     * @param action Acción a establecer
     */
    fun setSwipeLeftAction(context: Context, action: String) {
        getPrefs(context).edit {
            putString(KEY_SWIPE_LEFT_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el swipe izquierdo.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "add_to_queue")
     */
    fun getSwipeLeftAction(context: Context): String {
        val action = getPrefs(context).getString(KEY_SWIPE_LEFT_ACTION, DEFAULT_SWIPE_LEFT_ACTION) ?: DEFAULT_SWIPE_LEFT_ACTION
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
        getPrefs(context).edit {
            putString(KEY_SWIPE_RIGHT_ACTION, action)
        }
    }

    /**
     * Obtiene la acción configurada para el swipe derecho.
     * @param context Contexto de la aplicación
     * @return Acción actual (por defecto "add_to_liked_songs")
     */
    fun getSwipeRightAction(context: Context): String {
        val action = getPrefs(context).getString(KEY_SWIPE_RIGHT_ACTION, DEFAULT_SWIPE_RIGHT_ACTION) ?: DEFAULT_SWIPE_RIGHT_ACTION
        // Migración: la antigua acción "download" del feature local ya no existe
        if (action == "download") {
            setSwipeRightAction(context, DEFAULT_SWIPE_RIGHT_ACTION)
            return DEFAULT_SWIPE_RIGHT_ACTION
        }
        return action
    }

    // === GESTIÓN DE NICKNAME DEL USUARIO PARA FEED ===

    /**
     * Obtiene el nickname del usuario para el sistema de recomendaciones.
     * @param context Contexto de la aplicación
     * @return Nickname del usuario o null si no está configurado
     */
    fun getUserNickname(context: Context): String? {
        return getPrefs(context).getString(KEY_USER_NICKNAME, null)
    }

    /**
     * Establece el nickname del usuario para el sistema de recomendaciones.
     * @param context Contexto de la aplicación
     * @param nickname Nickname del usuario
     */
    fun setUserNickname(context: Context, nickname: String) {
        getPrefs(context).edit {
            putString(KEY_USER_NICKNAME, nickname.trim())
        }
    }

    // === COPIA DE SEGURIDAD AUTOMÁTICA ===

    /**
     * Carpeta SAF donde la app mantiene `plyr-sync.zip` actualizado.
     *
     * Es un *árbol* (`OpenDocumentTree`), no un documento: el permiso que se
     * toma sobre él es persistente y sobrevive a reinicios, así que la app
     * puede reescribir el archivo por su cuenta sin volver a preguntar nada.
     * Null si el usuario aún no ha elegido carpeta.
     */
    fun getBackupTreeUri(context: Context): String? =
        getPrefs(context).getString(KEY_BACKUP_TREE_URI, null)

    /**
     * Fija la carpeta de copia de seguridad. `documentId` es el documento hijo
     * ya cacheado dentro de ese árbol (o null si aún no existe, en cuyo caso
     * `BackupFolder` lo creará). Se guardan juntos porque el `documentId` solo
     * tiene sentido dentro de su árbol.
     */
    fun setBackupTree(context: Context, treeUri: String, documentId: String?) {
        getPrefs(context).edit {
            putString(KEY_BACKUP_TREE_URI, treeUri)
            if (documentId != null) putString(KEY_BACKUP_DOC_ID, documentId) else remove(KEY_BACKUP_DOC_ID)
            // El contenido escrito pertenece a la carpeta anterior: no vale
            // para la nueva, así que se olvida la huella.
            remove(KEY_BACKUP_HASH)
        }
    }

    /**
     * `documentId` cacheado del archivo de copia dentro del árbol elegido, o
     * null si todavía no se ha creado. Evita consultar al proveedor en cada
     * sincronización.
     */
    fun getBackupDocumentId(context: Context): String? =
        getPrefs(context).getString(KEY_BACKUP_DOC_ID, null)

    fun setBackupDocumentId(context: Context, documentId: String) {
        getPrefs(context).edit { putString(KEY_BACKUP_DOC_ID, documentId) }
    }

    /**
     * Huella SHA-256 de lo último escrito en la carpeta. Sirve para no reescribir
     * el ZIP cuando los datos no han cambiado desde la última sincronización.
     */
    fun getBackupHash(context: Context): String? =
        getPrefs(context).getString(KEY_BACKUP_HASH, null)

    fun setBackupHash(context: Context, hash: String) {
        getPrefs(context).edit { putString(KEY_BACKUP_HASH, hash) }
    }

    /**
     * Si la app debe volcar los cambios a la carpeta por su cuenta al salir.
     * Desactivado por defecto: hasta que el usuario no elija una carpeta no hay
     * nada adónde escribir, y algunos no quieren gastar datos con la copia.
     */
    fun isAutoSyncEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AUTO_SYNC, false) &&
            getPrefs(context).getString(KEY_BACKUP_TREE_URI, null) != null

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit { putBoolean(KEY_AUTO_SYNC, enabled) }
    }

    /**
     * Olvida la carpeta de copia: se usa cuando el permiso SAF deja de ser
     * válido (el usuario la borró o revocó el acceso) para no reintentar en
     * bucle contra un árbol inalcanzable.
     */
    fun clearBackupTree(context: Context) {
        getPrefs(context).edit {
            remove(KEY_BACKUP_TREE_URI)
            remove(KEY_BACKUP_DOC_ID)
            remove(KEY_BACKUP_HASH)
            putBoolean(KEY_AUTO_SYNC, false)
        }
    }

}
