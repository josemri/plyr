package com.plyr.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.plyr.database.*
import com.plyr.network.AppPlaylist
import com.plyr.network.AppTrack
import com.plyr.network.AppArtist
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.viewmodel.DownloadViewModel
import com.plyr.viewmodel.PlaylistViewModel
import com.plyr.viewmodel.CreatePlaylistViewModel
import com.plyr.viewmodel.PlaylistsDependencies
import com.plyr.service.YouTubeSearchManager
import com.plyr.service.CoverImageManager
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import com.plyr.ui.components.PlaylistOrigin
import com.plyr.ui.components.PlaylistShare
import com.plyr.ui.components.ShareDialog
import com.plyr.ui.components.ShareableItem
import com.plyr.ui.components.ShareType
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DelicateCoroutinesApi
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.utils.DownloadedAudioStore
import com.plyr.utils.StorageSize
import java.util.Locale
import com.plyr.ui.components.*
import com.plyr.ui.components.ActionButton
import com.plyr.ui.components.ActionButtonData
import com.plyr.ui.components.ActionButtonsGroup
import com.plyr.ui.utils.stableKeys
import com.plyr.ui.utils.youtubeAuthorFromDescription
import com.plyr.ui.utils.isYouTubePlaylistId
import com.plyr.ui.utils.stripYouTubePlaylistId
import com.plyr.ui.utils.isYouTubeVideoId
import com.plyr.ui.utils.buildSearchTrackEntities

private fun youtubeThumbTo16to9(url: String?): String? = UrlParser.normalizeYoutubeThumb(url)

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun PlaylistsScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null,
    initialPlaylistId: String? = null,
    openCreate: Boolean = false,
    onInitialConsumed: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val downloadViewModel = remember { (context.applicationContext as? com.plyr.PlyrApp)?.downloadViewModel }
    val state = rememberPlaylistViewModel(
        context = context,
        initialPlaylistId = initialPlaylistId,
        localRepository = localRepository,
        youtubeSearchManager = youtubeSearchManager,
        coroutineScope = coroutineScope,
        playerViewModel = playerViewModel,
        downloadViewModel = downloadViewModel,
    )

    val currentPlayingTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }
    val playlistsFromDB by localRepository.getAllPlaylistsLiveData().asFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val likedSongsPlaylist by localRepository.getTracksByPlaylistLiveData("liked_songs").asFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val download = rememberPlaylistDownloadValues(downloadViewModel)
    val playlists = PlaylistLocalRepository.visiblePlaylists(playlistsFromDB).map { it.toAppPlaylist() }
    val loadPlaylistTracks: (AppPlaylist) -> Unit = { playlist ->
        state.selectedPlaylist = playlist
        state.selectedPlaylistEntity = playlistsFromDB.find { it.remoteId == playlist.id }
    }

    LikedSongsCountEffect(likedSongsPlaylist, state)
    PlaylistsDownloadResultEffect(downloadViewModel, download.result)
    PlaylistsTrackLoaderEffect(state)
    PlaylistsDownloadedIdsEffect(state, download.revision)
    PlaylistsInitialPlaylistEffect(state, playlistsFromDB, onInitialConsumed, onBack)
    PlaylistsOpenCreateEffect(openCreate, state, onInitialConsumed)
    PlaylistsBackHandler(state, onBack)

    PlaylistsScreenContent(
        state = state,
        playlists = playlists,
        playlistsFromDB = playlistsFromDB,
        currentPlayingTrack = currentPlayingTrack,
        download = download,
        loadPlaylistTracks = loadPlaylistTracks,
        haptic = haptic,
        onBack = onBack,
    )
}

@Composable
private fun rememberPlaylistViewModel(
    context: Context,
    initialPlaylistId: String?,
    localRepository: PlaylistLocalRepository,
    youtubeSearchManager: YouTubeSearchManager,
    coroutineScope: CoroutineScope,
    playerViewModel: PlayerViewModel?,
    downloadViewModel: DownloadViewModel?,
): PlaylistViewModel = remember {
    PlaylistViewModel(
        initialPlaylistId = initialPlaylistId,
        deps = PlaylistsDependencies(
            context = context,
            localRepository = localRepository,
            youtubeSearchManager = youtubeSearchManager,
            coroutineScope = coroutineScope,
            playerViewModel = playerViewModel,
            downloadViewModel = downloadViewModel,
        ),
    )
}

private data class PlaylistDownloadValues(
    val viewModel: DownloadViewModel?,
    val playlistId: String?,
    val isDownloading: Boolean,
    val progress: Float,
    val message: String,
    val result: String?,
    val currentVideoId: String?,
    val currentFraction: Float,
    val revision: Int,
)

@Composable
private fun rememberPlaylistDownloadValues(viewModel: DownloadViewModel?): PlaylistDownloadValues {
    val playlistId by viewModel?.playlistId?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val isDownloading by viewModel?.isDownloading?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val progress by viewModel?.progress?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0f) }
    val message by viewModel?.message?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") }
    val result by viewModel?.resultMessage?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val currentVideoId by viewModel?.currentVideoId?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val currentFraction by viewModel?.currentFraction?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0f) }
    val revision by viewModel?.revision?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0) }
    return PlaylistDownloadValues(
        viewModel = viewModel,
        playlistId = playlistId,
        isDownloading = isDownloading,
        progress = progress,
        message = message,
        result = result,
        currentVideoId = currentVideoId,
        currentFraction = currentFraction,
        revision = revision,
    )
}

