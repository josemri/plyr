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
            "theme" to "tema",
            "theme_dark" to "oscuro",
            "theme_light" to "claro",
            "theme_system" to "sistema",
            "theme_auto" to "auto",
            "language" to "idioma",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",

            // Home Screen
            "home_feed" to "feed",
            "exit_message" to "Presiona de nuevo para salir",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "añadir recomendación",
            "loading" to "cargando...",
            "no_recommendations" to "no hay recomendaciones",

            // Search Screen
            "search_title" to "plyr_buscar",
            "search_placeholder" to "buscar música...",
            "search_loading" to "cargando...",
            "search_error" to "error",
            "search_scan_qr" to "qr",
            "search_youtube_results" to "resultados de youtube",
            "search_load_more" to "cargar más",

            // Search Screen - Additional translations
            "search_error_processing_qr" to "Error procesando QR",
            "permission_denied" to "Permiso de cámara denegado",
            "retry" to "Reintentar",
            "close" to "Cerrar",

            // Playlist / Form labels
            "playlist_name" to "Nombre de la playlist",
            "description" to "Descripción",
            "description_optional" to "Descripción (opcional)",
            "search_tracks_label" to "Buscar canciones",
            "create_playlist" to "Crear playlist",

            // Queue Screen
            "plyr_queue" to "plyr_cola",
            "no_tracks_loaded" to "Ninguna lista cargada",
            "player_not_available" to "reproductor no disponible",

            //Playlists Screen
            "loading_tracks" to "Cargando canciones...",

            // ADDITIONAL KEYS (SPANISH)
            "error_obtaining_audio" to "No se pudo obtener audio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_share" to "<shr>",
            "btn_nfc" to "<nfc>",

            // Offline storage
            "storage_title" to "audio offline",
            "storage_empty" to "no hay audio descargado",
            "storage_message" to "audio: %1\$d pistas · %2\$s",
            "delete_download" to "eliminar descarga",

            // SongListItem
            "add_to_playlist" to "añadir a playlist",
            "add_to_queue" to "añadir a cola",
            "share" to "compartir",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "cola",
            "swipe_action_liked" to "fav",
            "swipe_action_playlist" to "lista",
            "swipe_action_share" to "share",

            // Swipe Actions
            "swipe_left" to "swipe left",
            "swipe_right" to "swipe right",

            "share_me" to "< ¡compárteme! >",

            // Sync
            "sync" to "< sync >",
            "sync_synced" to "synced w/ %1\$s",
            "sync_working" to "sincronizando...",
            "sync_done" to "● copiado: %1\$d listas, %2\$d canciones",
            "sync_merged" to "+%1\$d listas, %2\$d canciones, %3\$d borradas del archivo",
            "sync_empty" to "● no hay listas que sincronizar",
            "sync_need_folder" to "● primero elige una carpeta",
            "sync_error" to "● no se pudo sincronizar",
            "sync_archive_unreadable" to "● no se pudo leer la copia de la carpeta, no se ha tocado nada",
            "sync_folder_denied" to "● no se pudo guardar el acceso a la carpeta",

            // NEW MISSING KEYS
            "app_logo" to "logotipo de plyr",
            "update_available" to "actualiza",
            "no_playlists" to "no hay playlists",
            "no_share_url" to "no hay nada que compartir con esta cancion",
            "plyr_lists" to "plyr_listas",

            // Shared labels
            "unknown_artist" to "Artista desconocido",
            "liked" to "♥ me gusta",
            "like" to "♡ me gusta",
            "continue" to "Continuar",
            "cancel" to "Cancelar",
            "share_via" to "Compartir vía",
            "delete_playlist_title" to "Eliminar lista",
            "delete_playlist_message" to "¿Seguro que quieres eliminar '%1\$s'? Esta acción no se puede deshacer.",
            "delete" to "Eliminar",
            "unsaved_changes_title" to "Cambios sin guardar",
            "unsaved_changes_message" to "Tienes cambios sin guardar. ¿Seguro que quieres salir?",
            "exit" to "Salir",
            "create_playlist_discarded" to "%1\$d de %2\$d canciones se añadieron (%3\$d sin vídeo)",
            "youtube_search_failed" to "La búsqueda en YouTube falló",
            "error_adding_track" to "Error al añadir la canción",
            "error_removing_track" to "Error al quitar la canción",

        ),

        // ENGLISH
        "english" to mapOf(
            // Config Screen
            "config_title" to "plyr_config",
            "theme" to "theme",
            "theme_dark" to "dark",
            "theme_light" to "light",
            "theme_system" to "system",
            "theme_auto" to "auto",
            "language" to "language",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "share_me" to "< share me! >",


            // Sync
            "sync" to "< sync >",
            "sync_synced" to "synced w/ %1\$s",
            "sync_working" to "syncing...",
            "sync_done" to "● copied: %1\$d playlists, %2\$d tracks",
            "sync_merged" to "+%1\$d playlists, %2\$d tracks, %3\$d deleted from the archive",
            "sync_empty" to "● no playlists to sync",
            "sync_need_folder" to "● pick a folder first",
            "sync_error" to "● could not sync",
            "sync_archive_unreadable" to "● couldn't read the copy in the folder, nothing was touched",
            "sync_folder_denied" to "● folder access could not be saved",

            // Home Screen
            "home_feed" to "feed",
            "exit_message" to "Press back again to exit",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "add recommendation",
            "loading" to "loading...",
            "no_recommendations" to "no recommendations",

            // Search Screen
            "search_title" to "plyr_search",
            "search_placeholder" to "search music...",
            "search_loading" to "loading...",
            "search_error" to "error",
            "search_scan_qr" to "qr",
            "search_youtube_results" to "youtube results",
            "search_load_more" to "load more",

            // Search Screen - Additional translations
            "search_error_processing_qr" to "Error processing QR",
            "permission_denied" to "Camera permission denied",
            "retry" to "Retry",
            "close" to "Close",

            // Playlist / Form labels
            "playlist_name" to "Playlist name",
            "description" to "Description",
            "description_optional" to "Description (optional)",
            "search_tracks_label" to "Search songs",
            "create_playlist" to "Create playlist",

            // Queue Screen
            "plyr_queue" to "plyr_queue",
            "no_tracks_loaded" to "No tracks loaded",

            // Playlists Screen
            "loading_tracks" to "Loading tracks...",

            // ADDITIONAL KEYS (ENGLISH)
            "error_obtaining_audio" to "Could not obtain audio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_share" to "<shr>",
            "btn_nfc" to "<nfc>",

            // Offline storage
            "storage_title" to "offline audio",
            "storage_empty" to "no downloaded audio",
            "storage_message" to "audio: %1\$d tracks · %2\$s",
            "delete_download" to "delete download",

            // SongListItem
            "add_to_playlist" to "add to playlist",
            "add_to_queue" to "add to queue",
            "share" to "share",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "queue",
            "swipe_action_liked" to "fav",
            "swipe_action_playlist" to "list",
            "swipe_action_share" to "share",

            // Swipe Actions
            "swipe_left" to "swipe left",
            "swipe_right" to "swipe right",

            // NEW MISSING KEYS
            "app_logo" to "plyr logo",
            "update_available" to "update",
            "no_playlists" to "no playlists",
            "no_share_url" to "nothing to share with this track",
            "plyr_lists" to "plyr_lists",
            "player_not_available" to "player not available",

            // Shared labels
            "unknown_artist" to "Unknown Artist",
            "liked" to "♥ liked",
            "like" to "♡ like",
            "continue" to "Continue",
            "cancel" to "Cancel",
            "share_via" to "Share via",
            "delete_playlist_title" to "Delete playlist",
            "delete_playlist_message" to "Are you sure you want to delete '%1\$s'? This action cannot be undone.",
            "delete" to "Delete",
            "unsaved_changes_title" to "Unsaved changes",
            "unsaved_changes_message" to "You have unsaved changes. Are you sure you want to exit?",
            "exit" to "Exit",
            "create_playlist_discarded" to "%1\$d of %2\$d tracks were added (%3\$d without a video)",
            "youtube_search_failed" to "YouTube search failed",
            "error_adding_track" to "Error adding track",
            "error_removing_track" to "Error removing track",

        ),

        // CATALÀ
        "català" to mapOf(
            // Config Screen
            "config_title" to "plyr_configuració",
            "theme" to "tema",
            "theme_dark" to "fosc",
            "theme_light" to "clar",
            "theme_system" to "sistema",
            "theme_auto" to "auto",
            "language" to "idioma",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "share_me" to "< Comparteix-me! >",


            // Sync
            "sync" to "< sync >",
            "sync_synced" to "synced w/ %1\$s",
            "sync_working" to "sincronitzant...",
            "sync_done" to "● copiat: %1\$d llistes, %2\$d cançons",
            "sync_merged" to "+%1\$d llistes, %2\$d cançons, %3\$d esborrades de l'arxiu",
            "sync_empty" to "● no hi ha llistes per sincronitzar",
            "sync_need_folder" to "● primer tria una carpeta",
            "sync_error" to "● no es va poder sincronitzar",
            "sync_archive_unreadable" to "● no es va poder llegir la còpia de la carpeta, no s'ha tocat res",
            "sync_folder_denied" to "● no es va poder desar l'accés a la carpeta",


            // Home Screen
            "home_feed" to "feed",
            "exit_message" to "Prem de nou per sortir",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "afegir recomanació",
            "loading" to "carregant...",
            "no_recommendations" to "no hi ha recomanacions",
            "nickname_description" to "tu apodo se usará en grupos y recomendaciones",

            // Search Screen
            "search_title" to "plyr_cercar",
            "search_placeholder" to "cercar música...",
            "search_loading" to "carregant...",
            "search_error" to "error",
            "search_scan_qr" to "qr",
            "search_youtube_results" to "resultats de youtube",
            "search_load_more" to "carregar més",

            // Search Screen - Additional translations
            "search_error_processing_qr" to "Error processant QR",
            "permission_denied" to "Permís de càmera denegat",
            "retry" to "Reintenta",
            "close" to "Tanca",

            // Playlist / Form labels
            "playlist_name" to "Nom de la playlist",
            "description" to "Descripció",
            "description_optional" to "Descripció (opcional)",
            "search_tracks_label" to "Cercar cançons",
            "create_playlist" to "Crear playlist",

            // Queue Screen
            "plyr_queue" to "plyr_cua",
            "no_tracks_loaded" to "Cap cançó carregada",
            "player_not_available" to "el reproductor no està disponible",

            // Playlists Screen
            "loading_tracks" to "Carregant cançons...",

            // ADDITIONAL KEYS (CATALÀ)
            "error_obtaining_audio" to "No s'ha pogut obtenir àudio",
            "error_prefix" to "Error: ",

            // Playlist actions and dialogs
            "btn_share" to "<shr>",
            "btn_nfc" to "<nfc>",

            // Offline storage
            "storage_title" to "àudio offline",
            "storage_empty" to "no hi ha àudio descarregat",
            "storage_message" to "àudio: %1\$d pistes · %2\$s",
            "delete_download" to "elimina la descàrrega",

            // SongListItem
            "add_to_playlist" to "afegir a playlist",
            "add_to_queue" to "afegir a cua",
            "share" to "compartir",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "cua",
            "swipe_action_liked" to "fav",
            "swipe_action_playlist" to "llista",
            "swipe_action_share" to "compartir",

            // Swipe Actions
            "swipe_left" to "lliscar esquerra",
            "swipe_right" to "lliscar dreta",


            // NEW MISSING KEYS
            "app_logo" to "logotip de plyr",
            "update_available" to "actualitza",
            "no_playlists" to "no hi ha playlists",
            "no_share_url" to "no hi ha res a compartir amb aquesta canco",
            "plyr_lists" to "plyr_llistes",

            // Shared labels
            "unknown_artist" to "Artista desconegut",
            "liked" to "♥ m'agrada",
            "like" to "♡ m'agrada",
            "continue" to "Continua",
            "cancel" to "Cancel·la",
            "share_via" to "Compartir via",
            "delete_playlist_title" to "Elimina la llista",
            "delete_playlist_message" to "Segur que vols eliminar '%1\$s'? Aquesta acció no es pot desfer.",
            "delete" to "Elimina",
            "unsaved_changes_title" to "Canvis sense desar",
            "unsaved_changes_message" to "Tens canvis sense desar. Segur que vols sortir?",
            "exit" to "Surt",
            "create_playlist_discarded" to "%1\$d de %2\$d cançons es van afegir (%3\$d sense vídeo)",
            "youtube_search_failed" to "La cerca a YouTube ha fallat",
            "error_adding_track" to "Error en afegir la cançó",
            "error_removing_track" to "Error en treure la cançó",

        ),

        // 日本語 (JAPONÉS)
        "日本語" to mapOf(
            // Config Screen
            "config_title" to "plyr_設定",
            "theme" to "テーマ",
            "theme_dark" to "ダーク",
            "theme_light" to "ライト",
            "theme_system" to "システム",
            "theme_auto" to "自動",
            "language" to "言語",
            "lang_spanish" to "es",
            "lang_english" to "en",
            "lang_catalan" to "ca",
            "lang_japanese" to "ja",
            "share_me" to "< 私を共有！ >",


            // Sync
            "sync" to "< sync >",
            "sync_synced" to "synced w/ %1\$s",
            "sync_working" to "同期中...",
            "sync_done" to "● コピー: %1\$d リスト, %2\$d 曲",
            "sync_merged" to "アーカイブから +%1\$d リスト、%2\$d 曲、%3\$d 削除",
            "sync_empty" to "● 同期するリストがありません",
            "sync_need_folder" to "● 先にフォルダを選んでください",
            "sync_error" to "● 同期できませんでした",
            "sync_archive_unreadable" to "● フォルダのコピーを読み込めませんでした。何も変更していません",
            "sync_folder_denied" to "● フォルダへのアクセスを保存できませんでした",


            // Home Screen
            "home_feed" to "feed",
            "exit_message" to "もう一度押すと終了します",

            // Feed Screen
            "feed_title" to "plyr_feed",
            "add_recommendation" to "おすすめを追加",
            "loading" to "読み込み中...",
            "no_recommendations" to "おすすめはありません",
            "nickname_description" to "あなたのニックネームはグループやおすすめに使用されます",

            // Search Screen
            "search_title" to "plyr_検索",
            "search_placeholder" to "音楽を検索...",
            "search_loading" to "読み込み中...",
            "search_error" to "エラー",
            "search_scan_qr" to "QR",
            "search_youtube_results" to "YouTubeの結果",
            "search_load_more" to "もっと読み込む",

            // Search Screen - Additional
            "search_error_processing_qr" to "QRの処理中にエラー",
            "permission_denied" to "カメラの許可が拒否されました",
            "retry" to "再試行",
            "close" to "閉じる",

            // Playlist / Form labels
            "playlist_name" to "プレイリスト名",
            "description" to "説明",
            "description_optional" to "説明 (任意)",
            "search_tracks_label" to "曲を検索",
            "create_playlist" to "プレイリストを作成",

            // Queue Screen
            "plyr_queue" to "plyr_キュー",
            "no_tracks_loaded" to "曲が読み込まれていません",
            "player_not_available" to "プレイヤーが利用できません",

            // Playlists Screen
            "loading_tracks" to "曲を読み込み中...",

            // ADDITIONAL KEYS (JAPONÉS)
            "error_obtaining_audio" to "音声を取得できませんでした",
            "error_prefix" to "エラー: ",

            // Playlist actions and dialogs
            "btn_share" to "<shr>",
            "btn_nfc" to "<nfc>",

            // Offline storage
            "storage_title" to "オフライン音声",
            "storage_empty" to "ダウンロード済みの音声はありません",
            "storage_message" to "音声: %1\$d 曲 · %2\$s",
            "delete_download" to "ダウンロードを削除",

            // SongListItem
            "add_to_playlist" to "プレイリストに追加",
            "add_to_queue" to "キューに追加",
            "share" to "共有",

            // Swipe Actions - Short versions for config screen
            "swipe_action_queue" to "キュー",
            "swipe_action_liked" to "お気に",
            "swipe_action_playlist" to "リスト",
            "swipe_action_share" to "共有",

            // Swipe Actions
            "swipe_left" to "左スワイプ",
            "swipe_right" to "右スワイプ",

            // NEW MISSING KEYS
            "app_logo" to "plyr ロゴ",
            "update_available" to "更新",
            "no_playlists" to "プレイリストがありません",
            "no_share_url" to "この曲には共有できるものがありません",
            "plyr_lists" to "plyr_リスト",

            // Shared labels
            "unknown_artist" to "不明なアーティスト",
            "liked" to "♥ いいね済み",
            "like" to "♡ いいね",
            "continue" to "続ける",
            "cancel" to "キャンセル",
            "share_via" to "共有方法",
            "delete_playlist_title" to "プレイリストを削除",
            "delete_playlist_message" to "'%1\$s' を削除してもよろしいですか？この操作は元に戻せません。",
            "delete" to "削除",
            "unsaved_changes_title" to "保存されていない変更",
            "unsaved_changes_message" to "保存されていない変更があります。終了してもよろしいですか？",
            "exit" to "終了",
            "create_playlist_discarded" to "%2\$d 曲中 %1\$d 曲を追加しました（%3\$d 曲は動画なし）",
            "youtube_search_failed" to "YouTube 検索に失敗しました",
            "error_adding_track" to "曲の追加中にエラー",
            "error_removing_track" to "曲の削除中にエラー",
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
