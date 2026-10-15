package com.plyr.desktop.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.download.AudioDownloader
import com.plyr.desktop.download.DownloadController
import com.plyr.desktop.download.DownloadedAudioStore
import com.plyr.desktop.player.PlayerController
import com.plyr.desktop.player.PlayerState
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.player.VlcAudioPlayer
import com.plyr.desktop.player.YouTubeTrackSourceResolver
import com.plyr.desktop.search.SearchController
import com.plyr.desktop.search.SearchResults
import com.plyr.desktop.search.YouTubePlaylistDetailController
import com.plyr.desktop.search.YouTubePlaylistDetailView
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.database.SettingsTombstoneStore
import com.plyr.desktop.feed.FeedController
import com.plyr.desktop.feed.FeedScreen
import com.plyr.desktop.media.DesktopMediaIntegration
import com.plyr.desktop.media.MediaInfo
import com.plyr.desktop.media.NoopMediaIntegration
import com.plyr.desktop.media.PlaybackControls
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.qr.QrShareDialog
import com.plyr.desktop.spotify.SpotifyImportController
import com.plyr.desktop.spotify.SpotifyImportService
import com.plyr.desktop.sync.BackupConfig
import com.plyr.desktop.sync.BackupController
import com.plyr.desktop.sync.DataExporter
import com.plyr.desktop.sync.DataImporter
import com.plyr.desktop.sync.DataSync
import com.plyr.desktop.ui.components.TrackMenuActions
import com.plyr.desktop.ui.screens.ConfigScreen
import com.plyr.desktop.ui.screens.HomeScreen
import com.plyr.desktop.ui.screens.QueueScreen
import com.plyr.desktop.ui.screens.SearchScreen
import com.plyr.desktop.utils.CoverCache
import com.plyr.desktop.utils.UpdateChecker
import com.plyr.network.YouTubeManager
import com.plyr.network.YouTubePlaylistInfo
import com.plyr.receivers.MediaCommand
import com.plyr.ui.components.ShareType
import com.plyr.ui.components.ShareableItem
import kotlinx.coroutines.delay
import java.nio.file.Path

/** Altura reservada abajo para que los controles flotantes no tapen el contenido. */
private val CONTROLS_RESERVED_HEIGHT = 96.dp

/** Cada cuánto se refresca la posición desde el motor mientras suena algo. */
private const val PROGRESS_POLL_MS = 500L

/**
 * Contenedor de navegación del escritorio y dueño de la reproducción.
 *
 * Equivalente a `com.plyr.ui.AudioListScreen` de Android, pero en vez de pagers
 * (que no encajan con ratón/teclado) usa un `Screen` explícito que se cambia
 * desde el menú de inicio. Desde M7 también es el punto donde se conectan el
 * reproductor ([PlayerController] sobre `VlcAudioPlayer`), la búsqueda de YouTube
 * ([SearchController]) y los controles flotantes. Desde M9 también posee la base
 * de datos persistente (bajo la carpeta de datos del SO).
 */