@Composable
private fun LikedSongsCountEffect(
    likedSongsPlaylist: List<TrackEntity>,
    state: PlaylistViewModel,
) {
    LaunchedEffect(likedSongsPlaylist) {
        state.likedSongsCount = likedSongsPlaylist.size
    }
}

@Composable
private fun PlaylistsDownloadResultEffect(
    downloadViewModel: DownloadViewModel?,
    downloadResult: String?,
) {
    LaunchedEffect(downloadResult) {
        if (downloadResult != null) {
            kotlinx.coroutines.delay(3000)
            downloadViewModel?.dismissResult()
        }
    }
}

@Composable
private fun PlaylistsTrackLoaderEffect(state: PlaylistViewModel) {
    LaunchedEffect(state.selectedPlaylistEntity?.remoteId, state.tracksRevision) {
        val id = state.selectedPlaylistEntity?.remoteId
        if (id != null) {
            state.isLoadingTracks = true
            val tracks = PlaylistDatabase.getDatabase(state.context).trackDao().getTracksByPlaylistSync(id)
            state.trackEntities = tracks
            state.playlistTracks = tracks.map { it.toAppTrack() }
            state.isLoadingTracks = false
        } else {
            state.trackEntities = emptyList()
            state.playlistTracks = emptyList()
        }
    }
}

@Composable
private fun PlaylistsDownloadedIdsEffect(
    state: PlaylistViewModel,
    downloadRevision: Int,
) {
    LaunchedEffect(state.trackEntities, downloadRevision) {
        state.downloadedVideoIds = withContext(Dispatchers.IO) {
            state.trackEntities.mapNotNull { it.youtubeVideoId }
                .filter { DownloadedAudioStore.localUri(state.context, it) != null }
                .toSet()
        }
    }
}

@Composable
private fun PlaylistsInitialPlaylistEffect(
    state: PlaylistViewModel,
    playlistsFromDB: List<PlaylistEntity>,
    onInitialConsumed: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(state.pendingInitialPlaylist) {
        val pendingId = state.pendingInitialPlaylist
        if (pendingId != null) {
            val entity = playlistsFromDB.find { it.remoteId == pendingId }
                ?: PlaylistDatabase.getDatabase(state.context).playlistDao().getPlaylistById(pendingId)
            state.pendingInitialPlaylist = null
            onInitialConsumed()
            if (entity != null) {
                state.selectedPlaylist = entity.toAppPlaylist()
                state.selectedPlaylistEntity = entity
            } else {
                onBack()
            }
        }
    }
}

@Composable
private fun PlaylistsOpenCreateEffect(
    openCreate: Boolean,
    state: PlaylistViewModel,
    onInitialConsumed: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (openCreate) {
            state.showCreatePlaylistScreen = true
            onInitialConsumed()
        }
    }
}

@Composable
private fun PlaylistsBackHandler(
    state: PlaylistViewModel,
    onBack: () -> Unit,
) {
    BackHandler {
        if (state.isEditing && state.hasUnsavedChanges) {
            state.showExitEditDialog = true
        } else if (state.selectedPlaylist != null) {
            if (state.openedFromHome) {
                onBack()
            } else {
                state.isEditing = false
                state.hasUnsavedChanges = false
                state.selectedPlaylist = null
                state.selectedPlaylistEntity = null
                state.playlistTracks = emptyList()
            }
        } else {
            state.isEditing = false
            state.hasUnsavedChanges = false
            onBack()
        }
    }
}

@Composable
private fun PlaylistsScreenContent(
    state: PlaylistViewModel,
    playlists: List<AppPlaylist>,
    playlistsFromDB: List<PlaylistEntity>,
    currentPlayingTrack: TrackEntity?,
    download: PlaylistDownloadValues,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
    haptic: HapticFeedback,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        //si se pulsa boton de <new> mostrar CreatePlaylistScreen
        if (state.showCreatePlaylistScreen) {
            CreatePlaylistScreen(
                onBack = { state.showCreatePlaylistScreen = false; onBack() },
                onPlaylistCreated = { state.showCreatePlaylistScreen = false; onBack() },
                playerViewModel = state.playerViewModel
            )
            return@Column
        }
        val playlistSel = state.selectedPlaylist
        if (playlistSel != null) {
            PlaylistDetailView(
                state = state,
                playlist = playlistSel,
                currentPlayingTrack = currentPlayingTrack,
                download = download,
                haptic = haptic,
                onBack = onBack,
                loadPlaylistTracks = loadPlaylistTracks
            )
        }

        // Lista de playlists (visible cuando no hay playlist seleccionada ni creando)
        if (state.selectedPlaylist == null && !state.showCreatePlaylistScreen && state.pendingInitialPlaylist == null) {
            PlaylistGridView(
                state = state,
                playlists = playlists,
                playlistsFromDB = playlistsFromDB,
                loadPlaylistTracks = loadPlaylistTracks,
                haptic = haptic
            )
        }
        CoverPickDialog(state)
    }
}

