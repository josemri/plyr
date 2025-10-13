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
            "config_title" to "$ plyr_config",
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
            "home_search" to "buscar",
            "home_playlists" to "listas",
            "home_queue" to "cola",
            "home_local" to "local",
            "home_settings" to "ajustes",
            "exit_message" to "Presiona de nuevo para salir",

            // Search Screen
            "search_title" to "$ search",
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
            "search_scan_qr" to "escanear qr",

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
            "next" to "next",
            "previous" to "previous",
            "repeat" to "repeat",
            "shuffle" to "shuffle"
        ),

        // ENGLISH
        "english" to mapOf(
            // Config Screen
            "config_title" to "$ plyr_config",
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
            "search_title" to "$ search",
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
            "search_scan_qr" to "scan qr",

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
            "shuffle" to "shuffle"
        ),

        // CATALÀ
        "català" to mapOf(
            // Config Screen
            "config_title" to "$ plyr_config",
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
            "search_title" to "$ cercar",
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
            "search_scan_qr" to "escanejar qr",

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
            "shuffle" to "aleatori"
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
