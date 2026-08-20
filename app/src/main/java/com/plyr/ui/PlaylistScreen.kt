package com.plyr.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import android.net.Uri
import coil.compose.AsyncImage
import com.plyr.database.*
import com.plyr.network.AppPlaylist
import com.plyr.network.AppTrack
import com.plyr.network.AppArtist
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.service.YouTubeSearchManager
import com.plyr.service.YouTubePlaylistCreator
import com.plyr.service.CoverImageManager
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import com.plyr.ui.components.ShareDialog
import com.plyr.ui.components.ShareableItem
import com.plyr.ui.components.ShareType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.DelicateCoroutinesApi
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.ui.components.*
import com.plyr.ui.components.ActionButton
import com.plyr.ui.components.ActionButtonData
import com.plyr.ui.components.ActionButtonsGroup

private fun getYouTubeChannelName(playlistEntity: PlaylistEntity?): String? {
    val description = playlistEntity?.description ?: return null
    return if (description.startsWith("YouTube Playlist by ")) {
        description.removePrefix("YouTube Playlist by ").takeIf { it.isNotBlank() }
    } else null
}

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

    // Repositorio local y manager de búsqueda
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Observar el track actual para actualización reactiva del indicador de reproducción
    val currentPlayingTrack by playerViewModel?.currentTrack?.observeAsState() ?: remember { mutableStateOf(null) }

    // Estado para las playlists y autenticación
    val playlistsFromDB by localRepository.getAllPlaylistsLiveData().asFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var isLoading by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Estado para Liked Songs - ahora desde DB
    val likedSongsPlaylist by localRepository.getTracksByPlaylistLiveData("liked_songs")
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var likedSongsCount by remember { mutableIntStateOf(0) }

    // Actualizar contador de Liked Songs
    LaunchedEffect(likedSongsPlaylist) {
        likedSongsCount = likedSongsPlaylist.size
    }

    // Estados para detectar cambios en modo edición (movidos aquí para ser accesibles globalmente)
    var showExitEditDialog by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var originalTitle by remember { mutableStateOf("") }
    var originalDesc by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    // Convertir entidades a AppPlaylist para compatibilidad con UI existente
    // Filtrar liked_songs y álbumes para que no aparezcan duplicados (se muestran como items especiales)
    val playlists = playlistsFromDB
        .filter { it.remoteId != "liked_songs" && !it.remoteId.startsWith("album_") }
        .map { it.toAppPlaylist() }

    // Estado para mostrar tracks de una playlist
    var selectedPlaylist by remember { mutableStateOf<AppPlaylist?>(null) }
    var selectedPlaylistEntity by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistTracks by remember { mutableStateOf<List<AppTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    var showCreatePlaylistScreen by remember { mutableStateOf(false) }

    // Estado y lanzador para cambiar la portada de una playlist (solo locales youtube_)
    var coverPickUri by remember { mutableStateOf<Uri?>(null) }
    val coverImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) coverPickUri = uri
    }

    // Estado para manejar navegación pendiente cuando hay cambios sin guardar
    var pendingPlaylist by remember { mutableStateOf<AppPlaylist?>(null) }

    // Cargar tracks reactivamente cuando cambia la playlist seleccionada
    var trackEntities by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    LaunchedEffect(selectedPlaylistEntity?.remoteId) {
        val id = selectedPlaylistEntity?.remoteId
        if (id != null) {
            isLoadingTracks = true
            val tracks = com.plyr.database.PlaylistDatabase.getDatabase(context).trackDao().getTracksByPlaylistSync(id)
            trackEntities = tracks
            playlistTracks = tracks.map { it.toAppTrack() }
            isLoadingTracks = false
        } else {
            trackEntities = emptyList()
            playlistTracks = emptyList()
        }
    }

    val loadPlaylists = { }

    val loadPlaylistTracks: (AppPlaylist) -> Unit = { playlist ->
        selectedPlaylist = playlist
        selectedPlaylistEntity = playlistsFromDB.find { it.remoteId == playlist.id }
    }

    // Abrir una playlist directamente desde el Home (carrusel)
    var pendingInitialPlaylist by remember { mutableStateOf(initialPlaylistId) }
    val openedFromHome = remember { initialPlaylistId != null }
    LaunchedEffect(pendingInitialPlaylist) {
        val pendingId = pendingInitialPlaylist
        if (pendingId != null) {
            val entity = playlistsFromDB.find { it.remoteId == pendingId }
                ?: com.plyr.database.PlaylistDatabase.getDatabase(context).playlistDao().getPlaylistById(pendingId)
            pendingInitialPlaylist = null
            onInitialConsumed()
            if (entity != null) {
                selectedPlaylist = entity.toAppPlaylist()
                selectedPlaylistEntity = entity
            } else {
                onBack()
            }
        }
    }

    // Abrir la pantalla de crear playlist desde el Home si se pidió
    LaunchedEffect(Unit) {
        if (openCreate) {
            showCreatePlaylistScreen = true
            onInitialConsumed()
        }
    }

    val loadLikedSongs: () -> Unit = { }


    // Cleanup del YouTubeSearchManager
    DisposableEffect(Unit) {
        onDispose {
            youtubeSearchManager.cleanup()
        }
    }

    // Manejar botón de retroceso del sistema
    BackHandler {
        if (isEditing && hasUnsavedChanges) {
            showExitEditDialog = true
        } else if (selectedPlaylist != null) {
            if (openedFromHome) {
                onBack()
            } else {
                isEditing = false
                hasUnsavedChanges = false
                selectedPlaylist = null
                selectedPlaylistEntity = null
                playlistTracks = emptyList()
            }
        } else {
            isEditing = false
            hasUnsavedChanges = false
            onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        //si se pulsa boton de <new> mostrar CreatePlaylistScreen
        if (showCreatePlaylistScreen) {
            CreatePlaylistScreen(
                onBack = { showCreatePlaylistScreen = false; onBack() },
                onPlaylistCreated = { showCreatePlaylistScreen = false; onBack() },
                playerViewModel = playerViewModel
            )
            return@Column
        }
        if (selectedPlaylist != null) {
            Titulo(selectedPlaylist!!.name)
            Spacer(modifier = Modifier.height(4.dp))

            // Descripción de la playlist entre el título y los botones,
            // pegada a la derecha (estilo WhatsApp "~descripción").
            // Para playlists de YouTube el autor se guarda como "YouTube Playlist by USER"
            // y aquí se muestra solo "USER".
            val rawDescription = selectedPlaylistEntity?.description ?: selectedPlaylist?.description
            val channelName = getYouTubeChannelName(selectedPlaylistEntity)
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

                // Vista de tracks de playlist
                if (isLoadingTracks) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Translations.get(context, "Loading tracks..."),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }   
                } else {
                    // Estados para los botones de control
                    var isRandomizing by remember { mutableStateOf(false) }
                    var isStarting by remember { mutableStateOf(false) }
                    var randomJob by remember { mutableStateOf<Job?>(null) }
                    var startJob by remember { mutableStateOf<Job?>(null) }
                    var showShareDialog by remember { mutableStateOf(false) }

                    // Determinar si la playlist seleccionada es editable (es 'mía')
                    // Las playlists de YouTube (prefijo youtube_) también son editables: son locales
                    val isYouTubePlaylistView = selectedPlaylist?.id?.startsWith("youtube_") == true
                    val canEdit = selectedPlaylistEntity != null && selectedPlaylist?.id != "liked_songs"

 // Función para parar todas las reproducciones
                     fun stopAllPlayback() {
                         isRandomizing = false
                         isStarting = false
                         randomJob?.cancel()
                         startJob?.cancel()
                         randomJob = null
                         startJob = null
                         // Cancelar espera de canción y pausar el reproductor
                         //playerViewModel?.cancelWaitForSong()
                         playerViewModel?.pausePlayer()
                     }


                    // Función para randomización simplificada - mezcla toda la playlist
                    fun startRandomizing() {
                        stopAllPlayback()
                        isRandomizing = true

                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            randomJob = coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                // Limpiar estado previo del reproductor
                                playerViewModel.clearPlayerState()

                                // Mezclar toda la lista de tracks
                                val shuffledTracks = trackEntities.shuffled()
                                val firstTrack = shuffledTracks.first()

                                // Reproducir la canción usando PlayerViewModel
                                playerViewModel.initializePlayer()

                                // Establecer la playlist mezclada completa desde el inicio (índice 0)
                                playerViewModel.setCurrentPlaylist(shuffledTracks, 0)

                                // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                playerViewModel.loadAudioFromTrack(firstTrack)

                                isRandomizing = false
                            }
                        }
                    }

                    // Función para reproducción ordenada simplificada - replica exactamente el comportamiento de hacer clic en la primera canción
                    fun startOrderedPlayback() {
                        stopAllPlayback()
                        isStarting = true

                        if (playlistTracks.isNotEmpty() && playerViewModel != null && trackEntities.isNotEmpty()) {
                            startJob = coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                // Limpiar estado previo del reproductor
                                playerViewModel.clearPlayerState()

                                // Replicar exactamente la lógica de SongListItem cuando haces clic en una canción
                                playerViewModel.setCurrentPlaylist(trackEntities, 0)
                                val selectedTrackEntity = trackEntities[0]

                                try {
                                    playerViewModel.loadAudioFromTrack(selectedTrackEntity)
                                } catch (e: Exception) {
                                    Log.e("PlaylistScreen", "Error al reproducir track: ${e.message}")
                                }

                                isStarting = false
                            }
                        } else {
                            isStarting = false
                        }
                    }

                    // Limpiar jobs al salir
                    DisposableEffect(selectedPlaylist) {
                        onDispose {
                            randomJob?.cancel()
                            startJob?.cancel()
                        }
                    }
                    Column {
                        // Botones de control
                        var showDeleteDialog by remember { mutableStateOf(false) }

                        val buttons = buildList {
                            if (!isEditing) {
                                // Botón start
                                add(ActionButtonData(
                                    text = if (isStarting) "//" else ">",
                                    color = if (isStarting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        if (isStarting) {
                                            stopAllPlayback()
                                        } else {
                                            startOrderedPlayback()
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                ))

                                // Botón rand
                                add(ActionButtonData(
                                    text = if (isRandomizing) "<stop>" else "<rnd>",
                                    color = if (isRandomizing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                    onClick = {
                                        if (isRandomizing) {
                                            stopAllPlayback()
                                        } else {
                                            startRandomizing()
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                ))

                                // Botón share
                                add(ActionButtonData(
                                    text = "<share>",
                                    color = MaterialTheme.colorScheme.error,
                                    onClick = {
                                        showShareDialog = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                ))
                            }

                            // Botón edit/save
                            if (canEdit) {
                                add(ActionButtonData(
                                    text = if (isEditing) "<save>" else "<edit>",
                                    color = if (isEditing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = {
                                        if (isEditing) {
                                            // Al hacer clic en save, verificar si hay cambios sin guardar
                                            if (hasUnsavedChanges) {
                                                // Guardar cambios en la playlist local
                                                if (selectedPlaylist != null) {
                                                    isLoadingTracks = true
                                                    coroutineScope.launch {
                                                        val success = localRepository.updatePlaylistDetails(
                                                            localPlaylistId = selectedPlaylist!!.id,
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
                                                // Si no hay cambios, solo salir del modo edición
                                                isEditing = false
                                            }
                                        } else {
                                            // Al entrar al modo edición, guardar valores originales e inicializar campos
                                            originalTitle = selectedPlaylist?.name ?: ""
                                            originalDesc = selectedPlaylist?.description ?: ""
                                            newTitle = originalTitle
                                            newDesc = originalDesc
                                            hasUnsavedChanges = false
                                            isEditing = true
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                ))
                            }

                            // Botón delete
                            if (canEdit && isEditing) {
                                add(ActionButtonData(
                                    text = "<delete>",
                                    color = MaterialTheme.colorScheme.error,
                                    onClick = {
                                        showDeleteDialog = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                ))
                            }
                        }

                        ActionButtonsGroup(
                            buttons = buttons,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp)
                        )

                        // Diálogo de confirmación para eliminar playlist
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = {
                                    Text(
                                        "Delete playlist",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                },
                                text = {
                                    Text(
                                        "Are you sure you want to delete '${selectedPlaylist?.name}'? This action cannot be undone.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showDeleteDialog = false
                                            if (selectedPlaylist != null) {
                                                // Eliminar playlist local
                                                coroutineScope.launch {
                                                    localRepository.deleteYouTubePlaylist(selectedPlaylist!!.id.removePrefix("youtube_"))
                                                    isEditing = false
                                                    hasUnsavedChanges = false
                                                    selectedPlaylist = null
                                                    playlistTracks = emptyList()
                                                    onBack()
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            "Delete",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showDeleteDialog = false }
                                    ) {
                                        Text(
                                            "Cancel",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                }
                            )
                        }
                        if (isEditing) {
                            // Estados para el buscador de canciones en edición
                            var searchQuery by remember { mutableStateOf("") }
                            var isSearching by remember { mutableStateOf(false) }
                            var searchResults by remember { mutableStateOf<List<AppTrack>>(emptyList()) }
                            var editError by remember { mutableStateOf<String?>(null) }

                            // Detectar cambios en los campos
                            LaunchedEffect(newTitle, newDesc) {
                                hasUnsavedChanges = (newTitle != originalTitle || newDesc != originalDesc)
                            }

                            // Usar LazyColumn para permitir scroll
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                // Nombre y descripción, con la portada a la izquierda
                                // (la propia portada es el botón para cambiarla)
                                if (isYouTubePlaylistView) {
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
                                                model = youtubeThumbTo16to9(selectedPlaylistEntity?.imageUrl),
                                                contentDescription = "Portada actual (pulsar para cambiar)",
                                                modifier = Modifier
                                                    .size(120.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        coverImagePickerLauncher.launch(
                                                            PickVisualMediaRequest(
                                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                                            )
                                                        )
                                                    },
                                                contentScale = ContentScale.Crop,
                                                placeholder = null,
                                                error = null,
                                                fallback = null
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = newTitle,
                                                    onValueChange = { newTitle = it },
                                                    label = { Text(Translations.get(context, "playlist_name")) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = newDesc,
                                                    onValueChange = { newDesc = it },
                                                    label = { Text(Translations.get(context, "description")) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(16.dp))
                                    }
                                } else {
                                    item {
                                        OutlinedTextField(
                                            value = newTitle,
                                            onValueChange = { newTitle = it },
                                            label = { Text(Translations.get(context, "playlist_name")) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    item {
                                        OutlinedTextField(
                                            value = newDesc,
                                            onValueChange = { newDesc = it },
                                            label = { Text(Translations.get(context, "description")) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(16.dp))
                                    }
                                }

                                // Campo de búsqueda
                                item {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = { Text(Translations.get(context, "search_tracks_label")) },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { searchQuery = "" }) {
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
                                                if (searchQuery.isNotBlank() && !isSearching) {
                                                    isSearching = true
                                                    // Búsqueda de vídeos de YouTube con la integración existente
                                                    coroutineScope.launch {
                                                        val result = try {
                                                            youtubeSearchManager.searchYouTubeAll(searchQuery, maxVideos = 10, maxPlaylists = 0)
                                                        } catch (e: Exception) {
                                                            Log.e("PlaylistScreen", "Error buscando en YouTube: ${e.message}")
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
                                                            editError = "YouTube search failed"
                                                        }
                                                    }
                                                }
                                            }
                                        ),
                                        enabled = !isSearching
                                    )
                                }

                                // Mostrar indicador de búsqueda
                                if (isSearching) {
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

                                // Resultados de búsqueda usando SongListItem
                                if (searchResults.isNotEmpty()) {
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
                                    val searchTrackEntities = searchResults.take(10).mapIndexed { trackIndex, track ->
                                        TrackEntity(
                                            id = "edit_search_${track.id}_$trackIndex",
                                            playlistId = "edit_search_${System.currentTimeMillis()}",
                                            remoteTrackId = track.id,
                                            name = track.name,
                                            artists = track.getArtistNames(),
                                            youtubeVideoId = null,
                                            audioUrl = null,
                                            position = trackIndex,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }

                                    items(searchResults.take(10).size) { index ->
                                        val track = searchResults[index]
                                        val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
                                        SongListItem(
                                            song = Song(
                                                number = index + 1,
                                                title = track.name,
                                                artist = track.getArtistNames(),
                                                remoteId = track.id,
                                                shareUrl = "https://www.youtube.com/watch?v=${track.id}"
                                            ),
                                            trackEntities = searchTrackEntities,
                                            index = index,
                                            playerViewModel = playerViewModel,
                                            coroutineScope = coroutineScope,
                                            isCurrentlyPlaying = isPlaying,
                                            customButtonIcon = "+",
                                            customButtonAction = {
                                                if (selectedPlaylist != null) {
                                                    // Añadir track a la playlist de YouTube local (videoId ya resuelto)
                                                    coroutineScope.launch {
                                                        val success = localRepository.addTrackToYouTubePlaylist(
                                                            localPlaylistId = selectedPlaylist!!.id,
                                                            track = TrackEntity(
                                                                id = "",
                                                                playlistId = selectedPlaylist!!.id,
                                                                remoteTrackId = track.id,
                                                                name = track.name,
                                                                artists = track.getArtistNames(),
                                                                youtubeVideoId = track.id.takeIf { it.length == 11 },
                                                                audioUrl = null,
                                                                position = 0,
                                                                lastSyncTime = System.currentTimeMillis()
                                                            )
                                                        )
                                                        if (success) {
                                                            searchResults = emptyList()
                                                            searchQuery = ""
                                                        } else {
                                                            editError = "Error adding track"
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Mostrar error si hay
                                editError?.let {
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        Text("${Translations.get(context, "error_prefix")}$it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(16.dp))
                                }

                                // Lista de canciones actuales usando SongListItem
                                if (playlistTracks.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "current tracks [${playlistTracks.size}]:",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }

                                    items(playlistTracks.size) { index ->
                                        val track = playlistTracks[index]
                                        val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
                                        SongListItem(
                                            song = Song(
                                                number = index + 1,
                                                title = track.name,
                                                artist = track.getArtistNames(),
                                                remoteId = track.id,
                                                shareUrl = "https://www.youtube.com/watch?v=${track.id}"
                                            ),
                                            trackEntities = trackEntities,
                                            index = index,
                                            playerViewModel = playerViewModel,
                                            coroutineScope = coroutineScope,
                                            isCurrentlyPlaying = isPlaying,
                                            customButtonIcon = "x",
                                            customButtonAction = {
                                                if (selectedPlaylist != null) {
                                                    // Eliminar track de la playlist de YouTube local
                                                    coroutineScope.launch {
                                                        val success = localRepository.removeTrackFromYouTubePlaylist(
                                                            localPlaylistId = selectedPlaylist!!.id,
                                                            remoteTrackId = track.id
                                                        )
                                                        if (!success) {
                                                            editError = "Error removing track"
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        // Lista de tracks (solo visible cuando NO está en modo edición)
                        if (!isEditing) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Prepara trackEntities - si no hay en DB, crear temporales
                                val trackEntitiesList = trackEntities.ifEmpty {
                                    // Crear TrackEntities temporales para álbumes u otras fuentes sin BD
                                    playlistTracks.mapIndexed { trackIndex, track ->
                                        TrackEntity(
                                            id = "temp_${selectedPlaylist?.id}_${track.id}",
                                            playlistId = selectedPlaylist?.id ?: "unknown",
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

                                items(playlistTracks.size) { index ->
                                    val track = playlistTracks[index]
                                    val song = Song(
                                        number = index + 1,
                                        title = track.name,
                                        artist = track.getArtistNames(),
                                        remoteId = track.id,
                                        shareUrl = "https://www.youtube.com/watch?v=${track.id}"
                                    )
                                    val isPlaying = currentPlayingTrack?.remoteTrackId == track.id
                                    SongListItem(
                                        song = song,
                                        trackEntities = trackEntitiesList,
                                        index = index,
                                        playerViewModel = playerViewModel,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier.fillMaxWidth(),
                                        isCurrentlyPlaying = isPlaying
                                    )
                                }
                            }
                        }
                    }

                    // Diálogo de confirmación para salir sin guardar
                    if (showExitEditDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showExitEditDialog = false
                                pendingPlaylist = null
                            },
                            title = {
                                Text(
                                    "Unsaved changes",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            },
                            text = {
                                Text(
                                    "You have unsaved changes. Are you sure you want to exit?",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showExitEditDialog = false
                                        isEditing = false
                                        hasUnsavedChanges = false

                                        // Si hay una playlist pendiente, cargarla
                                        if (pendingPlaylist != null) {
                                            selectedPlaylist = pendingPlaylist
                                            loadPlaylistTracks(pendingPlaylist!!)
                                            pendingPlaylist = null
                                        } else {
                                            // Si no hay playlist pendiente, salir de la vista actual
                                            selectedPlaylist = null
                                            playlistTracks = emptyList()
                                        }
                                    }
                                ) {
                                    Text(
                                        "Exit",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showExitEditDialog = false
                                        pendingPlaylist = null
                                    }
                                ) {
                                    Text(
                                        "Cancel",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        )
                    }

                    // Diálogo de compartir - debe estar dentro del mismo scope que showShareDialog
                    if (showShareDialog) {
                        ShareDialog(
                            item = ShareableItem(
                                remoteId = selectedPlaylist!!.id,
                                shareUrl = null,
                                youtubeId = selectedPlaylist!!.id.removePrefix("youtube_"),
                                title = selectedPlaylist!!.name,
                                artist = "Playlist",
                                type = ShareType.PLAYLIST
                            ),
                            onDismiss = { showShareDialog = false }
                        )
                    }
                }
            }


        // Lista de playlists (visible cuando no hay playlist seleccionada ni creando)
        if (selectedPlaylist == null && !showCreatePlaylistScreen && pendingInitialPlaylist == null) {
            Titulo(
                titulo = Translations.get(context, "plyr_lists"),
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
                            showCreatePlaylistScreen = true
                        }.padding(start = 8.dp)
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (playlists.isEmpty()) {
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
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(playlists.size) { index ->
                        val playlist = playlists[index]
                        val playlistEntity = playlistsFromDB.find { it.remoteId == playlist.id }
                        val channelName = getYouTubeChannelName(playlistEntity)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    loadPlaylistTracks(playlist)
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
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
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
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
                    }
                }
            }
        }
        coverPickUri?.let { uri ->
            val entity = selectedPlaylistEntity
            if (entity != null && isEditing && entity.remoteId.startsWith("youtube_")) {
                CoverCropDialog(
                    uri = uri,
                    onDismiss = { coverPickUri = null },
                    onConfirm = { cropped ->
                        coverPickUri = null
                        coroutineScope.launch {
                            val path = CoverImageManager.save(
                                context,
                                entity.remoteId.removePrefix("youtube_"),
                                cropped
                            )
                            if (path != null) {
                                localRepository.updatePlaylistImage(entity.remoteId, path)
                                // Refrescar la entidad para que la preview en modo edición se actualice
                                selectedPlaylistEntity = entity.copy(imageUrl = path)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CreatePlaylistScreen(
    onBack: () -> Unit,
    onPlaylistCreated: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    var playlistName by remember { mutableStateOf("") }
    var playlistDesc by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Estados para el buscador de canciones
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<AppTrack>>(emptyList()) }
    var selectedTracks by remember { mutableStateOf<List<AppTrack>>(emptyList()) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }

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
        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            label = { Text(Translations.get(context, "playlist_name")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = playlistDesc,
            onValueChange = { playlistDesc = it },
            label = { Text(Translations.get(context, "description_optional")) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        // Campo de búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(Translations.get(context, "search_tracks_label")) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
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
                                error = "YouTube search failed"
                            }
                        }
                    }
                }
            ),
            enabled = !isSearching
        )

        // Mostrar indicador de búsqueda
        if (isSearching) {
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
        if (searchResults.isNotEmpty()) {
            val trackEntities = searchResults.take(10).mapIndexed { trackIndex, track ->
                TrackEntity(
                    id = "yt_search_${track.id}_$trackIndex",
                    playlistId = "yt_search_${System.currentTimeMillis()}",
                    remoteTrackId = track.id,
                    name = track.name,
                    artists = track.getArtistNames(),
                    youtubeVideoId = null,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }

            searchResults.take(10).forEachIndexed { index, track ->
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
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope,
                    isCurrentlyPlaying = isPlaying,
                    isSelected = selectedTracks.contains(track),
                    customButtonIcon = "+",
                    customButtonAction = {
                        if (!selectedTracks.contains(track)) {
                            selectedTracks = selectedTracks + track
                            searchResults = emptyList()
                            searchQuery = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Lista de canciones seleccionadas
        if (selectedTracks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "selected [${selectedTracks.size}]:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            val tracksEntities = selectedTracks.mapIndexed { trackIndex, track ->
                TrackEntity(
                    id = "yt_search_${track.id}_$trackIndex",
                    playlistId = "yt_search_${System.currentTimeMillis()}",
                    remoteTrackId = track.id,
                    name = track.name,
                    artists = track.getArtistNames(),
                    youtubeVideoId = null,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }

            selectedTracks.forEachIndexed { index, track ->
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
                    playerViewModel = playerViewModel,
                    coroutineScope = coroutineScope,
                    isCurrentlyPlaying = isPlaying,
                    isSelected = true,
                    customButtonIcon = "x",
                    customButtonAction = {
                        selectedTracks = selectedTracks.filterIndexed { i, _ -> i != index }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ActionButton(
            data = ActionButtonData(
                text = if (isLoading) "<creating...>" else "<create>",
                color = if (isLoading) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                enabled = !isLoading && playlistName.isNotBlank(),
                onClick = {
                    // Acción de crear playlist con las canciones seleccionadas
                    isLoading = true
                    error = null
                    // Crear playlist de YouTube usando la integración existente
                    coroutineScope.launch {
                        val (saved, message) = withContext(Dispatchers.IO) {
                            val creator = YouTubePlaylistCreator()
                            val rawId = "yt_${System.currentTimeMillis()}"
                            // Los tracks añadidos vía búsqueda de YouTube (id = videoId) no se re-buscan
                            val resolvedVideoIds = selectedTracks
                                .filter { it.id.length == 11 }
                                .associate { it.id to it.id }
                            val created = creator.build(
                                title = playlistName,
                                description = playlistDesc.ifBlank { null },
                                sourceTracks = creator.buildSourceTracks(selectedTracks),
                                targetPlaylistId = "youtube_$rawId",
                                resolvedVideoIds = resolvedVideoIds
                            )
                            val ok = localRepository.saveCreatedYouTubePlaylist(
                                playlistId = rawId,
                                title = created.title,
                                description = created.description,
                                imageUrl = null,
                                tracks = created.tracks
                            )
                            ok to "${created.tracks.size} tracks (${selectedTracks.size - created.tracks.size} sin vídeo)"
                        }
                        isLoading = false
                        if (saved) {
                            onPlaylistCreated()
                        } else {
                            error = message
                        }
                    }
                }
            )
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("${Translations.get(context, "error_prefix")}$it", color = MaterialTheme.colorScheme.error)
        }
    }
}