@Composable
fun AudioListScreen(
    modifier: Modifier = Modifier,
    initialScreen: Screen = Screen.HOME,
    openDatabase: () -> DesktopDatabase = { DesktopDatabase.open(Path.of(AppPaths.databaseFile)) },
    mediaIntegration: DesktopMediaIntegration = NoopMediaIntegration(),
    checkForUpdate: (suspend () -> UpdateChecker.UpdateInfo?)? = null,
    downloadStore: DownloadedAudioStore = DownloadedAudioStore(),
    onPlayerControllerReady: (PlayerController) -> Unit = {},
    onQuit: () -> Unit = {},
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    val goHome = { currentScreen = Screen.HOME }
    val scope = rememberCoroutineScope()

    val playerController = remember(scope, downloadStore) {
        PlayerController(
            scope = scope,
            openPlayer = { VlcAudioPlayer() },
            sourceResolver = YouTubeTrackSourceResolver(store = downloadStore),
            initialRepeatMode = DesktopConfig.repeatMode,
            onRepeatModeChanged = { DesktopConfig.repeatMode = it },
        )
    }
    val searchController = remember(scope) {
        SearchController(
            scope = scope,
            search = { query, page ->
                // M19: una sola búsqueda devuelve vídeos y listas. M21: `page`
                // pide la página siguiente, que `loadMore` irá añadiendo.
                val found = YouTubeManager.searchAll(query, page = page)
                SearchResults(
                    videos = found.videos.mapIndexed { index, item ->
                        PlayerTrack.fromSearchResult(index, item)
                    },
                    playlists = found.playlists,
                    hasMore = found.hasMore,
                )
            },
        )
    }

    // Reproduce una lista de pistas y salta a la cola; lo comparten el detalle de
    // listas locales (M8) y el de listas de YouTube (M19).
    val playTracks: (List<com.plyr.database.TrackRecord>, Int) -> Unit = { tracks, index ->
        playerController.play(tracks.toPlayerTracks(), index)
        currentScreen = Screen.QUEUE
    }

    DisposableEffect(playerController) {
        onDispose { playerController.release() }
    }

    // Integración nativa (M13): MPRIS publica metadatos y atiende las teclas
    // multimedia; la bandeja del sistema comparte estos mismos controles.
    val playbackControls = remember(playerController, onQuit) {
        ControllerPlaybackControls(playerController, onQuit)
    }
    DisposableEffect(mediaIntegration, playbackControls) {
        mediaIntegration.attach(playbackControls)
        onDispose { mediaIntegration.close() }
    }
    LaunchedEffect(playerController) { onPlayerControllerReady(playerController) }
    LaunchedEffect(playerController, mediaIntegration) {
        playerController.state.collect { state ->
            mediaIntegration.updateNowPlaying(state.toMediaInfo(), state.isPlaying, state.positionMs)
        }
    }

    // Base de datos persistente bajo la carpeta de datos del SO (`AppPaths`, M9).
    // Se inyecta para que los tests usen una en memoria y no toquen el disco del
    // usuario, y se cierra al salir de la pantalla.
    val database = remember(openDatabase) { openDatabase() }
    DisposableEffect(database) {
        onDispose { database.close() }
    }

    // Repositorio único de la sesión, con tombs persistentes (M10). Se comparte
    // entre la pantalla de listas y la copia de seguridad, y avisa a esta última
    // de cada cambio para programar el volcado a la carpeta elegida.
    val tombstones = remember { SettingsTombstoneStore() }
    val repository = remember(database, tombstones) { PlaylistRepository(database, tombstones) }
    val backupScope = rememberCoroutineScope()
    val dataSync = remember(repository, tombstones) {
        DataSync(
            exporter = DataExporter(repository, tombstones, CoverCache()),
            importer = DataImporter(repository, tombstones),
            config = BackupConfig(),
        )
    }
    val backupController = remember(dataSync, backupScope) { BackupController(dataSync, backupScope) }
    DisposableEffect(repository, dataSync) {
        repository.onChanged = { dataSync.markDirty() }
        onDispose {
            repository.onChanged = null
            dataSync.shutdown()
        }
    }

    // Importación de Spotify (M11): desacoplada del repositorio y de Android, con
    // la búsqueda real de YouTube y el guardado en la base de escritorio.
    val spotifyController = remember(scope, repository) {
        SpotifyImportController(scope, SpotifyImportService(repository))
    }
    // Feed de recomendaciones (M12): Supabase inyectado en el propio controlador.
    val feedController = remember(scope) { FeedController(scope) }
    // Listas (M8): el controlador vive aquí —y no en la pantalla— porque la
    // búsqueda también lo usa para "añadir a lista".
    val playlistsController = remember(scope, repository) {
        com.plyr.desktop.playlists.PlaylistsController(scope, repository)
    }

    // Buscador de pistas de YouTube reutilizable (M22): lo usan el alta de listas
    // (F21) y el modo edición del detalle (F26). Se recuerda para que su
    // identidad no cambie en cada recomposición (los composables lo `remember`n).
    val searchTracks: suspend (String) -> List<PlayerTrack> =
        remember { { query -> searchYouTubeTracks(query) } }

    // Acciones por pista (M22) que comparten búsqueda y cola: me gusta, añadir a
    // cola y compartir. "Añadir a lista" se resuelve con el repositorio.
    val isTrackLiked: (PlayerTrack) -> Boolean = { track ->
        playlistsController.isLiked(track.videoId, track.title, track.artist)
    }
    val toggleTrackLike: (PlayerTrack) -> Unit = { track ->
        playlistsController.toggleLike(track.videoId, track.title, track.artist, track.id)
    }
    val addTrackToQueue: (PlayerTrack) -> Unit = { track -> playerController.addToQueue(track) }
    var sharedTrack by remember { mutableStateOf<PlayerTrack?>(null) }
    val shareTrack: (PlayerTrack) -> Unit = { track -> sharedTrack = track }
    val trackMenuActions = TrackMenuActions(
        isLiked = isTrackLiked,
        onToggleLike = toggleTrackLike,
        onAddToQueue = addTrackToQueue,
        onShare = shareTrack,
    )
    // Descarga offline (M16 backend, M17 UI): un controlador por sesión que
    // comparten el detalle de lista (bajar/cancelar/borrar) y Ajustes (tamaño y
    // borrar todo). Reutiliza el mismo almacén que el resolutor de reproducción.
    val downloadController = remember(scope, downloadStore) {
        DownloadController(
            scope = scope,
            store = downloadStore,
            downloader = AudioDownloader(store = downloadStore),
        )
    }
    val playerState by playerController.state.collectAsState()
    val searchState by searchController.state.collectAsState()
    val playlistsState by playlistsController.state.collectAsState()

    // Detalle de una lista de YouTube buscada (M19): carga sus pistas con la red
    // y las puede guardar como lista local reutilizando el repositorio de la
    // sesión. La lista abierta vive aquí para que la búsqueda vuelva a ella.
    val youtubeDetailController = remember(scope, repository) {
        YouTubePlaylistDetailController(
            scope = scope,
            repository = repository,
            loadVideos = { playlistId -> YouTubeManager.getPlaylistVideos(playlistId) },
        )
    }
    var selectedYouTubePlaylist by remember { mutableStateOf<YouTubePlaylistInfo?>(null) }
    val youtubeDetailState by youtubeDetailController.state.collectAsState()

    // Atajos de teclado (M15): el foco vive en la raíz para que las teclas
    // multimedia y los cursores lleguen aunque no haya nada más enfocado; los
    // campos de texto consumen lo suyo antes (fase de burbujeo).
    val shortcutsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { shortcutsFocus.requestFocus() } }

    // Refresca posición/duración solo mientras suena: con la reproducción parada
    // no hay bucle y la composición queda ociosa (los tests no se cuelgan).
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            playerController.refreshProgress()
            delay(PROGRESS_POLL_MS)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(shortcutsFocus)
                .focusable()
                .onKeyEvent { event ->
                    when (KeyboardShortcuts.commandFor(event)) {
                        MediaCommand.PLAY_PAUSE -> {
                            playerController.togglePlayPause()
                            true
                        }
                        MediaCommand.PLAY -> {
                            if (!playerState.isPlaying) playerController.togglePlayPause()
                            true
                        }
                        MediaCommand.PAUSE -> {
                            if (playerState.isPlaying) playerController.togglePlayPause()
                            true
                        }
                        MediaCommand.NEXT -> {
                            playerController.next()
                            true
                        }
                        MediaCommand.PREVIOUS -> {
                            playerController.previous()
                            true
                        }
                        MediaCommand.FAST_FORWARD -> {
                            playerController.seekTo(
                                playerState.positionMs + KeyboardShortcuts.SEEK_STEP_MS,
                            )
                            true
                        }
                        MediaCommand.REWIND -> {
                            playerController.seekTo(
                                (playerState.positionMs - KeyboardShortcuts.SEEK_STEP_MS).coerceAtLeast(0L),
                            )
                            true
                        }
                        MediaCommand.NONE -> false
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = CONTROLS_RESERVED_HEIGHT),
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        onNavigate = { currentScreen = it },
                        // Carrusel de listas (M18): mismas listas visibles que la
                        // pantalla de playlists; al pulsar una se abre su detalle.
                        playlists = playlistsState.playlists,
                        onOpenPlaylist = { id ->
                            playlistsController.selectPlaylist(id)
                            currentScreen = Screen.PLAYLISTS
                        },
                        checkForUpdate = checkForUpdate,
                    )
                    Screen.SEARCH -> {
                        val openPlaylist = selectedYouTubePlaylist
                        if (openPlaylist != null) {
                            // Detalle de la lista de YouTube abierta (M19): sus
                            // pistas, reproducir todo y guardarla como lista local.
                            YouTubePlaylistDetailView(
                                state = youtubeDetailState,
                                onPlayTracks = playTracks,
                                onToggleSave = youtubeDetailController::toggleSave,
                                onShuffle = {
                                    // Aleatorio (M20): misma idea que Android; no
                                    // navega, la barra flotante muestra lo que suena.
                                    playerController.startRandomized(youtubeDetailState.tracks.toPlayerTracks())
                                },
                                onBack = {
                                    youtubeDetailController.close()
                                    selectedYouTubePlaylist = null
                                },
                            )
                        } else {
                            SearchScreen(
                                state = searchState,
                                onQueryChange = searchController::onQueryChange,
                                onSearch = { searchController.performSearch(it) },
                                onPlayResult = { index ->
                                    if (index in searchState.results.indices) {
                                        playerController.play(searchState.results, index)
                                    }
                                },
                                onBack = goHome,
                                playlists = playlistsState.playlists,
                                onLoadMore = searchController::loadMore,
                                onAddToPlaylist = { track, playlistId ->
                                    playlistsController.addTrackToPlaylist(playlistId, track.toTrackRecord(playlistId))
                                },
                                onScanResult = { result ->
                                    when {
                                        result.source == "spotify" && result.id.isNotBlank() -> {
                                            spotifyController.onUrlChange("spotify:playlist:${result.id}")
                                            currentScreen = Screen.CONFIG
                                        }
                                        result.source == "youtube" && result.id.isNotBlank() -> {
                                            val url = "https://www.youtube.com/watch?v=${result.id}"
                                            searchController.onQueryChange(url)
                                            searchController.performSearch(url)
                                        }
                                    }
                                },
                                onToggleVideos = searchController::toggleVideos,
                                onTogglePlaylists = searchController::togglePlaylists,
                                onOpenPlaylist = { playlist ->
                                    selectedYouTubePlaylist = playlist
                                    youtubeDetailController.open(playlist)
                                },
                                menuActions = trackMenuActions,
                            )
                        }
                    }
                    Screen.QUEUE -> QueueScreen(
                        state = playerState,
                        onJump = playerController::jumpTo,
                        onMove = playerController::moveQueueItem,
                        onBack = goHome,
                        playlists = playlistsState.playlists,
                        onAddToPlaylist = { track, playlistId ->
                            playlistsController.addTrackToPlaylist(playlistId, track.toTrackRecord(playlistId))
                        },
                        menuActions = trackMenuActions,
                    )
                    Screen.PLAYLISTS -> {
                        com.plyr.desktop.playlists.PlaylistsScreen(
                            controller = playlistsController,
                            downloadController = downloadController,
                            onBack = goHome,
                            onPlayTracks = playTracks,
                            // Aleatorio (M20): baraja la lista abierta y arranca.
                            // No navega a la cola: así el botón sigue a la vista
                            // para poder parar con `<stop>`.
                            isRandomizing = playerState.isRandomizing,
                            onPlayRandom = { tracks -> playerController.startRandomized(tracks.toPlayerTracks()) },
                            onStopRandomize = playerController::stopRandomized,
                            searchTracks = searchTracks,
                            onAddToQueue = { track ->
                                playerController.addToQueue(PlayerTrack.fromTrackRecord(0, track))
                            },
                        )
                    }
                    Screen.FEED -> FeedScreen(
                        controller = feedController,
                        onBack = goHome,
                    )
                    Screen.CONFIG -> ConfigScreen(
                        onBack = goHome,
                        onRepeatModeSelected = playerController::setRepeatMode,
                        backupController = backupController,
                        spotifyImportController = spotifyController,
                        downloadController = downloadController,
                    )
                }
            }

            FloatingMusicControls(
                state = playerState.toUiState(),
                onPlayPause = playerController::togglePlayPause,
                onPrevious = playerController::previous,
                onNext = playerController::next,
                onRepeat = playerController::cycleRepeat,
                onSeek = playerController::seekTo,
                onShowQueue = { currentScreen = Screen.QUEUE },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    // Compartir una pista desde la cola o los resultados (M22): el QR se pinta
    // aquí, en la raíz, para que sobreviva al cambio de pantalla.
    sharedTrack?.let { track ->
        QrShareDialog(
            item = ShareableItem(
                remoteId = track.id,
                shareUrl = null,
                youtubeId = track.videoId,
                title = track.title,
                artist = track.artist,
                type = ShareType.TRACK,
            ),
            onDismiss = { sharedTrack = null },
        )
    }
}

