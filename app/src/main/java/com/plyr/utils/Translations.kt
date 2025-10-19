package com.plyr.utils

import android.content.Context

/**
 * Sistema de traducciones para la aplicación plyr
 * Maneja las traducciones para Español, English y Català
 */
object Translations {

    // Mapa de traducciones por idioma
    private val translations = mapOf(
        // ESPAÑOL
        "español" to mapOf(
            // Config Screen
            "config_title" to "plyr_ajustes",
            "theme" to "> tema",
            "theme_dark" to "oscuro",
            "theme_light" to "claro",
            "search_engine" to "> motor_de_búsqueda",
            "search_spotify" to "spotify",
            "search_youtube" to "youtube",
            "audio_quality" to "> calidad_de_audio",
            "quality_low" to "baja",
            "quality_med" to "media",
            "quality_high" to "alta",
            "language" to "> idioma",
            "lang_spanish" to "español",
            "lang_english" to "english",
            "lang_catalan" to "català",
            "info" to "> información",
            "info_text" to "    ● ¡no piratees música!\n    ● cambia el motor con yt: / sp:",
            "sptfy_status" to "> estado_sptfy",
            "client" to "    ● cliente:",
            "api" to "    ● api:",
            "connected" to "conectado",
            "disconnected" to "desconectado",
            "configured" to "configurado",
            "not_configured" to "no configurado",
            "credentials_required" to "se requieren credenciales",
            "configure_credentials_first" to "configura las credenciales primero",
            "opening_browser" to "abriendo navegador...",
            "check_browser" to "revisa el navegador",
            "error_starting_oauth" to "error al iniciar oauth",
            "client_id" to "      id del client:",
            "client_secret" to "      secreto del client:",
            "enter_client_id" to "introduce tu id del cliente de spotify",
            "enter_client_secret" to "introduce tu secreto del cliente de spotify ",
            "how_to_get_credentials" to "      > cómo obtener credenciales de la api de spotify:",
            "instruction_1" to "1. ve a https://developer.spotify.com/dashboard",
            "instruction_2" to "2. inicia sesión con tu cuenta de spotify",
            "instruction_3" to "3. selecciona 'crear aplicación'",
            "instruction_4" to "4. rellena el nombre (por ejemplo, 'plyr mobile')",
            "instruction_5" to "5. establece el redirect uri: 'plyr://spotify/callback'",
            "instruction_6" to "6. selecciona 'mobile' y 'web  pi'",
            "instruction_7" to "7. haz clic en 'guardar'",
            "instruction_8" to "8. copia el client_id y el client_secret",
            "instruction_9" to "9. pegalos en los campos de arriba",
            "note_local_storage" to "      nota: estas credenciales se guardan localmente",

            // Main Screen
            "plyr_title" to "$ plyr", //QUITAR EL TITLE GENERAL
            "search_hint" to "search...",
            "no_results" to "no results found",
            "loading" to "loading...",

            // Home Screen
            "home_search" to "buscar",
            "home_playlists" to "listas",
            "home_queue" to "cola",
            "home_local" to "local",
            "home_settings" to "ajustes",
            "exit_message" to "Presiona de nuevo para salir",

            // Search Screen
            "search_title" to "plyr_buscar",
            "search_placeholder" to "buscar música...",
            "search_loading" to "cargando...",
            "search_no_results" to "no se encontraron resultados",
            "search_error" to "error",
            "search_spotify_not_connected" to "Spotify no está conectado",
            "search_token_not_available" to "Token de Spotify no disponible",
            "search_engine_not_recognized" to "Motor de búsqueda no reconocido",
            "search_error_loading_tracks" to "Error cargando canciones",
            "search_loading_tracks" to "cargando canciones...",
            "search_tracks" to "canciones",
            "search_albums" to "álbumes",
            "search_artists" to "artistas",
            "search_playlists" to "listas",
            "search_videos" to "videos",
            "search_youtube_playlists" to "listas youtube",
            "search_start" to "iniciar",
            "search_random" to "aleatorio",
            "search_save" to "guardar",
            "search_share" to "compartir",
            "search_saved" to "guardado",
            "search_error_saving" to "error al guardar",
            "search_followers" to "seguidores",
            "search_monthly_listeners" to "oyentes mensuales",
            "search_scan_qr" to "qr",
            "playlist_cover" to "Portada de la playlist",
            "artist_image" to "Imagen del artista",
            "search_query_empty_after_prefix" to "Querry vacía después del prefijo",
            "album_cover" to "Portada del album",
            "search_youtube_results" to "resultados de youtube",
            "search_load_more" to "cargar más",

            // Search Screen - Additional translations
            "search_select_playlist" to "Seleccionar playlist",
            "search_cancel" to "Cancelar",
            "search_removing" to "eliminando...",
            "search_removed" to "¡eliminado!",
            "search_saving_status" to "guardando...",
            "search_error_no_token" to "error: no hay token",
            "search_unsave" to "desguardar",
            "search_youtube_results" to "resultados youtube",
            "search_load_more" to "cargar más",
            "search_error_getting_track" to "Error obteniendo track",
            "search_error_getting_playlist" to "Error obteniendo playlist",
            "search_error_getting_album" to "Error obteniendo álbum",
            "search_error_getting_artist" to "Error obteniendo artista",
            "search_error_processing_qr" to "Error procesando QR",
            "search_adding_to_playlist" to "añadiendo canción a la playlist",

            // Player
            "now_playing" to "now playing",
            "play" to "play",
            "pause" to "pause",
            "next" to "siguiente",
            "previous" to "anterior",
            "repeat" to "repetir",
            "shuffle" to "mezclar",

            // Playlist / Form labels
            "playlist_name" to "Nombre de la playlist",
            "description" to "Descripción",
            "description_optional" to "Descripción (opcional)",
            "search_tracks_label" to "Buscar canciones",
            "create_playlist" to "Crear playlist",
            "playlist_name_placeholder" to "Nombre de la playlist",

            // Local Screen
            "plyr_local" to "plyr_local",
            "unknown error" to "error desconocido",
            "No tracks downloaded" to "Ninguna canción descargada",
            "delete track" to "eliminar canción",
            "Song {{track_name}} will be removed permanently" to "La canción {{track_name}} será eliminada permanentemente",
            "delete" to "eliminar",
            "cancel" to "cancelar",

            // Queue Screen
            "plyr_queue" to "plyr_cola",
            "No tracks loaded" to "Ninguna lista cargada",
            "Play a track to start a playlist" to "Reproduce una canción para iniciar una lista",
            "player_not_available" to "reproductor_no_disponible",

            //Playlists Screen
            "plyr_lists" to "plyr_listas",
            "<syncing...>" to "<sincronizando...>",
            "<sync>" to "<sincronizar>",
            "<new>" to "<crear>",
            "Spotify not connected" to "Spotify no conectado",
            "Loading tracks..." to "Cargando canciones...",

            // ADDITIONAL KEYS (SPANISH)
            "connected_successfully" to "conectado correctamente",
            "token_exchange_failed" to "intercambio de token fallido",
            "cancelled_by_user" to "cancelado por el usuario",
            "error_obtaining_audio" to "No se pudo obtener audio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_start" to "<start>",
            "btn_stop" to "<stop>",
            "btn_rand" to "<rand>",
            "btn_share" to "<share>",
            "btn_save" to "<save>",
            "btn_edit" to "<edit>",
            "btn_delete" to "<delete>",
            "creating" to "<creating...>",
            "create" to "<create>",
            "delete_playlist_title" to "Eliminar playlist",
            "delete_playlist_confirm" to "¿Seguro que quieres eliminar '%s'? Esta acción no se puede deshacer.",
            "unsaved_changes_title" to "Cambios sin guardar",
            "unsaved_changes_text" to "Tienes cambios sin guardar. ¿Seguro que quieres salir?",
            "exit_label" to "Salir",
            "cancel_label" to "Cancelar",
            "delete_label" to "Eliminar",

        ),

        // ENGLISH
        "english" to mapOf(
            // Config Screen
            "config_title" to "plyr_config",
            "theme" to "> theme",
            "theme_dark" to "dark",
            "theme_light" to "light",
            "search_engine" to "> search_engine",
            "search_spotify" to "spotify",
            "search_youtube" to "youtube",
            "audio_quality" to "> audio_quality",
            "quality_low" to "low",
            "quality_med" to "med",
            "quality_high" to "high",
            "language" to "> language",
            "lang_spanish" to "español",
            "lang_english" to "english",
            "lang_catalan" to "català",
            "info" to "> info",
            "info_text" to "    ● don't pirate music!\n    ● Change engine with yt: / sp:",
            "sptfy_status" to "> sptfy_status",
            "client" to "    ● client:",
            "api" to "    ● api:",
            "connected" to "connected",
            "disconnected" to "disconnected",
            "configured" to "configured",
            "not_configured" to "not_configured",
            "credentials_required" to "credentials required",
            "configure_credentials_first" to "configure credentials first",
            "opening_browser" to "opening_browser...",
            "check_browser" to "check_browser",
            "error_starting_oauth" to "error_starting_oauth",
            "client_id" to "      client_id:",
            "client_secret" to "      client_secret:",
            "enter_client_id" to "enter your spotify client id",
            "enter_client_secret" to "enter your spotify client secret",
            "how_to_get_credentials" to "      > how to get spotify api credentials:",
            "instruction_1" to "1. go to https://developer.spotify.com/dashboard",
            "instruction_2" to "2. log in with your spotify account",
            "instruction_3" to "3. click 'create app'",
            "instruction_4" to "4. fill app name (e.g., 'plyr mobile')",
            "instruction_5" to "5. set redirect uri: 'plyr://spotify/callback'",
            "instruction_6" to "6. select 'mobile' and 'web api'",
            "instruction_7" to "7. click 'save'",
            "instruction_8" to "8. copy client id and client secret",
            "instruction_9" to "9. paste them in the fields above",
            "note_local_storage" to "      note: these credentials are stored locally",

            // Main Screen
            "plyr_title" to "$ plyr",
            "search_hint" to "search...",
            "no_results" to "no results found",
            "loading" to "loading...",

            // Home Screen
            "home_search" to "search",
            "home_playlists" to "playlists",
            "home_queue" to "queue",
            "home_local" to "local",
            "home_settings" to "settings",
            "exit_message" to "Press back again to exit",

            // Search Screen
            "search_title" to "plyr_search",
            "search_placeholder" to "search music...",
            "search_loading" to "loading...",
            "search_no_results" to "no results found",
            "search_error" to "error",
            "search_spotify_not_connected" to "Spotify is not connected",
            "search_token_not_available" to "Spotify token not available",
            "search_engine_not_recognized" to "Search engine not recognized",
            "search_error_loading_tracks" to "Error loading tracks",
            "search_loading_tracks" to "loading tracks...",
            "search_tracks" to "tracks",
            "search_albums" to "albums",
            "search_artists" to "artists",
            "search_playlists" to "playlists",
            "search_videos" to "videos",
            "search_youtube_playlists" to "youtube playlists",
            "search_start" to "start",
            "search_random" to "random",
            "search_save" to "save",
            "search_share" to "share",
            "search_saved" to "saved",
            "search_error_saving" to "error saving",
            "search_followers" to "followers",
            "search_monthly_listeners" to "monthly listeners",
            "search_scan_qr" to "qr",
            "playlist_cover" to "Playlist cover",
            "artist_image" to "Artist image",
            "search_query_empty_after_prefix" to "Query empty after prefix",
            "album_cover" to "Album cover",
            "search_youtube_results" to "youtube results",
            "search_load_more" to "load more",

            // Search Screen - Additional translations
            "search_select_playlist" to "Select playlist",
            "search_cancel" to "Cancel",
            "search_removing" to "removing...",
            "search_removed" to "removed!",
            "search_saving_status" to "saving...",
            "search_error_no_token" to "error: no token",
            "search_unsave" to "unsave",
            "search_youtube_results" to "youtube results",
            "search_load_more" to "load more",
            "search_error_getting_track" to "Error getting track",
            "search_error_getting_playlist" to "Error getting playlist",
            "search_error_getting_album" to "Error getting album",
            "search_error_getting_artist" to "Error getting artist",
            "search_error_processing_qr" to "Error processing QR",
            "search_adding_to_playlist" to "adding song to playlist",

            // Player
            "now_playing" to "now playing",
            "play" to "play",
            "pause" to "pause",
            "next" to "next",
            "previous" to "previous",
            "repeat" to "repeat",
            "shuffle" to "shuffle",

            // ADDITIONAL KEYS (ENGLISH)
            "connected_successfully" to "Connected successfully",
            "token_exchange_failed" to "Token exchange failed",
            "cancelled_by_user" to "Cancelled by user",
            "error_obtaining_audio" to "Could not obtain audio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_start" to "<start>",
            "btn_stop" to "<stop>",
            "btn_rand" to "<rand>",
            "btn_share" to "<share>",
            "btn_save" to "<save>",
            "btn_edit" to "<edit>",
            "btn_delete" to "<delete>",
            "creating" to "<creating...>",
            "create" to "<create>",
            "delete_playlist_title" to "Delete playlist",
            "delete_playlist_confirm" to "Are you sure you want to delete '%s'? This action cannot be undone.",
            "unsaved_changes_title" to "Unsaved changes",
            "unsaved_changes_text" to "You have unsaved changes. Are you sure you want to exit?",
            "exit_label" to "Exit",
            "cancel_label" to "Cancel",
            "delete_label" to "Delete",

        ),

        // CATALÀ
        "català" to mapOf(
            // Config Screen
            "config_title" to "plyr_configuració",
            "theme" to "> tema",
            "theme_dark" to "fosc",
            "theme_light" to "clar",
            "search_engine" to "> motor_cerca",
            "search_spotify" to "spotify",
            "search_youtube" to "youtube",
            "audio_quality" to "> qualitat_audio",
            "quality_low" to "baixa",
            "quality_med" to "mitjana",
            "quality_high" to "alta",
            "language" to "> idioma",
            "lang_spanish" to "español",
            "lang_english" to "english",
            "lang_catalan" to "català",
            "info" to "> info",
            "info_text" to "    ● no piratejis música!\n    ● Canvia motor amb yt: / sp:",
            "sptfy_status" to "> estat_sptfy",
            "client" to "    ● client:",
            "api" to "    ● api:",
            "connected" to "connectat",
            "disconnected" to "desconnectat",
            "configured" to "configurat",
            "not_configured" to "no_configurat",
            "credentials_required" to "credencials requerides",
            "configure_credentials_first" to "configura les credencials primer",
            "opening_browser" to "obrint_navegador...",
            "check_browser" to "comprova_navegador",
            "error_starting_oauth" to "error_iniciant_oauth",
            "client_id" to "      client_id:",
            "client_secret" to "      client_secret:",
            "enter_client_id" to "introdueix el teu spotify client id",
            "enter_client_secret" to "introdueix el teu spotify client secret",
            "how_to_get_credentials" to "      > com obtenir credencials api spotify:",
            "instruction_1" to "1. ves a https://developer.spotify.com/dashboard",
            "instruction_2" to "2. inicia sessió amb el teu compte spotify",
            "instruction_3" to "3. clica 'create app'",
            "instruction_4" to "4. omple el nom app (ex., 'plyr mobile')",
            "instruction_5" to "5. estableix redirect uri: 'plyr://spotify/callback'",
            "instruction_6" to "6. selecciona 'mobile' i 'web api'",
            "instruction_7" to "7. clica 'save'",
            "instruction_8" to "8. copia client id i client secret",
            "instruction_9" to "9. enganxa'ls als camps de dalt",
            "note_local_storage" to "      nota: aquestes credencials es guarden localment",

            // Main Screen
            "plyr_title" to "$ plyr",
            "search_hint" to "cercar...",
            "no_results" to "no s'han trobat resultats",
            "loading" to "carregant...",

            // Home Screen
            "home_search" to "cercar",
            "home_playlists" to "llistes",
            "home_queue" to "cua",
            "home_local" to "local",
            "home_settings" to "ajustos",
            "exit_message" to "Prem de nou per sortir",

            // Search Screen
            "search_title" to "plyr_cercar",
            "search_placeholder" to "cercar música...",
            "search_loading" to "carregant...",
            "search_no_results" to "no s'han trobat resultats",
            "search_error" to "error",
            "search_spotify_not_connected" to "Spotify no està connectat",
            "search_token_not_available" to "Token de Spotify no disponible",
            "search_engine_not_recognized" to "Motor de cerca no reconegut",
            "search_error_loading_tracks" to "Error carregant cançons",
            "search_loading_tracks" to "carregant cançons...",
            "search_tracks" to "cançons",
            "search_albums" to "àlbums",
            "search_artists" to "artistes",
            "search_playlists" to "llistes",
            "search_videos" to "vídeos",
            "search_youtube_playlists" to "llistes youtube",
            "search_start" to "iniciar",
            "search_random" to "aleatori",
            "search_save" to "desar",
            "search_share" to "compartir",
            "search_saved" to "desat",
            "search_error_saving" to "error al desar",
            "search_followers" to "seguidors",
            "search_monthly_listeners" to "oients mensuals",
            "search_scan_qr" to "qr",
            "playlist_cover" to "Portada de la playlist",
            "artist_image" to "Imatge de l'artista",
            "search_query_empty_after_prefix" to "Querry buida després del prefix",
            "album_cover" to "Portada del album",
            "search_youtube_results" to "resultats de youtube",
            "search_load_more" to "carregar més",

            // Search Screen - Additional translations
            "search_select_playlist" to "Seleccionar playlist",
            "search_cancel" to "Cancelar",
            "search_removing" to "eliminant...",
            "search_removed" to "eliminat!",
            "search_saving_status" to "desant...",
            "search_error_no_token" to "error: no hi ha token",
            "search_unsave" to "desguardar",
            "search_youtube_results" to "resultats youtube",
            "search_load_more" to "carregar més",
            "search_error_getting_track" to "Error obtenint track",
            "search_error_getting_playlist" to "Error obtenint playlist",
            "search_error_getting_album" to "Error obtenint àlbum",
            "search_error_getting_artist" to "Error obtenint artista",
            "search_error_processing_qr" to "Error processant QR",
            "search_adding_to_playlist" to "afegint cançó a la playlist",

            // Player
            "now_playing" to "reproduint ara",
            "play" to "reproduir",
            "pause" to "pausa",
            "next" to "següent",
            "previous" to "anterior",
            "repeat" to "repetir",
            "shuffle" to "aleatori",

            // ADDITIONAL KEYS (CATALAN)
            "connected_successfully" to "connectat correctament",
            "token_exchange_failed" to "intercanvi de token fallit",
            "cancelled_by_user" to "cancel·lat per l'usuari",
            "error_obtaining_audio" to "No s'ha pogut obtenir l'àudio",
            "error_prefix" to "Error: ",

            "btn_start" to "<start>",
            "btn_stop" to "<stop>",
            "btn_rand" to "<rand>",
            "btn_share" to "<share>",
            "btn_save" to "<save>",
            "btn_edit" to "<edit>",
            "btn_delete" to "<delete>",
            "creating" to "<creating...>",
            "create" to "<create>",
            "delete_playlist_title" to "Eliminar playlist",
            "delete_playlist_confirm" to "Segur que vols eliminar '%s'? Aquesta acció no es pot desfer.",
            "unsaved_changes_title" to "Canvis sense desar",
            "unsaved_changes_text" to "Tens canvis sense desar. Segur que vols sortir?",
            "exit_label" to "Sortir",
            "cancel_label" to "Cancel·la",
            "delete_label" to "Eliminar",

        )
    )

    /**
     * Obtiene una traducción para una clave específica según el idioma actual
     * @param context Contexto de la aplicación
     * @param key Clave de la traducción
     * @return Traducción correspondiente o la clave si no existe
     */
    fun get(context: Context, key: String): String {
        val language = Config.getLanguage(context)
        return translations[language]?.get(key) ?: key
    }

    /**
     * Obtiene una traducción para una clave específica según un idioma específico
     * @param language Idioma deseado
     * @param key Clave de la traducción
     * @return Traducción correspondiente o la clave si no existe
     */
    fun get(language: String, key: String): String {
        return translations[language]?.get(key) ?: key
    }

    /**
     * Verifica si existe una traducción para una clave específica
     * @param context Contexto de la aplicación
     * @param key Clave de la traducción
     * @return true si existe, false en caso contrario
     */
    fun exists(context: Context, key: String): Boolean {
        val language = Config.getLanguage(context)
        return translations[language]?.containsKey(key) ?: false
    }
}
