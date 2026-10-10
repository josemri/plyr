package com.plyr.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plyr.database.CreatedPlaylist
import com.plyr.database.PlaylistEntity
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import com.plyr.network.AppArtist
import com.plyr.network.AppPlaylist
import com.plyr.network.AppTrack
import com.plyr.service.YouTubePlaylistCreator
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.Reorder
import com.plyr.ui.components.ReorderState
import com.plyr.ui.utils.isYouTubeVideoId
import com.plyr.utils.Translations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaylistsDependencies(
    val context: Context,
    val localRepository: PlaylistLocalRepository,
    val youtubeSearchManager: YouTubeSearchManager,
    val coroutineScope: CoroutineScope,
    val playerViewModel: PlayerViewModel?,
    val downloadViewModel: DownloadViewModel?,
)

@Stable
class PlaylistViewModel(
    initialPlaylistId: String?,
    val deps: PlaylistsDependencies,
) {
    val context: Context get() = deps.context
    val localRepository: PlaylistLocalRepository get() = deps.localRepository
    val youtubeSearchManager: YouTubeSearchManager get() = deps.youtubeSearchManager
    val coroutineScope: CoroutineScope get() = deps.coroutineScope
    val playerViewModel: PlayerViewModel? get() = deps.playerViewModel
    val downloadViewModel: DownloadViewModel? get() = deps.downloadViewModel

    var isEditing by mutableStateOf(false)
    var showExitEditDialog by mutableStateOf(false)
    var hasUnsavedChanges by mutableStateOf(false)
    var originalTitle by mutableStateOf("")
    var originalDesc by mutableStateOf("")
    var newTitle by mutableStateOf("")
    var newDesc by mutableStateOf("")
    var selectedPlaylist by mutableStateOf<AppPlaylist?>(null)
    var selectedPlaylistEntity by mutableStateOf<PlaylistEntity?>(null)
    var playlistTracks by mutableStateOf<List<AppTrack>>(emptyList())
    var isLoadingTracks by mutableStateOf(false)
    var showCreatePlaylistScreen by mutableStateOf(false)
    var coverPickUri by mutableStateOf<Uri?>(null)
    var pendingPlaylist by mutableStateOf<AppPlaylist?>(null)
    var trackEntities by mutableStateOf<List<TrackEntity>>(emptyList())
    var tracksRevision by mutableIntStateOf(0)
    var downloadedVideoIds by mutableStateOf<Set<String>>(emptySet())
    var showStorageDialog by mutableStateOf(false)
    var pendingInitialPlaylist by mutableStateOf(initialPlaylistId)
    var likedSongsCount by mutableIntStateOf(0)
    var isRandomizing by mutableStateOf(false)
    var showShareDialog by mutableStateOf(false)
    var showDeleteDialog by mutableStateOf(false)
    val openedFromHome = initialPlaylistId != null
    val reorderState = ReorderState()

    fun enterEditMode() {
        originalTitle = selectedPlaylist?.name ?: ""
        originalDesc = selectedPlaylist?.description ?: ""
        newTitle = originalTitle
        newDesc = originalDesc
        hasUnsavedChanges = false
        isEditing = true
    }

    fun saveEdits(onBack: () -> Unit) {
        if (hasUnsavedChanges) {
            val toEdit = selectedPlaylist
            if (toEdit != null) {
                isLoadingTracks = true
                coroutineScope.launch {
                    val success = localRepository.playlists.updatePlaylistDetails(
                        localPlaylistId = toEdit.id,
                        newTitle = if (newTitle != originalTitle) newTitle else null,
                        newDesc = if (newDesc != originalDesc) newDesc else null
                    )
                    isLoadingTracks = false
                    if (success) {
                        isEditing = false
                        hasUnsavedChanges = false
                        selectedPlaylist = null
                        playlistTracks = emptyList()
                        onBack()
                    } else {
                        Log.e("PlaylistScreen", "Error actualizando playlist")
                    }
                }
            } else {
                hasUnsavedChanges = false
                isEditing = false
            }
        } else {
            hasUnsavedChanges = false
            isEditing = false
        }
    }

    fun deletePlaylist(onBack: () -> Unit) {
        val toDelete = selectedPlaylist
        if (toDelete != null) {
            coroutineScope.launch {
                localRepository.playlists.deletePlaylist(toDelete.id)
                isEditing = false
                hasUnsavedChanges = false
                selectedPlaylist = null
                playlistTracks = emptyList()
                onBack()
            }
        }
    }

    fun stopAllPlayback() {
        isRandomizing = false
        playerViewModel?.playback?.cancel()
        playerViewModel?.pausePlayer()
    }

    fun startRandomizing() {
        stopAllPlayback()
        isRandomizing = true
        val pvm = playerViewModel
        if (playlistTracks.isNotEmpty() && pvm != null) {
            pvm.clearPlayerState()
            val shuffledTracks = trackEntities.shuffled()
            val firstTrack = shuffledTracks.first()
            pvm.initializePlayer()
            pvm.setCurrentPlaylist(shuffledTracks, 0)
            pvm.playback.play(firstTrack) {
                isRandomizing = false
            }
        } else {
            isRandomizing = false
        }
    }

    fun reorderTracks(from: Int, to: Int) {
        if (from != to) {
            val reordered = Reorder.move(playlistTracks, from, to)
            playlistTracks = reordered
            val playlistId = selectedPlaylistEntity?.remoteId
            if (playlistId != null) {
                coroutineScope.launch {
                    localRepository.tracks.reorderTracks(
                        localPlaylistId = playlistId,
                        orderedTrackIds = reordered.map { it.id }
                    )
                    tracksRevision++
                }
            }
        }
    }

    fun addTrackToPlaylist(track: AppTrack, onError: (String) -> Unit) {
        val toAdd = selectedPlaylist ?: return
        coroutineScope.launch {
            val success = localRepository.tracks.addTrackToYouTubePlaylist(
                localPlaylistId = toAdd.id,
                track = TrackEntity(
                    id = "",
                    playlistId = toAdd.id,
                    remoteTrackId = track.id,
                    name = track.name,
                    artists = track.getArtistNames(),
                    youtubeVideoId = track.id.takeIf { isYouTubeVideoId(it) },
                    audioUrl = null,
                    position = 0,
                    lastSyncTime = System.currentTimeMillis()
                )
            )
            if (success) {
                tracksRevision++
            } else {
                onError(Translations.get(context, "error_adding_track"))
            }
        }
    }

    fun removeTrackFromPlaylist(track: AppTrack, onError: (String) -> Unit) {
        val toRemove = selectedPlaylist ?: return
        coroutineScope.launch {
            val success = localRepository.tracks.removeTrackFromYouTubePlaylist(
                localPlaylistId = toRemove.id,
                remoteTrackId = track.id
            )
            if (success) {
                tracksRevision++
            } else {
                onError(Translations.get(context, "error_removing_track"))
            }
        }
    }

    fun resetTransientUi() {
        isRandomizing = false
        showShareDialog = false
        showDeleteDialog = false
    }

    fun confirmExitEdit(loadPlaylistTracks: (AppPlaylist) -> Unit) {
        showExitEditDialog = false
        isEditing = false
        hasUnsavedChanges = false
        val pending = pendingPlaylist
        if (pending != null) {
            selectedPlaylist = pending
            loadPlaylistTracks(pending)
            pendingPlaylist = null
        } else {
            selectedPlaylist = null
            playlistTracks = emptyList()
        }
    }
}