/** Mapea las pistas de una lista local/YouTube a la cola del reproductor. */
private fun List<com.plyr.database.TrackRecord>.toPlayerTracks(): List<PlayerTrack> =
    mapIndexed { index, track -> PlayerTrack.fromTrackRecord(index, track) }

/**
 * Busca canciones en YouTube para el buscador de pistas de las listas (M22).
 * Devuelve solo vídeos (sin listas) y un puñado de resultados, como Android.
 */
private suspend fun searchYouTubeTracks(query: String): List<PlayerTrack> =
    YouTubeManager.searchAll(query, maxVideos = 10, maxPlaylists = 0).videos
        .mapIndexed { index, item -> PlayerTrack.fromSearchResult(index, item) }

/** Traduce el estado del reproductor al que pinta la barra flotante. */
private fun PlayerState.toUiState() = PlayerUiState(    title = title,
    isLoading = isLoading,
    error = error,
    isPlaying = isPlaying,
    positionMs = positionMs,
    durationMs = durationMs,
    queueIndex = currentIndex.takeIf { it >= 0 },
    queueSize = queue.size.takeIf { it > 0 },
    repeatMode = repeatMode,
)

/**
 * Adapta [PlayerController] a las órdenes que llegan del sistema (MPRIS y
 * bandeja). `play`/`pause`/`stop` se resuelven contra el estado actual para que
 * un `Play` con la música sonando o un `Pause` en silencio no hagan nada.
 */
