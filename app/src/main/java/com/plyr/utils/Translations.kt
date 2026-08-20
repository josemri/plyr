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
            "user_nickname" to "> nombre_feed",
            "config_title" to "plyr_ajustes",
            "theme" to "> tema",
            "theme_dark" to "oscuro",
            "theme_light" to "claro",
            "theme_system" to "sistema",
            "theme_auto" to "auto",
            "search_engine" to "> motor_de_búsqueda",
            "language" to "> idioma",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "info" to "> información",
            "info_text" to "    ● ¡no piratees música!\n    ● cambia el motor con yt: / sp:",
            "lastfm_api_key" to "    ● lastfm_api_key:",

            // Swipe Actions Configuration
            "swipe_actions" to "> acciones_de_deslizamiento",
            "swipe_left" to "deslizar izquierda",
            "swipe_right" to "deslizar derecha",

            // Main Screen
            "no_results" to "no results found",
            "loading" to "loading...",

            // Home Screen
            "home_queue" to "cola",
            "home_new_playlist" to "nueva playlist",
            "home_feed" to "feed",
            "home_settings" to "ajustes",
            "exit_message" to "Presiona de nuevo para salir",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "añadir recomendación",
            "loading" to "cargando...",
            "no_recommendations" to "no hay recomendaciones",
            "invite_code" to "código de invitación",
            "nickname" to "apodo",
            "comment" to "comentario (opcional)",
            "enter_nickname" to "introduce tu apodo...",
            "recommendations" to "> recomendaciones",

            // Search Screen
            "search_title" to "plyr_buscar",
            "search_placeholder" to "buscar música...",
            "search_loading" to "cargando...",
            "search_error" to "error",
            "search_scan_qr" to "qr",
            "artist_image" to "Imagen del artista",
            "search_youtube_results" to "resultados de youtube",
            "search_load_more" to "cargar más",
            "colored by used engine" to "coloreado según el motor usado",

            // Search Screen - Additional translations
            "search_error_processing_qr" to "Error procesando QR",

            // Player
            "next" to "siguiente",
            "previous" to "anterior",

            // Playlist / Form labels
            "playlist_name" to "Nombre de la playlist",
            "description" to "Descripción",
            "description_optional" to "Descripción (opcional)",
            "search_tracks_label" to "Buscar canciones",
            "create_playlist" to "Crear playlist",

            // Queue Screen
            "plyr_queue" to "plyr_cola",
            "No tracks loaded" to "Ninguna lista cargada",
            "player_not_available" to "reproductor_no_disponible",

            //Playlists Screen
            "Loading tracks..." to "Cargando canciones...",

            // ADDITIONAL KEYS (SPANISH)
            "error_obtaining_audio" to "No se pudo obtener audio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_share" to "<share>",
            "btn_nfc" to "<nfc>",

            // SongListItem
            "add_to_playlist" to "añadir a playlist",
            "add_to_queue" to "añadir a cola",
            "share" to "compartir",
            "add_to_liked_songs" to "añadir a favoritos",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "cola",
            "swipe_action_liked" to "fav",
            "swipe_action_playlist" to "lista",
            "swipe_action_share" to "share",

            // Gestures Configuration
            "gestures_section" to "> gestos",
            "shake_for" to "    agitar para:",
            "shake_off" to "off",
            "shake_next" to "next",
            "shake_previous" to "prev",
            "shake_play_pause" to "play",
            "swipe_song_left" to "    swipe canción izquierda:",
            "swipe_song_right" to "    swipe canción derecha:",
            "orientation_for" to "    orientar para:",
            "orientation_off" to "off",
            "orientation_skip" to "saltar",
            "orientation_volume" to "volumen",

            "enabled" to "Activo",
            "disabled" to "Inactivo",

            "share_me" to "< ¡compárteme! >",

            // NEW MISSING KEYS
            "app_logo" to "logotipo de plyr",
            "no_playlists" to "no hay playlists",
            "plyr_lists" to "plyr_listas",
            "not_configured" to "no configurado"

        ),

        // ENGLISH
        "english" to mapOf(
            // Config Screen
            "user_nickname" to "> nickname_feed",
            "config_title" to "plyr_config",
            "theme" to "> theme",
            "theme_dark" to "dark",
            "theme_light" to "light",
            "theme_system" to "system",
            "theme_auto" to "auto",
            "search_engine" to "> search_engine",
            "language" to "> language",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "info" to "> info",
            "info_text" to "    ● don't pirate music!\n    ● Change engine with yt: / sp:",
            "lastfm_api_key" to "    ● lastfm_api_key:",
            "orientation_skip" to "skip",
            "orientation_volume" to "volume",
            "orientation_off" to "off",
            "orientation_for" to "    orientation for:",
            "share_me" to "< share me! >",


            // Swipe Actions Configuration
            "swipe_actions" to "> swipe_actions",
            "swipe_left" to "swipe left",
            "swipe_right" to "swipe right",

            // Main Screen
            "no_results" to "no results found",
            "loading" to "loading...",

            // Home Screen
            "home_queue" to "queue",
            "home_new_playlist" to "new playlist",
            "home_feed" to "feed",
            "home_settings" to "settings",
            "exit_message" to "Press back again to exit",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "add recommendation",
            "loading" to "loading...",
            "no_recommendations" to "no recommendations",
            "invite_code" to "invite code",
            "nickname" to "nickname",
            "comment" to "comment (optional)",
            "enter_nickname" to "enter your nickname...",
            "recommendations" to "> recommendations",

            // Search Screen
            "search_title" to "plyr_search",
            "search_placeholder" to "search music...",
            "search_loading" to "loading...",
            "search_error" to "error",
            "search_scan_qr" to "qr",
            "artist_image" to "Artist image",
            "search_youtube_results" to "youtube results",
            "search_load_more" to "load more",
            "colored by used engine" to "colored by used engine",

            // Search Screen - Additional translations
            "search_error_processing_qr" to "Error processing QR",

            // Player
            "next" to "next",
            "previous" to "previous",

            // Playlist / Form labels
            "playlist_name" to "Playlist name",
            "description" to "Description",
            "description_optional" to "Description (optional)",
            "search_tracks_label" to "Search songs",
            "create_playlist" to "Create playlist",

            // Queue Screen
            "plyr_queue" to "plyr_queue",
            "No tracks loaded" to "No tracks loaded",

            // Playlists Screen
            "Loading tracks..." to "Loading tracks...",

            // ADDITIONAL KEYS (ENGLISH)
            "error_obtaining_audio" to "Could not obtain audio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_share" to "<share>",
            "btn_nfc" to "<nfc>",

            // SongListItem
            "add_to_playlist" to "add to playlist",
            "add_to_queue" to "add to queue",
            "share" to "share",
            "add_to_liked_songs" to "add to liked songs",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "queue",
            "swipe_action_liked" to "fav",
            "swipe_action_playlist" to "list",
            "swipe_action_share" to "share",

            // Gestures Configuration
            "gestures_section" to "> gestures",
            "shake_for" to "    shake for:",
            "shake_off" to "off",
            "shake_next" to "next",
            "shake_previous" to "prev",
            "shake_play_pause" to "play",
            "swipe_song_left" to "    swipe song left:",
            "swipe_song_right" to "    swipe song right:",

            "enabled" to "Enabled",
            "disabled" to "Disabled",

            // NEW MISSING KEYS
            "app_logo" to "plyr logo",
            "no_playlists" to "no playlists",
            "plyr_lists" to "plyr_lists",
            "not_configured" to "not configured",
            "player_not_available" to "player not available",

        ),

        // CATALÀ
        "català" to mapOf(
            // Config Screen
            "user_nickname" to "> nom_feed",
            "config_title" to "plyr_configuració",
            "theme" to "> tema",
            "theme_dark" to "fosc",
            "theme_light" to "clar",
            "theme_system" to "sistema",
            "theme_auto" to "auto",
            "search_engine" to "> motor_cerca",
            "language" to "> idioma",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "info" to "> info",
            "info_text" to "    ● no piratejis música!\n    ● Canvia motor amb yt: / sp:",
            "lastfm_api_key" to "    ● lastfm_api_key:",
            "orientation_skip" to "saltar",
            "orientation_volume" to "volum",
            "orientation_off" to "off",
            "orientation_for" to "    orientar per:",
            "share_me" to "< Comparteix-me! >",


            // Swipe Actions Configuration
            "swipe_actions" to "> accions_de_lliscament",
            "swipe_left" to "lliscar esquerra",
            "swipe_right" to "lliscar dreta",

            // Main Screen
            "no_results" to "no s'han trobat resultats",
            "loading" to "carregant...",

            // Home Screen
            "home_queue" to "cua",
            "home_new_playlist" to "nova llista",
            "home_feed" to "feed",
            "home_settings" to "ajustos",
            "exit_message" to "Prem de nou per sortir",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "afegir recomanació",
            "loading" to "carregant...",
            "no_recommendations" to "no hi ha recomanacions",
            "invite_code" to "codi d'invitació",
            "nickname" to "apodo",
            "comment" to "comentari (opcional)",
            "user_nickname" to "apodo de usuario",
            "nickname_description" to "tu apodo se usará en grupos y recomendaciones",
            "enter_nickname" to "introduce tu apodo...",
            "recommendations" to "> recomanacions",

            // Search Screen
            "search_title" to "plyr_cercar",
            "search_placeholder" to "cercar música...",
            "search_loading" to "carregant...",
            "search_error" to "error",
            "search_scan_qr" to "qr",
            "artist_image" to "Imatge de l'artista",
            "search_youtube_results" to "resultats de youtube",
            "search_load_more" to "carregar més",
            "colored by used engine" to "colorat segons el motor usat",

            // Search Screen - Additional translations
            "search_error_processing_qr" to "Error processant QR",

            // Player
            "next" to "següent",
            "previous" to "anterior",

            // Playlist / Form labels
            "playlist_name" to "Nom de la playlist",
            "description" to "Descripció",
            "description_optional" to "Descripció (opcional)",
            "search_tracks_label" to "Cercar cançons",
            "create_playlist" to "Crear playlist",

            // Queue Screen
            "plyr_queue" to "plyr_キュー",
            "No tracks loaded" to "曲が読み込まれていません",
            "player_not_available" to "el reproductor no està disponible",

            // Playlists Screen
            "Loading tracks..." to "Carregant cançons...",

            // ADDITIONAL KEYS (CATALÀ)
            "error_obtaining_audio" to "No s'ha pogut obtenir àudio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_share" to "<compartir>",
            "btn_nfc" to "<nfc>",

            // SongListItem
            "add_to_playlist" to "afegir a playlist",
            "add_to_queue" to "afegir a cua",
            "share" to "compartir",
            "add_to_liked_songs" to "afegir a favorits",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "cua",
            "swipe_action_liked" to "fav",
            "swipe_action_playlist" to "llista",
            "swipe_action_share" to "compartir",

            // Gestures Configuration
            "gestures_section" to "> gestos",
            "shake_for" to "    agitar per:",
            "shake_off" to "off",
            "shake_next" to "següent",
            "shake_previous" to "anterior",
            "shake_play_pause" to "reproduir",
            "swipe_song_left" to "    lliscar cançó esquerra:",
            "swipe_song_right" to "    lliscar cançó dreta:",

            "enabled" to "Actiu",
            "disabled" to "Inactiu",

            "share_me" to "< Comparteix-me! >",


            // NEW MISSING KEYS
            "app_logo" to "logotip de plyr",
            "no_playlists" to "no hi ha playlists",
            "plyr_lists" to "plyr_llistes",
            "not_configured" to "no configurat",

        ),

        // 日本語 (JAPONÉS)
        "日本語" to mapOf(
            // Config Screen
            "user_nickname" to "> ユーザーネーム/ニックネーム",
            "config_title" to "plyr_設定",
            "theme" to "> テーマ",
            "theme_dark" to "ダーク",
            "theme_light" to "ライト",
            "theme_system" to "システム",
            "theme_auto" to "自動",
            "search_engine" to "> 検索エンジン",
            "language" to "> 言語",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "info" to "> 情報",
            "info_text" to "    ● 音楽の海賊行為はやめよう!\n    ● エンジン切替: yt: / sp:",
            "lastfm_api_key" to "    ● lastfm_api_key:",
            "orientation_skip" to "スキップ",
            "orientation_volume" to "音量",
            "orientation_off" to "オフ",
            "orientation_for" to "    向けて:",
            "share_me" to "< 私を共有！ >",


            // Swipe Actions Configuration
            "swipe_actions" to "> スワイプアクション",
            "swipe_left" to "左スワイプ",
            "swipe_right" to "右スワイプ",

            // Main Screen
            "no_results" to "結果が見つかりません",
            "loading" to "読み込み中...",

            // Home Screen
            "home_queue" to "キュー",
            "home_new_playlist" to "新しいプレイリスト",
            "home_feed" to "feed",
            "home_settings" to "設定",
            "exit_message" to "もう一度押すと終了します",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "おすすめを追加",
            "loading" to "読み込み中...",
            "no_recommendations" to "おすすめはありません",
            "invite_code" to "招待コード",
            "nickname" to "ニックネーム",
            "comment" to "コメント (任意)",
            "user_nickname" to "ユーザーのニックネーム",
            "nickname_description" to "あなたのニックネームはグループやおすすめに使用されます",
            "enter_nickname" to "ニックネームを入力...",
            "recommendations" to "> おすすめ",

            // Search Screen
            "search_title" to "plyr_検索",
            "search_placeholder" to "音楽を検索...",
            "search_loading" to "読み込み中...",
            "search_error" to "エラー",
            "search_scan_qr" to "QR",
            "artist_image" to "アーティスト画像",
            "search_youtube_results" to "YouTubeの結果",
            "search_load_more" to "もっと読み込む",
            "colored by used engine" to "検索エンジン別の色",

            // Search Screen - Additional
            "search_error_processing_qr" to "QRの処理中にエラー",

            // Player
            "next" to "次へ",
            "previous" to "前へ",

            // Playlist / Form labels
            "playlist_name" to "プレイリスト名",
            "description" to "説明",
            "description_optional" to "説明 (任意)",
            "search_tracks_label" to "曲を検索",
            "create_playlist" to "プレイリストを作成",

            // Queue Screen
            "plyr_queue" to "plyr_キュー",
            "No tracks loaded" to "曲が読み込まれていません",
            "player_not_available" to "プレイヤーが利用できません",

            // Playlists Screen
            "Loading tracks..." to "曲を読み込み中...",

            // ADDITIONAL KEYS (JAPONÉS)
            "error_obtaining_audio" to "音声を取得できませんでした",
            "error_prefix" to "エラー: ",

            // Playlist actions and dialogs
            "btn_share" to "<共有>",
            "btn_nfc" to "<nfc>",

            // SongListItem
            "add_to_playlist" to "プレイリストに追加",
            "add_to_queue" to "キューに追加",
            "share" to "共有",
            "add_to_liked_songs" to "お気に入りに追加",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "キュー",
            "swipe_action_liked" to "お気に",
            "swipe_action_playlist" to "リスト",
            "swipe_action_share" to "共有",

            // Gestures Configuration
            "gestures_section" to "> ジェスチャー",
            "shake_for" to "    振って:",
            "shake_off" to "off",
            "shake_next" to "次",
            "shake_previous" to "前",
            "shake_play_pause" to "再生",
            "swipe_song_left" to "    左にスワイプ:",
            "swipe_song_right" to "    右にスワイプ:",

            "enabled" to "有効",
            "disabled" to "無効",

            // NEW MISSING KEYS
            "app_logo" to "plyr ロゴ",
            "no_playlists" to "プレイリストがありません",
            "plyr_lists" to "plyr_リスト",
            "not_configured" to "未設定",
        ),
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
}
