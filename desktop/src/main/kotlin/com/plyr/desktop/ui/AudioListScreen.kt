package com.plyr.desktop.ui

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
import androidx.compose.ui.unit.dp
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.player.PlayerController
import com.plyr.desktop.player.PlayerState
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.player.VlcAudioPlayer
import com.plyr.desktop.player.YouTubeTrackSourceResolver
import com.plyr.desktop.search.SearchController
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.desktop.database.SettingsTombstoneStore
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.spotify.SpotifyImportController
import com.plyr.desktop.spotify.SpotifyImportService
import com.plyr.desktop.sync.BackupConfig
import com.plyr.desktop.sync.BackupController
import com.plyr.desktop.sync.DataExporter
import com.plyr.desktop.sync.DataImporter
import com.plyr.desktop.sync.DataSync
import com.plyr.desktop.ui.screens.ConfigScreen
import com.plyr.desktop.ui.screens.HomeScreen
import com.plyr.desktop.ui.screens.PlaceholderScreen
import com.plyr.desktop.ui.screens.QueueScreen
import com.plyr.desktop.ui.screens.SearchScreen
import com.plyr.desktop.utils.CoverCache
import com.plyr.network.YouTubeManager
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
) {
    var currentScreen by remember { mutableStateOf(initialScreen) }
    val goHome = { currentScreen = Screen.HOME }
    val scope = rememberCoroutineScope()

    val playerController = remember(scope) {
        PlayerController(
            scope = scope,
            openPlayer = { VlcAudioPlayer() },
            sourceResolver = YouTubeTrackSourceResolver(),
            initialRepeatMode = DesktopConfig.repeatMode,
            onRepeatModeChanged = { DesktopConfig.repeatMode = it },
        )
    }
    val searchController = remember(scope) {
        SearchController(
            scope = scope,
            search = { query, maxResults ->
                YouTubeManager.searchVideos(query, maxResults)
                    .mapIndexed { index, item -> PlayerTrack.fromSearchResult(index, item) }
            },
        )
    }

    DisposableEffect(playerController) {
        onDispose { playerController.release() }
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
    val playerState by playerController.state.collectAsState()
    val searchState by searchController.state.collectAsState()

    // Refresca posición/duración solo mientras suena: con la reproducción parada
    // no hay bucle y la composición queda ociosa (los tests no se cuelgan).
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            playerController.refreshProgress()
            delay(PROGRESS_POLL_MS)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = CONTROLS_RESERVED_HEIGHT),
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(onNavigate = { currentScreen = it })
                    Screen.SEARCH -> SearchScreen(
                        state = searchState,
                        onQueryChange = searchController::onQueryChange,
                        onSearch = { searchController.performSearch(it) },
                        onPlayResult = { index ->
                            if (index in searchState.results.indices) {
                                playerController.play(searchState.results, index)
                            }
                        },
                        onBack = goHome,
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
                    )
                    Screen.QUEUE -> QueueScreen(
                        state = playerState,
                        onJump = playerController::jumpTo,
                        onMove = playerController::moveQueueItem,
                        onBack = goHome,
                    )
                    Screen.PLAYLISTS -> {
                        val playlistsScope = rememberCoroutineScope()
                        val playlistsController = remember(playlistsScope, repository) {
                            com.plyr.desktop.playlists.PlaylistsController(playlistsScope, repository)
                        }
                        com.plyr.desktop.playlists.PlaylistsScreen(
                            controller = playlistsController,
                            onBack = goHome,
                            onPlayTracks = { tracks: List<com.plyr.database.TrackRecord>, index: Int ->
                                playerController.play(
                                    tracks.mapIndexed { i: Int, t: com.plyr.database.TrackRecord -> PlayerTrack.fromTrackRecord(i, t) },
                                    index
                                )
                                currentScreen = Screen.QUEUE
                            }
                        )
                    }
                    Screen.FEED -> PlaceholderScreen(Screen.FEED, onBack = goHome)
                    Screen.CONFIG -> ConfigScreen(
                        onBack = goHome,
                        onRepeatModeSelected = playerController::setRepeatMode,
                        backupController = backupController,
                        spotifyImportController = spotifyController,
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
}

/** Traduce el estado del reproductor al que pinta la barra flotante. */
private fun PlayerState.toUiState() = PlayerUiState(
    title = title,
    isLoading = isLoading,
    error = error,
    isPlaying = isPlaying,
    positionMs = positionMs,
    durationMs = durationMs,
    queueIndex = currentIndex.takeIf { it >= 0 },
    queueSize = queue.size.takeIf { it > 0 },
    repeatMode = repeatMode,
)