private class ControllerPlaybackControls(
    private val controller: PlayerController,
    private val onQuit: () -> Unit,
) : PlaybackControls {
    override fun play() {
        if (!controller.state.value.isPlaying) controller.togglePlayPause()
    }

    override fun pause() {
        if (controller.state.value.isPlaying) controller.togglePlayPause()
    }

    override fun togglePlayPause() = controller.togglePlayPause()

    override fun next() = controller.next()

    override fun previous() = controller.previous()

    override fun stop() {
        if (controller.state.value.isPlaying) controller.togglePlayPause()
    }

    override fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    override fun quit() = onQuit()
}

/** Metadatos que se publican en el escritorio (MPRIS) desde el estado actual. */
private fun PlayerState.toMediaInfo(): MediaInfo? {
    val track = queue.getOrNull(currentIndex)
    val trackTitle = title ?: track?.title ?: return null
    return MediaInfo(
        title = trackTitle,
        artist = track?.artist.orEmpty(),
        durationMs = durationMs,
        trackId = trackPath(track?.id),
    )
}

/** Ruta MPRIS del `trackid` (D-Bus exige `[A-Za-z0-9_]` en cada segmento). */
private fun trackPath(trackId: String?): String {
    if (trackId.isNullOrBlank()) return MediaInfo.DEFAULT_TRACK_ID
    return "/org/mpris/MediaPlayer2/Track/" + trackId.replace(Regex("[^A-Za-z0-9_]"), "_")
}