@Composable
private fun PlaylistDetailView(
    state: PlaylistViewModel,
    playlist: AppPlaylist,
    currentPlayingTrack: TrackEntity?,
    download: PlaylistDownloadValues,
    haptic: HapticFeedback,
    onBack: () -> Unit,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
) {
    Titulo(playlist.name)
    Spacer(modifier = Modifier.height(4.dp))

    // Descripción de la playlist entre el título y los botones,
    // pegada a la derecha (estilo WhatsApp "~descripción").
    // Para playlists de YouTube el autor se guarda como "YouTube Playlist by USER"
    // y aquí se muestra solo "USER".
    PlaylistDescription(state, playlist)

    // Vista de tracks de playlist
    if (state.isLoadingTracks) {
        PlaylistLoading(state.context)
    } else {
        PlaylistDetailLoaded(
            state = state,
            currentPlayingTrack = currentPlayingTrack,
            download = download,
            haptic = haptic,
            onBack = onBack,
            loadPlaylistTracks = loadPlaylistTracks
        )
    }
}

@Composable
private fun PlaylistDescription(
    state: PlaylistViewModel,
    playlist: AppPlaylist,
) {
    val rawDescription = state.selectedPlaylistEntity?.description ?: playlist.description
    val channelName = youtubeAuthorFromDescription(state.selectedPlaylistEntity?.description)
    val playlistDescription = (channelName ?: rawDescription)?.takeIf { it.isNotBlank() }
    if (playlistDescription != null) {
        Text(
            text = "~$playlistDescription",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PlaylistLoading(context: Context) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = Translations.get(context, "loading_tracks"),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun PlaylistDetailLoaded(
    state: PlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
    download: PlaylistDownloadValues,
    haptic: HapticFeedback,
    onBack: () -> Unit,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
) {
    // Determinar si la playlist seleccionada es editable (es 'mía')
    // Las playlists de YouTube (prefijo youtube_) también son editables: son locales
    val isYouTubePlaylistView = isYouTubePlaylistId(state.selectedPlaylist?.id)
    val canEdit = state.selectedPlaylistEntity != null && state.selectedPlaylist?.id != "liked_songs"

    // Origen de la lista para compartir (B53). Sin una columna que
    // lo guarde se deduce del id y la description, y si no se
    // reconoce con certeza la lista no ofrece compartir: antes se
    // montaba una URL adivinada que no abría nada.
    val playlistShareOrigin = remember(state.selectedPlaylistEntity) {
        PlaylistShare.classify(
            state.selectedPlaylistEntity?.remoteId,
            state.selectedPlaylistEntity?.description,
            state.selectedPlaylistEntity?.source,
            state.selectedPlaylistEntity?.sourceId,
        )
    }
    val isPlaylistShareable = playlistShareOrigin != PlaylistOrigin.UNKNOWN

    PlaylistDetailBody(
        state = state,
        currentPlayingTrack = currentPlayingTrack,
        download = download,
        haptic = haptic,
        onBack = onBack,
        isPlaylistShareable = isPlaylistShareable,
        canEdit = canEdit,
        isYouTubePlaylistView = isYouTubePlaylistView
    )
    PlaylistDetailDialogs(
        state = state,
        playlistShareOrigin = playlistShareOrigin,
        loadPlaylistTracks = loadPlaylistTracks
    )
    DisposableEffect(Unit) {
        onDispose { state.resetTransientUi() }
    }
}

@Composable
private fun PlaylistDetailBody(
    state: PlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
    download: PlaylistDownloadValues,
    haptic: HapticFeedback,
    onBack: () -> Unit,
    isPlaylistShareable: Boolean,
    canEdit: Boolean,
    isYouTubePlaylistView: Boolean,
) {
    Column {
        // Botones de control
        PlaylistActionButtons(
            state = state,
            download = download,
            isPlaylistShareable = isPlaylistShareable,
            canEdit = canEdit,
            haptic = haptic,
            onBack = onBack
        )

        // Barra de descarga de la lista (como la de import):
        // solo aparece para la lista que se está descargando.
        PlaylistDownloadBar(state, download)

        // Diálogo de gestión del audio offline (tamaño y borrado)
        if (state.showStorageDialog) {
            OfflineStorageDialog(
                context = state.context,
                onDeleteAll = { state.downloadViewModel?.clearDownloads() },
                onDismiss = { state.showStorageDialog = false }
            )
        }

        // Diálogo de confirmación para eliminar playlist
        if (state.showDeleteDialog) {
            PlaylistDeleteDialog(state, onBack)
        }

        if (state.isEditing) {
            PlaylistEditContent(state, isYouTubePlaylistView, currentPlayingTrack)
        } else {
            PlaylistTracksList(state, currentPlayingTrack, download)
        }
    }
}

@Composable
private fun PlaylistDetailDialogs(
    state: PlaylistViewModel,
    playlistShareOrigin: PlaylistOrigin,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
) {
    // Diálogo de confirmación para salir sin guardar
    if (state.showExitEditDialog) {
        ExitEditDialog(state, loadPlaylistTracks)
    }

    // Diálogo de compartir - debe estar dentro del mismo scope que showShareDialog
    if (state.showShareDialog) {
        PlaylistShareDialog(state, playlistShareOrigin)
    }
}

@Composable
private fun PlaylistActionButtons(
    state: PlaylistViewModel,
    download: PlaylistDownloadValues,
    isPlaylistShareable: Boolean,
    canEdit: Boolean,
    haptic: HapticFeedback,
    onBack: () -> Unit,
) {
    val downloadableIds = state.trackEntities
        .mapNotNull { it.youtubeVideoId }
        .filter { it.isNotBlank() }
        .toSet()
    val allDownloaded = downloadableIds.isNotEmpty() &&
        downloadableIds.all { it in state.downloadedVideoIds }

    val buttons = buildList {
        if (!state.isEditing) {
            // Botón rand
            add(playbackRandomButton(state, haptic))

            // Botón share: solo si hay una URL de verdad detrás
            // (B53). Los favoritos y las listas creadas en la
            // app no son de ningún servicio, así que antes
            // producían un QR a una página inexistente.
            if (isPlaylistShareable) {
                add(sharePlaylistButton(state, haptic))
            }

            // Botón único de descarga/almacén: descarga mientras
            // falte audio; gestión y borrado cuando ya está todo.
            // No aparece si no hay nada que descargar ni borrar.
            if (state.selectedPlaylistEntity != null && downloadableIds.isNotEmpty()) {
                add(downloadOrCleanButton(state, download, allDownloaded, haptic))
            }
        }

        // Botón edit/save
        if (canEdit) {
            add(editPlaylistButton(state, haptic, onBack))
        }

        // Botón delete
        if (canEdit && state.isEditing) {
            add(deletePlaylistButton(state, haptic))
        }
    }

    ActionButtonsGroup(
        buttons = buttons,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}

@Composable
private fun playbackRandomButton(
    state: PlaylistViewModel,
    haptic: HapticFeedback,
): ActionButtonData = ActionButtonData(
    text = if (state.isRandomizing) "<stop>" else "<rnd>",
    color = if (state.isRandomizing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    onClick = {
        if (state.isRandomizing) {
            state.stopAllPlayback()
        } else {
            state.startRandomizing()
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
)

@Composable
private fun sharePlaylistButton(
    state: PlaylistViewModel,
    haptic: HapticFeedback,
): ActionButtonData = ActionButtonData(
    text = "<shr>",
    color = MaterialTheme.colorScheme.error,
    onClick = {
        state.showShareDialog = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
)

@Composable
private fun downloadOrCleanButton(
    state: PlaylistViewModel,
    download: PlaylistDownloadValues,
    allDownloaded: Boolean,
    haptic: HapticFeedback,
): ActionButtonData {
    val downloadingThis = download.isDownloading && download.playlistId == state.selectedPlaylist?.id
    return ActionButtonData(
        text = when {
            downloadingThis -> "<stop>"
            allDownloaded -> "<clr>"
            else -> "<dwn>"
        },
        color = when {
            downloadingThis -> MaterialTheme.colorScheme.error
            allDownloaded -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.tertiary
        },
        onClick = {
            when {
                downloadingThis -> download.viewModel?.cancel()
                allDownloaded -> state.showStorageDialog = true
                else -> download.viewModel?.startDownload(
                    playlistId = state.selectedPlaylist?.id.orEmpty(),
                    playlistTitle = state.selectedPlaylist?.name.orEmpty(),
                    tracks = state.trackEntities
                )
            }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    )
}

@Composable
private fun editPlaylistButton(
    state: PlaylistViewModel,
    haptic: HapticFeedback,
    onBack: () -> Unit,
): ActionButtonData = ActionButtonData(
    text = if (state.isEditing) "<save>" else "<edt>",
    color = if (state.isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    onClick = {
        if (state.isEditing) {
            state.saveEdits(onBack)
        } else {
            state.enterEditMode()
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
)

@Composable
private fun deletePlaylistButton(
    state: PlaylistViewModel,
    haptic: HapticFeedback,
): ActionButtonData = ActionButtonData(
    text = "<delete>",
    color = MaterialTheme.colorScheme.error,
    onClick = {
        state.showDeleteDialog = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
)

@Composable
private fun PlaylistDownloadBar(
    state: PlaylistViewModel,
    download: PlaylistDownloadValues,
) {
    if (download.playlistId == state.selectedPlaylist?.id && (download.isDownloading || download.result != null)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(
                text = if (download.isDownloading) download.message else download.result.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = if (download.isDownloading) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                )
            )
            if (download.isDownloading) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PlaylistDeleteDialog(
    state: PlaylistViewModel,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { state.showDeleteDialog = false },
        title = {
            Text(
                Translations.get(state.context, "delete_playlist_title"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        },
        text = {
            Text(
                String.format(
                    Locale.ROOT,
                    Translations.get(state.context, "delete_playlist_message"),
                    state.selectedPlaylist?.name ?: ""
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.showDeleteDialog = false
                    state.deletePlaylist(onBack)
                }
            ) {
                Text(
                    Translations.get(state.context, "delete"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.error
                    )
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { state.showDeleteDialog = false }
            ) {
                Text(
                    Translations.get(state.context, "cancel"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    )
}

@Stable
private class PlaylistEditSearchState {
    var query by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var results by mutableStateOf<List<AppTrack>>(emptyList())
    var error by mutableStateOf<String?>(null)
}

@Composable
private fun ColumnScope.PlaylistEditContent(
    state: PlaylistViewModel,
    isYouTubePlaylistView: Boolean,
    currentPlayingTrack: TrackEntity?,
) {
    val context = state.context
    // Estados para el buscador de canciones en edición
    val search = remember { PlaylistEditSearchState() }

    val coverImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) state.coverPickUri = uri
    }

    // Detectar cambios en los campos
    LaunchedEffect(state.newTitle, state.newDesc) {
        state.hasUnsavedChanges = (state.newTitle != state.originalTitle || state.newDesc != state.originalDesc)
    }

    // Usar LazyColumn para permitir scroll
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        playlistEditFields(
            state = state,
            isYouTubePlaylistView = isYouTubePlaylistView,
            onPickCover = {
                coverImagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )

        // Campo de búsqueda
        item {
            PlaylistEditSearchField(context, state, search)
        }

        // Mostrar indicador de búsqueda
        playlistEditSearchingIndicator(search)

        // Resultados de búsqueda usando SongListItem
        playlistEditSearchResults(state, search, currentPlayingTrack)

        // Mostrar error si hay
        playlistEditErrorMessage(search, context)

        item {
            Spacer(Modifier.height(16.dp))
        }

        // Lista de canciones actuales usando SongListItem
        playlistEditCurrentTracks(state, currentPlayingTrack, search)
    }
}

// Nombre y descripción, con la portada a la izquierda
// (la propia portada es el botón para cambiarla)
private fun LazyListScope.playlistEditFields(
    state: PlaylistViewModel,
    isYouTubePlaylistView: Boolean,
    onPickCover: () -> Unit,
) {
    if (isYouTubePlaylistView) {
        item {
            PlaylistEditCoverAndFields(
                state = state,
                onPickCover = onPickCover
            )
        }
    } else {
        item {
            PlaylistEditTitleField(state)
        }
        item {
            PlaylistEditDescField(state)
        }
    }
}

private fun LazyListScope.playlistEditSearchingIndicator(search: PlaylistEditSearchState) {
    if (search.isSearching) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$ searching...",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.tertiary
                )
            )
        }
    }
}

@Composable
private fun PlaylistEditCoverAndFields(
    state: PlaylistViewModel,
    onPickCover: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = youtubeThumbTo16to9(state.selectedPlaylistEntity?.imageUrl),
            contentDescription = "Portada actual (pulsar para cambiar)",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onPickCover() },
            contentScale = ContentScale.Crop,
            placeholder = null,
            error = null,
            fallback = null
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = state.newTitle,
                onValueChange = { state.newTitle = it },
                label = { Text(Translations.get(state.context, "playlist_name")) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.newDesc,
                onValueChange = { state.newDesc = it },
                label = { Text(Translations.get(state.context, "description")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PlaylistEditTitleField(state: PlaylistViewModel) {
    OutlinedTextField(
        value = state.newTitle,
        onValueChange = { state.newTitle = it },
        label = { Text(Translations.get(state.context, "playlist_name")) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PlaylistEditDescField(state: PlaylistViewModel) {
    OutlinedTextField(
        value = state.newDesc,
        onValueChange = { state.newDesc = it },
        label = { Text(Translations.get(state.context, "description")) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun PlaylistEditSearchField(
    context: Context,
    state: PlaylistViewModel,
    search: PlaylistEditSearchState,
) {
    OutlinedTextField(
        value = search.query,
        onValueChange = { search.query = it },
        label = { Text(Translations.get(context, "search_tracks_label")) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (search.query.isNotEmpty()) {
                IconButton(onClick = { search.query = "" }) {
                    Text(
                        text = "x",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                if (search.query.isNotBlank() && !search.isSearching) {
                    search.isSearching = true
                    // Búsqueda de vídeos de YouTube con la integración existente
                    state.coroutineScope.launch {
                        val result = try {
                            state.youtubeSearchManager.searchYouTubeAll(search.query, maxVideos = 10, maxPlaylists = 0)
                        } catch (e: Exception) {
                            Log.e("PlaylistScreen", "Error buscando en YouTube: ${e.message}")
                            null
                        }
                        search.isSearching = false
                        if (result != null) {
                            search.results = result.videos.map { video ->
                                AppTrack(
                                    id = video.videoId,
                                    name = video.title,
                                    artists = listOf(AppArtist(video.uploader))
                                )
                            }
                        } else {
                            search.error = Translations.get(context, "youtube_search_failed")
                        }
                    }
                }
            }
        ),
        enabled = !search.isSearching
    )
}

private fun LazyListScope.playlistEditSearchResults(
    state: PlaylistViewModel,
    search: PlaylistEditSearchState,
    currentPlayingTrack: TrackEntity?,
) {
    if (search.results.isEmpty()) return
    item {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "results:",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
    }

    // Crear trackEntities para los resultados de búsqueda
    val searchTrackEntities = buildSearchTrackEntities(
        tracks = search.results,
        idPrefix = "edit_search",
        timestamp = System.currentTimeMillis(),
    )

    val searchItemKeys = stableKeys(search.results.take(10).map { it.id })
    items(search.results.take(10).size, key = { index -> searchItemKeys[index] }) { index ->
        val track = search.results[index]
        val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
        SongListItem(
            song = Song(
                number = index + 1,
                title = track.name,
                artist = track.getArtistNames(),
                remoteId = track.id,
                youtubeId = track.id.takeIf { isYouTubeVideoId(it) },
                shareUrl = "https://www.youtube.com/watch?v=${track.id}"
            ),
            trackEntities = searchTrackEntities,
            index = index,
            playerViewModel = state.playerViewModel,
            coroutineScope = state.coroutineScope,
            isCurrentlyPlaying = isPlaying,
            customButtonIcon = "+",
            customButtonAction = {
                // Añadir track a la playlist de YouTube local (videoId ya resuelto)
                state.addTrackToPlaylist(track) { message -> search.error = message }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun LazyListScope.playlistEditErrorMessage(
    search: PlaylistEditSearchState,
    context: Context,
) {
    search.error?.let {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "${Translations.get(context, "error_prefix")}$it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

private fun LazyListScope.playlistEditCurrentTracks(
    state: PlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
    search: PlaylistEditSearchState,
) {
    if (state.playlistTracks.isEmpty()) return
    item {
        Text(
            text = "current tracks [${state.playlistTracks.size}]:",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.height(8.dp))
    }

    val playlistItemKeys = stableKeys(state.playlistTracks.map { it.id })
    items(state.playlistTracks.size, key = { index -> playlistItemKeys[index] }) { index ->
        val track = state.playlistTracks[index]
        val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
        SongListItem(
            song = Song(
                number = index + 1,
                title = track.name,
                artist = track.getArtistNames(),
                remoteId = track.id,
                youtubeId = track.youtubeVideoId,
                shareUrl = null
            ),
            trackEntities = state.trackEntities,
            index = index,
            playerViewModel = state.playerViewModel,
            coroutineScope = state.coroutineScope,
            isCurrentlyPlaying = isPlaying,
            customButtonIcon = "x",
            customButtonAction = {
                // Eliminar track de la playlist de YouTube local
                state.removeTrackFromPlaylist(track) { message -> search.error = message }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// Lista de tracks (solo visible cuando NO está en modo edición)
@Composable
private fun PlaylistTracksList(
    state: PlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
    download: PlaylistDownloadValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Prepara trackEntities - si no hay en DB, crear temporales
        val trackEntitiesList = state.trackEntities.ifEmpty {
            // Crear TrackEntities temporales para álbumes u otras fuentes sin BD
            state.playlistTracks.mapIndexed { trackIndex, track ->
                TrackEntity(
                    id = "temp_${state.selectedPlaylist?.id}_${track.id}",
                    playlistId = state.selectedPlaylist?.id ?: "unknown",
                    remoteTrackId = track.id,
                    name = track.name,
                    artists = track.getArtistNames(),
                    youtubeVideoId = null,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
        }

        val playlistItemKeys = stableKeys(state.playlistTracks.map { it.id })
        items(state.playlistTracks.size, key = { index -> playlistItemKeys[index] }) { index ->
            PlaylistTrackRow(
                state = state,
                currentPlayingTrack = currentPlayingTrack,
                download = download,
                trackEntitiesList = trackEntitiesList,
                index = index
            )
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    state: PlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
    download: PlaylistDownloadValues,
    trackEntitiesList: List<TrackEntity>,
    index: Int,
) {
    val track = state.playlistTracks[index]
    val song = Song(
        number = index + 1,
        title = track.name,
        artist = track.getArtistNames(),
        remoteId = track.id,
        youtubeId = track.youtubeVideoId,
        shareUrl = null
    )
    val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
    val videoId = track.youtubeVideoId
    val isDownloaded = videoId != null && videoId in state.downloadedVideoIds
    val trackProgress = if (download.isDownloading && videoId != null && videoId == download.currentVideoId) {
        download.currentFraction
    } else null
    Box(
        modifier = state.reorderState.itemModifier(
            id = track.id,
            index = index,
            itemCount = state.playlistTracks.size,
            onDrop = state::reorderTracks
        )
    ) {
        SongListItem(
            song = song,
            trackEntities = trackEntitiesList,
            index = index,
            playerViewModel = state.playerViewModel,
            coroutineScope = state.coroutineScope,
            modifier = Modifier.fillMaxWidth(),
            isCurrentlyPlaying = isPlaying,
            onLikedStatusChanged = { state.tracksRevision++ },
            isDownloaded = isDownloaded,
            downloadProgress = trackProgress,
            onDeleteDownload = if (isDownloaded && videoId != null) {
                { download.viewModel?.removeDownload(videoId) }
            } else null
        )
    }
}

@Composable
private fun ExitEditDialog(
    state: PlaylistViewModel,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            state.showExitEditDialog = false
            state.pendingPlaylist = null
        },
        title = {
            Text(
                Translations.get(state.context, "unsaved_changes_title"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        },
        text = {
            Text(
                Translations.get(state.context, "unsaved_changes_message"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        },
        confirmButton = { ExitEditConfirmButton(state, loadPlaylistTracks) },
        dismissButton = { ExitEditDismissButton(state) }
    )
}

@Composable
private fun ExitEditConfirmButton(
    state: PlaylistViewModel,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
) {
    TextButton(onClick = { state.confirmExitEdit(loadPlaylistTracks) }) {
        Text(
            Translations.get(state.context, "exit"),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.error
            )
        )
    }
}

@Composable
private fun ExitEditDismissButton(state: PlaylistViewModel) {
    TextButton(
        onClick = {
            state.showExitEditDialog = false
            state.pendingPlaylist = null
        }
    ) {
        Text(
            Translations.get(state.context, "cancel"),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun PlaylistShareDialog(
    state: PlaylistViewModel,
    playlistShareOrigin: PlaylistOrigin,
) {
    val toShare = state.selectedPlaylist ?: return
    ShareDialog(
        item = ShareableItem(
            remoteId = toShare.id,
            shareUrl = null,
            youtubeId = run {
                val ent = state.selectedPlaylistEntity
                when (ent?.source) {
                    PlaylistSource.SPOTIFY -> ent.sourceId ?: stripYouTubePlaylistId(toShare.id)
                    else -> stripYouTubePlaylistId(toShare.id)
                }
            },
            title = toShare.name,
            artist = "Playlist",
            type = ShareType.PLAYLIST,
            playlistOrigin = playlistShareOrigin,
        ),
        onDismiss = { state.showShareDialog = false }
    )
}

@Composable
private fun CoverPickDialog(state: PlaylistViewModel) {
    val uri = state.coverPickUri ?: return
    val entity = state.selectedPlaylistEntity
    if (entity != null && state.isEditing && isYouTubePlaylistId(entity.remoteId)) {
        CoverCropDialog(
            uri = uri,
            onDismiss = { state.coverPickUri = null },
            onConfirm = { cropped ->
                state.coverPickUri = null
                state.coroutineScope.launch {
                    val path = CoverImageManager.save(
                        state.context,
                        stripYouTubePlaylistId(entity.remoteId),
                        cropped
                    )
                    if (path != null) {
                        state.localRepository.playlists.updatePlaylistImage(entity.remoteId, path)
                        // Refrescar la entidad para que la preview en modo edición se actualice
                        state.selectedPlaylistEntity = entity.copy(imageUrl = path)
                    }
                }
            }
        )
    }
}

@Composable
private fun PlaylistGridView(
    state: PlaylistViewModel,
    playlists: List<AppPlaylist>,
    playlistsFromDB: List<PlaylistEntity>,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
    haptic: HapticFeedback,
) {
    Titulo(
        titulo = Translations.get(state.context, "plyr_lists"),
        trailing = {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    state.showCreatePlaylistScreen = true
                }.padding(start = 8.dp)
            )
        }
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (playlists.isEmpty()) {
        PlaylistEmptyState(state.context)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val playlistIdsKeys = stableKeys(playlists.map { it.id })
            items(playlists.size, key = { index -> playlistIdsKeys[index] }) { index ->
                PlaylistGridItem(
                    playlist = playlists[index],
                    playlistsFromDB = playlistsFromDB,
                    loadPlaylistTracks = loadPlaylistTracks,
                    haptic = haptic
                )
            }
        }
    }
}

@Composable
private fun PlaylistEmptyState(context: Context) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = Translations.get(context, "no_playlists"),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun PlaylistGridItem(
    playlist: AppPlaylist,
    playlistsFromDB: List<PlaylistEntity>,
    loadPlaylistTracks: (AppPlaylist) -> Unit,
    haptic: HapticFeedback,
) {
    val isLiked = playlist.id == "liked_songs"
    val playlistEntity = playlistsFromDB.find { it.remoteId == playlist.id }
    val channelName = youtubeAuthorFromDescription(playlistEntity?.description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                loadPlaylistTracks(playlist)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLiked) {
            PlaylistLikedCover()
        } else {
            AsyncImage(
                model = youtubeThumbTo16to9(playlistEntity?.imageUrl),
                contentDescription = "Portada de ${playlist.name}",
                modifier = Modifier
                    .size(150.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = null,
                error = null,
                fallback = null
            )
        }
        PlaylistGridItemLabel(playlist.name, isLiked, channelName)
    }
}

@Composable
private fun PlaylistLikedCover() {
    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "♥",
            fontSize = 48.sp,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PlaylistGridItemLabel(
    name: String,
    isLiked: Boolean,
    channelName: String?,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.padding(top = 8.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
    if (channelName != null) {
        Text(
            text = channelName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Diálogo de gestión del audio offline: muestra cuántas pistas y cuánto ocupan
 * y permite borrarlo todo. Reconsulta el almacén al abrirse y tras borrar.
 */
@Composable
private fun OfflineStorageDialog(
    context: Context,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var refresh by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<DownloadedAudioStore.Summary?>(null) }
    LaunchedEffect(refresh) {
        summary = withContext(Dispatchers.IO) { DownloadedAudioStore.summary(context) }
    }
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Translations.get(context, "storage_title"),
                style = mono.copy(color = MaterialTheme.colorScheme.primary)
            )
        },
        text = { Text(text = offlineStorageMessage(context, summary), style = mono) },
        confirmButton = {
            val current = summary
            if (current != null && current.count > 0) {
                TextButton(onClick = {
                    onDeleteAll()
                    refresh++
                }) {
                    Text(
                        text = Translations.get(context, "delete"),
                        style = mono.copy(color = MaterialTheme.colorScheme.error)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = Translations.get(context, "close"),
                    style = mono.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
        }
    )
}

private fun offlineStorageMessage(context: Context, summary: DownloadedAudioStore.Summary?): String = when {
    summary == null -> Translations.get(context, "loading")
    summary.count == 0 -> Translations.get(context, "storage_empty")
    else -> String.format(
        Locale.ROOT,
        Translations.get(context, "storage_message"),
        summary.count,
        StorageSize.format(summary.bytes)
    )
}

@Stable

@Composable
fun CreatePlaylistScreen(
    onBack: () -> Unit,
    onPlaylistCreated: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val state = remember {
        CreatePlaylistViewModel(
            context = context,
            localRepository = localRepository,
            youtubeSearchManager = youtubeSearchManager,
            coroutineScope = coroutineScope,
            playerViewModel = playerViewModel,
        )
    }

    // Observar el track actual para actualización reactiva del indicador de reproducción
    val currentPlayingTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }

    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Titulo(Translations.get(context, "create_playlist"))
        Spacer(Modifier.height(16.dp))
        CreatePlaylistForm(state)
        CreatePlaylistSearchSection(state, currentPlayingTrack)
        CreatePlaylistSelectedSection(state, currentPlayingTrack)
        CreatePlaylistActions(state, onPlaylistCreated)
    }
}

@Composable
private fun CreatePlaylistForm(state: CreatePlaylistViewModel) {
    val context = state.context
    OutlinedTextField(
        value = state.name,
        onValueChange = { state.name = it },
        label = { Text(Translations.get(context, "playlist_name")) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.description,
        onValueChange = { state.description = it },
        label = { Text(Translations.get(context, "description_optional")) },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    // Campo de búsqueda
    CreatePlaylistSearchField(state)
}

@Composable
private fun CreatePlaylistSearchField(state: CreatePlaylistViewModel) {
    val context = state.context
    OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { state.searchQuery = it },
        label = { Text(Translations.get(context, "search_tracks_label")) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (state.searchQuery.isNotEmpty()) {
                IconButton(onClick = { state.searchQuery = "" }) {
                    Text(
                        text = "x",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { state.search() }),
        enabled = !state.isSearching
    )
}

@Composable
private fun CreatePlaylistSearchSection(
    state: CreatePlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
) {
    // Mostrar indicador de búsqueda
    if (state.isSearching) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$ searching...",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary
            )
        )
    }

    // Resultados de búsqueda
    if (state.searchResults.isNotEmpty()) {
        CreatePlaylistSearchResults(state, currentPlayingTrack)
    }
}

@Composable
private fun CreatePlaylistSearchResults(
    state: CreatePlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
) {
    val trackEntities = buildSearchTrackEntities(
        tracks = state.searchResults,
        idPrefix = "yt_search",
        timestamp = System.currentTimeMillis(),
    )

    state.searchResults.take(10).forEachIndexed { index, track ->
        val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
        SongListItem(
            song = Song(
                number = index + 1,
                title = track.name,
                artist = track.getArtistNames(),
                youtubeId = track.id,
                shareUrl = "https://www.youtube.com/watch?v=${track.id}"
            ),
            trackEntities = trackEntities,
            index = index,
            playerViewModel = state.playerViewModel,
            coroutineScope = state.coroutineScope,
            isCurrentlyPlaying = isPlaying,
            isSelected = state.selectedTracks.contains(track),
            customButtonIcon = "+",
            customButtonAction = {
                state.addTrack(track)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CreatePlaylistSelectedSection(
    state: CreatePlaylistViewModel,
    currentPlayingTrack: TrackEntity?,
) {
    // Lista de canciones seleccionadas
    if (state.selectedTracks.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Text(
        text = "selected [${state.selectedTracks.size}]:",
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
    )
    val tracksEntities = buildSearchTrackEntities(
        tracks = state.selectedTracks,
        idPrefix = "yt_search",
        timestamp = System.currentTimeMillis(),
    )

    state.selectedTracks.forEachIndexed { index, track ->
        val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
        SongListItem(
            song = Song(
                number = index + 1,
                title = track.name,
                artist = track.getArtistNames(),
                youtubeId = track.id,
                shareUrl = "https://www.youtube.com/watch?v=${track.id}"
            ),
            trackEntities = tracksEntities,
            index = index,
            playerViewModel = state.playerViewModel,
            coroutineScope = state.coroutineScope,
            isCurrentlyPlaying = isPlaying,
            isSelected = true,
            customButtonIcon = "x",
            customButtonAction = {
                state.removeTrack(index)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CreatePlaylistActions(
    state: CreatePlaylistViewModel,
    onPlaylistCreated: () -> Unit,
) {
    val context = state.context
    Spacer(Modifier.height(16.dp))
    ActionButton(
        data = ActionButtonData(
            text = if (state.isLoading) "<creating...>" else "<create>",
            color = if (state.isLoading) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            enabled = !state.isLoading && state.name.isNotBlank(),
            onClick = { state.createPlaylist(onPlaylistCreated) }
        )
    )
    state.error?.let {
        Spacer(Modifier.height(8.dp))
        Text("${Translations.get(context, "error_prefix")}$it", color = MaterialTheme.colorScheme.error)
    }
    state.discardWarning?.let { discarded ->
        CreatePlaylistDiscardWarning(state, onPlaylistCreated, discarded)
    }
    if (state.isLoading) {
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { state.cancelCreate() }
        ) {
            Text(Translations.get(context, "cancel"))
        }
    }
}

@Composable
private fun CreatePlaylistDiscardWarning(
    state: CreatePlaylistViewModel,
    onPlaylistCreated: () -> Unit,
    discarded: Int,
) {
    val context = state.context
    Spacer(Modifier.height(8.dp))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            String.format(
                Locale.ROOT,
                Translations.get(context, "create_playlist_discarded"),
                state.selectedTracks.size - discarded,
                state.selectedTracks.size,
                discarded
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        TextButton(onClick = {
            state.discardWarning = null
            onPlaylistCreated()
        }) {
            Text(Translations.get(context, "continue"))
        }
    }
}