@Stable
class CreatePlaylistViewModel(
    val context: Context,
    val localRepository: PlaylistLocalRepository,
    val youtubeSearchManager: YouTubeSearchManager,
    val coroutineScope: CoroutineScope,
    val playerViewModel: PlayerViewModel?,
) {
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var discardWarning by mutableStateOf<Int?>(null)
    var createJob by mutableStateOf<Job?>(null)
    var searchQuery by mutableStateOf("")
    var isSearching by mutableStateOf(false)
    var searchResults by mutableStateOf<List<AppTrack>>(emptyList())
    var selectedTracks by mutableStateOf<List<AppTrack>>(emptyList())

    fun search() {
        if (searchQuery.isNotBlank() && !isSearching) {
            isSearching = true
            error = null
            // Búsqueda de vídeos de YouTube con la integración existente
            coroutineScope.launch {
                val result = try {
                    youtubeSearchManager.searchYouTubeAll(searchQuery, maxVideos = 10, maxPlaylists = 0)
                } catch (e: Exception) {
                    null
                }
                isSearching = false
                if (result != null) {
                    searchResults = result.videos.map { video ->
                        AppTrack(
                            id = video.videoId,
                            name = video.title,
                            artists = listOf(AppArtist(video.uploader))
                        )
                    }
                } else {
                    error = Translations.get(context, "youtube_search_failed")
                }
            }
        }
    }

    fun addTrack(track: AppTrack) {
        if (!selectedTracks.contains(track)) {
            selectedTracks = selectedTracks + track
        }
    }

    fun removeTrack(index: Int) {
        selectedTracks = selectedTracks.filterIndexed { i, _ -> i != index }
    }

    fun createPlaylist(onPlaylistCreated: () -> Unit) {
        // Acción de crear playlist con las canciones seleccionadas
        isLoading = true
        error = null
        discardWarning = null
        // Crear playlist de YouTube usando la integración existente.
        // build es suspend (B16): se puede cancelar, tiene timeout por
        // resolución y reporta cuántas canciones se descartan (B15).
        createJob = coroutineScope.launch {
            val creator = YouTubePlaylistCreator()
            val rawId = "yt_${System.currentTimeMillis()}"
            // Los tracks añadidos vía búsqueda de YouTube (id = videoId) no se re-buscan
            val resolvedVideoIds = selectedTracks
                .filter { isYouTubeVideoId(it.id) }
                .associate { it.id to it.id }
            val created = creator.build(
                title = name,
                description = description.ifBlank { null },
                sourceTracks = creator.buildSourceTracks(selectedTracks),
                targetPlaylistId = "youtube_$rawId",
                resolvedVideoIds = resolvedVideoIds
            )
            val saved = withContext(Dispatchers.IO) {
                localRepository.playlists.saveCreatedYouTubePlaylist(
                    created = CreatedPlaylist(
                        playlistId = rawId,
                        title = created.title,
                        description = created.description,
                        imageUrl = null
                    ),
                    tracks = created.tracks
                )
            }
            isLoading = false
            if (saved) {
                if (created.discardedTracks > 0) {
                    // No se navega sin avisar (B15): el usuario decide
                    // si continuar sabiendo que faltan canciones.
                    discardWarning = created.discardedTracks
                } else {
                    onPlaylistCreated()
                }
            } else {
                error = "${created.tracks.size} tracks (${created.discardedTracks} sin vídeo)"
            }
        }
    }

    fun cancelCreate() {
        createJob?.cancel()
        isLoading = false
    }
}
