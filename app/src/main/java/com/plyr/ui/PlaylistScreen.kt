package com.plyr.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.livedata.observeAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.net.Uri
import coil.compose.AsyncImage
import com.plyr.database.*
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyTrack
import com.plyr.network.SpotifyArtist
import com.plyr.network.SpotifyAlbum
import com.plyr.network.SpotifyArtistFull
import com.plyr.utils.Config
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.ui.components.*
import androidx.compose.ui.graphics.Brush
import com.plyr.network.getRecommendations
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
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var isSyncing by remember { mutableStateOf(false) }
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

    // Estado para álbumes guardados
    var savedAlbums by remember { mutableStateOf<List<SpotifyAlbum>>(emptyList()) }
    var isLoadingSavedAlbums by remember { mutableStateOf(false) }
    var savedAlbumsCount by remember { mutableIntStateOf(0) }

    // Estado para artistas seguidos
    var followedArtists by remember { mutableStateOf<List<SpotifyArtistFull>>(emptyList()) }
    var isLoadingFollowedArtists by remember { mutableStateOf(false) }
    var followedArtistsCount by remember { mutableIntStateOf(0) }

    // Estados para detectar cambios en modo edición (movidos aquí para ser accesibles globalmente)
    var showExitEditDialog by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    var originalTitle by remember { mutableStateOf("") }
    var originalDesc by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    // Convertir entidades a SpotifyPlaylist para compatibilidad con UI existente
    // Filtrar liked_songs y álbumes para que no aparezcan duplicados (se muestran como items especiales)
    val playlists = playlistsFromDB
        .filter { it.spotifyId != "liked_songs" && !it.spotifyId.startsWith("album_") }
        .map { it.toSpotifyPlaylist() }

    // Estado para mostrar tracks de una playlist
    var selectedPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var selectedPlaylistEntity by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    var showCreatePlaylistScreen by remember { mutableStateOf(false) }

    // Estado y lanzador para cambiar la portada de una playlist (solo locales youtube_)
    var coverPickUri by remember { mutableStateOf<Uri?>(null) }
    val coverImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) coverPickUri = uri
    }

    // Estado para los álbumes del artista seleccionado
    var selectedArtist by remember { mutableStateOf<SpotifyArtistFull?>(null) }
    var artistAlbums by remember { mutableStateOf<List<SpotifyAlbum>>(emptyList()) }
    var isLoadingArtistAlbums by remember { mutableStateOf(false) }
    var isFollowingArtist by remember { mutableStateOf<Boolean?>(null) }
    var isFollowActionLoading by remember { mutableStateOf(false) }
    var isViewingAlbumFromArtist by remember { mutableStateOf(false) }

    // Verificar si seguimos al artista cuando se selecciona uno
    LaunchedEffect(selectedArtist) {
        if (selectedArtist != null) {
            val accessToken = Config.getSpotifyAccessToken(context)
            if (accessToken != null) {
                SpotifyRepository.checkIfFollowingArtist(accessToken, selectedArtist!!.id) { isFollowing, errorMsg ->
                    if (errorMsg == null) {
                        isFollowingArtist = isFollowing
                    } else {
                        Log.e("PlaylistScreen", "Error checking if following artist: $errorMsg")
                        isFollowingArtist = null
                    }
                }
            }
        } else {
            isFollowingArtist = null
        }
    }

    // Estado para manejar navegación pendiente cuando hay cambios sin guardar
    var pendingPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }

    // Tracks observados desde la base de datos
    val tracksFromDB by if (selectedPlaylistEntity != null) {
        localRepository.getTracksByPlaylistLiveData(selectedPlaylistEntity!!.spotifyId)
            .asFlow()
            .collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    // Actualizar tracks cuando cambien en la DB
    LaunchedEffect(tracksFromDB) {
        if (selectedPlaylistEntity != null) {
            playlistTracks = tracksFromDB.map { it.toSpotifyTrack() }
        }
    }

    // Función para cargar playlists con sincronización automática
    val loadPlaylists = {
        if (isSpotifyConnected) {
            isLoading = true
            coroutineScope.launch {
                localRepository.getPlaylistsWithAutoSync()
                isLoading = false
            }
        }
    }

    // Función para cargar tracks de una playlist
    val loadPlaylistTracks: (SpotifyPlaylist) -> Unit = { playlist ->
        selectedPlaylist = playlist
        selectedPlaylistEntity = playlistsFromDB.find { it.spotifyId == playlist.id }
        isLoadingTracks = true

        if (selectedPlaylistEntity == null) {
            isLoadingTracks = false
        } else {
            if (playlist.id.startsWith("youtube_")) {
                // Para playlists de YouTube, los tracks ya están en la DB
                // Forzar lectura para que el observer de tracksFromDB se active
                coroutineScope.launch {
                    localRepository.getTracksWithAutoSync(playlist.id)
                    isLoadingTracks = false
                }
            } else {
                // Usar corrutina para operaciones asíncronas (Spotify playlists)
                coroutineScope.launch {
                    localRepository.getTracksWithAutoSync(playlist.id)
                    isLoadingTracks = false
                }
            }
        }
    }

    // Abrir una playlist directamente desde el Home (carrusel)
    var pendingInitialPlaylist by remember { mutableStateOf(initialPlaylistId) }
    LaunchedEffect(playlistsFromDB) {
        val pendingId = pendingInitialPlaylist
        if (pendingId != null && playlistsFromDB.isNotEmpty()) {
            val entity = playlistsFromDB.find { it.spotifyId == pendingId }
            pendingInitialPlaylist = null
            onInitialConsumed()
            if (entity != null) {
                loadPlaylistTracks(entity.toSpotifyPlaylist())
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

    //LIKED SONGS
    // Función para cargar las Liked Songs del usuario - ahora sincroniza con la base de datos
    val loadLikedSongs: () -> Unit = {
        coroutineScope.launch {
            try {
                // Sincronizar Liked Songs con la base de datos local
                localRepository.getLikedSongsWithAutoSync()
                Log.d("PlaylistsScreen", "✓ Liked Songs sincronizadas desde DB")
            } catch (e: Exception) {
                Log.e("PlaylistsScreen", "Exception syncing liked songs: ${e.message}")
            }
        }
    }
    //LIKED SONGS


    //ALBUMS
    // Función para cargar los álbumes guardados del usuario
    val loadSavedAlbums: () -> Unit = {
        isLoadingSavedAlbums = true

        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    // Obtener los álbumes guardados usando la API de Spotify
                    SpotifyRepository.getUserSavedAlbums(accessToken) { albums, errorMsg ->
                        isLoadingSavedAlbums = false
                        if (albums != null) {
                            savedAlbums = albums
                            savedAlbumsCount = albums.size
                            Log.d("PlaylistsScreen", "✓ Saved Albums actualizados: ${albums.size} álbumes")
                        } else {
                            Log.e("PlaylistsScreen", "Error loading saved albums: $errorMsg")
                        }
                    }
                } else {
                    isLoadingSavedAlbums = false
                }
            } catch (e: Exception) {
                isLoadingSavedAlbums = false
                Log.e("PlaylistsScreen", "Exception loading saved albums: ${e.message}")
            }
        }
    }
    //ALBUMS

    //ARTISTS
    // Función para cargar los artistas seguidos del usuario
    val loadFollowedArtists: () -> Unit = {
        isLoadingFollowedArtists = true

        coroutineScope.launch {
            try {
                val accessToken = Config.getSpotifyAccessToken(context)
                if (accessToken != null) {
                    // Obtener los artistas seguidos usando la API de Spotify
                    SpotifyRepository.getUserFollowedArtists(accessToken) { artists, errorMsg ->
                        isLoadingFollowedArtists = false
                        if (artists != null) {
                            followedArtists = artists
                            followedArtistsCount = artists.size
                            Log.d("PlaylistsScreen", "✓ Followed Artists actualizados: ${artists.size} artistas")
                        } else {
                            Log.e("PlaylistsScreen", "Error loading followed artists: $errorMsg")
                        }
                    }
                } else {
                    isLoadingFollowedArtists = false
                }
            } catch (e: Exception) {
                isLoadingFollowedArtists = false
                Log.e("PlaylistsScreen", "Exception loading followed artists: ${e.message}")
            }
        }
    }
    //ARTISTS

    // Función para forzar sincronización completa
    val forceSyncAll = {
        if (!isSpotifyConnected) {
        } else {
            isSyncing = true

            coroutineScope.launch {
                try {
                    localRepository.forceSyncAll()
                    loadLikedSongs()
                    loadSavedAlbums()
                    loadFollowedArtists()
                    isSyncing = false
                } catch (_: Exception) {
                    isSyncing = false
                }
            }
        }
    }

    // Cargar si está conectado
    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected) {
            loadPlaylists()
            loadLikedSongs()
            loadSavedAlbums()
            loadFollowedArtists()
        }
    }

    // Cleanup del YouTubeSearchManager
    DisposableEffect(Unit) {
        onDispose {
            youtubeSearchManager.cleanup()
        }
    }

    // Manejar botón de retroceso del sistema
    BackHandler {
        if (isEditing && hasUnsavedChanges) {
            // Si estamos en modo edición con cambios sin guardar, mostrar diálogo
            showExitEditDialog = true
        } else {
            // Salir de la playlist y volver al Home
            isEditing = false
            hasUnsavedChanges = false
            selectedPlaylist = null
            selectedPlaylistEntity = null
            playlistTracks = emptyList()
            selectedArtist = null
            artistAlbums = emptyList()
            isViewingAlbumFromArtist = false
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
                onBack = { showCreatePlaylistScreen = false },
                onPlaylistCreated = { showCreatePlaylistScreen = false; onBack() },
                playerViewModel = playerViewModel
            )
            return@Column
        }
        if (selectedPlaylist != null) {
            Titulo(selectedPlaylist!!.name)
            Spacer(modifier = Modifier.height(16.dp))

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
                                val shuffledTracks = tracksFromDB.shuffled()
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

                        if (playlistTracks.isNotEmpty() && playerViewModel != null && tracksFromDB.isNotEmpty()) {
                            startJob = coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                // Limpiar estado previo del reproductor
                                playerViewModel.clearPlayerState()

                                // Replicar exactamente la lógica de SongListItem cuando haces clic en una canción
                                playerViewModel.setCurrentPlaylist(tracksFromDB, 0)
                                val selectedTrackEntity = tracksFromDB[0]

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

                                // Botón follow/unfollow - solo para artistas
                                if (selectedArtist != null) {
                                    add(ActionButtonData(
                                        text = if (isFollowActionLoading) {
                                            "<...>"
                                        } else if (isFollowingArtist == true) {
                                            "<unfollow>"
                                        } else {
                                            "<follow>"
                                        },
                                        color = if (isFollowingArtist == true) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        enabled = !isFollowActionLoading,
                                        onClick = {
                                            val accessToken = Config.getSpotifyAccessToken(context)
                                            if (accessToken != null && selectedArtist != null) {
                                                isFollowActionLoading = true
                                                if (isFollowingArtist == true) {
                                                    SpotifyRepository.unfollowArtist(accessToken, selectedArtist!!.id) { success, errorMsg ->
                                                        isFollowActionLoading = false
                                                        if (success) {
                                                            isFollowingArtist = false
                                                            // Recargar la lista de artistas seguidos
                                                            coroutineScope.launch {
                                                                val token = Config.getSpotifyAccessToken(context)
                                                                if (token != null) {
                                                                    SpotifyRepository.getUserFollowedArtists(token) { artists, _ ->
                                                                        if (artists != null) {
                                                                            followedArtists = artists
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Log.e("PlaylistScreen", "Error unfollowing artist: $errorMsg")
                                                        }
                                                    }
                                                } else {
                                                    SpotifyRepository.followArtist(accessToken, selectedArtist!!.id) { success, errorMsg ->
                                                        isFollowActionLoading = false
                                                        if (success) {
                                                            isFollowingArtist = true
                                                            // Recargar la lista de artistas seguidos
                                                            coroutineScope.launch {
                                                                val token = Config.getSpotifyAccessToken(context)
                                                                if (token != null) {
                                                                    SpotifyRepository.getUserFollowedArtists(token) { artists, _ ->
                                                                        if (artists != null) {
                                                                            followedArtists = artists
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Log.e("PlaylistScreen", "Error following artist: $errorMsg")
                                                        }
                                                    }
                                                }
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    ))
                                }
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
                                                if (isYouTubePlaylistView && selectedPlaylist != null) {
                                                    // Guardar cambios en la playlist de YouTube local
                                                    isLoadingTracks = true
                                                    coroutineScope.launch {
                                                        val success = localRepository.updatePlaylistDetails(
                                                            localPlaylistId = selectedPlaylist!!.id,
                                                            newTitle = if (newTitle != originalTitle) newTitle else null,
                                                            newDesc = if (newDesc != originalDesc) newDesc else null
                                                        )
                                                        isLoadingTracks = false
                                                        if (success) {
                                                            // Salir del modo edición y volver al listado
                                                            isEditing = false
                                                            hasUnsavedChanges = false
                                                            selectedPlaylist = null
                                                            playlistTracks = emptyList()
                                                            onBack()
                                                        } else {
                                                            Log.e("PlaylistScreen", "Error actualizando playlist de YouTube local")
                                                        }
                                                    }
                                                } else {
                                                    // Guardar cambios en Spotify
                                                    val accessToken = Config.getSpotifyAccessToken(context)
                                                    if (accessToken != null && selectedPlaylist != null) {
                                                        // Mostrar indicador de carga
                                                        isLoadingTracks = true

                                                        SpotifyRepository.updatePlaylistDetails(
                                                            accessToken = accessToken,
                                                            playlistId = selectedPlaylist!!.id,
                                                            name = if (newTitle != originalTitle) newTitle else null,
                                                            description = if (newDesc != originalDesc) newDesc else null
                                                        ) { success, errorMsg ->
                                                            if (success) {
                                                                // Sincronizar playlists después de EDITAR
                                                                coroutineScope.launch {
                                                                    localRepository.syncPlaylistsFromSpotify()
                                                                    // Esperar a que termine la sincronización
                                                                    kotlinx.coroutines.delay(500)
                                                                    isLoadingTracks = false
                                                                    // Salir del modo edición y volver al listado
                                                                    isEditing = false
                                                                    hasUnsavedChanges = false
                                                                    selectedPlaylist = null
                                                                    playlistTracks = emptyList()
                                                                }
                                                            } else {
                                                                isLoadingTracks = false
                                                                // Mostrar error
                                                                Log.e("PlaylistScreen", "Error actualizando playlist: $errorMsg")
                                                            }
                                                        }
                                                    } else {
                                                        // Si no hay token, solo resetear el flag y salir
                                                        hasUnsavedChanges = false
                                                        isEditing = false
                                                    }
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
                                            if (isYouTubePlaylistView && selectedPlaylist != null) {
                                                // Eliminar playlist de YouTube local (sin token de Spotify)
                                                coroutineScope.launch {
                                                    localRepository.deleteYouTubePlaylist(selectedPlaylist!!.id.removePrefix("youtube_"))
                                                    isEditing = false
                                                    hasUnsavedChanges = false
                                                    selectedPlaylist = null
                                                    playlistTracks = emptyList()
                                                    onBack()
                                                }
                                            } else {
                                                // Eliminar la playlist
                                                val accessToken = Config.getSpotifyAccessToken(context)
                                                if (accessToken != null && selectedPlaylist != null) {
                                                    coroutineScope.launch {
                                                        SpotifyRepository.unfollowPlaylist(
                                                            accessToken,
                                                            selectedPlaylist!!.id
                                                        ) { success: Boolean, errorMsg: String? ->
                                                            if (success) {
                                                                // Sincronizar playlists después de eliminar
                                                                coroutineScope.launch {
                                                                    localRepository.syncPlaylistsFromSpotify()
                                                                }
                                                                // Salir del modo edición y volver a la lista
                                                                isEditing = false
                                                                hasUnsavedChanges = false
                                                                selectedPlaylist = null
                                                                playlistTracks = emptyList()
                                                                // Volver al Home
                                                                onBack()
                                                            }
                                                        }
                                                    }
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
                            var searchResults by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
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
                                                    if (isYouTubePlaylistView) {
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
                                                                    SpotifyTrack(
                                                                        id = video.videoId,
                                                                        name = video.title,
                                                                        artists = listOf(SpotifyArtist(video.uploader))
                                                                    )
                                                                }
                                                            } else {
                                                                editError = "YouTube search failed"
                                                            }
                                                        }
                                                    } else {
                                                        val accessToken = Config.getSpotifyAccessToken(context)
                                                        if (accessToken != null) {
                                                            coroutineScope.launch {
                                                                SpotifyRepository.searchAll(accessToken, searchQuery) { results, errorMsg ->
                                                                    isSearching = false
                                                                    if (results != null) {
                                                                        searchResults = results.tracks.items
                                                                    } else {
                                                                        editError = errorMsg
                                                                    }
                                                                }
                                                            }
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
                                            spotifyTrackId = track.id,
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
                                        val isPlaying = currentPlayingTrack?.spotifyTrackId == track.id
                                        SongListItem(
                                            song = Song(
                                                number = index + 1,
                                                title = track.name,
                                                artist = track.getArtistNames(),
                                                spotifyId = track.id,
                                                spotifyUrl = "https://open.spotify.com/track/${track.id}"
                                            ),
                                            trackEntities = searchTrackEntities,
                                            index = index,
                                            playerViewModel = playerViewModel,
                                            coroutineScope = coroutineScope,
                                            isCurrentlyPlaying = isPlaying,
                                            customButtonIcon = "+",
                                            customButtonAction = {
                                                if (isYouTubePlaylistView && selectedPlaylist != null) {
                                                    // Añadir track a la playlist de YouTube local (videoId ya resuelto)
                                                    coroutineScope.launch {
                                                        val success = localRepository.addTrackToYouTubePlaylist(
                                                            localPlaylistId = selectedPlaylist!!.id,
                                                            track = TrackEntity(
                                                                id = "",
                                                                playlistId = selectedPlaylist!!.id,
                                                                spotifyTrackId = track.id,
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
                                                } else {
                                                    // Añadir canción a la playlist
                                                    val accessToken = Config.getSpotifyAccessToken(context)
                                                    if (accessToken != null && selectedPlaylist != null) {
                                                        coroutineScope.launch {
                                                            SpotifyRepository.addTrackToPlaylist(
                                                                accessToken,
                                                                selectedPlaylist!!.id,
                                                                track.id
                                                            ) { success, errorMsg ->
                                                                if (success) {
                                                                    searchResults = emptyList()
                                                                    searchQuery = ""
                                                                    // Recargar tracks
                                                                    coroutineScope.launch {
                                                                        localRepository.syncTracksFromSpotify(selectedPlaylist!!.id)
                                                                    }
                                                                } else {
                                                                    editError = errorMsg
                                                                }
                                                            }
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
                                        val isPlaying = currentPlayingTrack?.spotifyTrackId == track.id
                                        SongListItem(
                                            song = Song(
                                                number = index + 1,
                                                title = track.name,
                                                artist = track.getArtistNames(),
                                                spotifyId = track.id,
                                                spotifyUrl = "https://open.spotify.com/track/${track.id}"
                                            ),
                                            trackEntities = tracksFromDB,
                                            index = index,
                                            playerViewModel = playerViewModel,
                                            coroutineScope = coroutineScope,
                                            isCurrentlyPlaying = isPlaying,
                                            customButtonIcon = "x",
                                            customButtonAction = {
                                                if (isYouTubePlaylistView && selectedPlaylist != null) {
                                                    // Eliminar track de la playlist de YouTube local
                                                    coroutineScope.launch {
                                                        val success = localRepository.removeTrackFromYouTubePlaylist(
                                                            localPlaylistId = selectedPlaylist!!.id,
                                                            spotifyTrackId = track.id
                                                        )
                                                        if (!success) {
                                                            editError = "Error removing track"
                                                        }
                                                    }
                                                } else {
                                                    // Eliminar canción de la playlist
                                                    val accessToken = Config.getSpotifyAccessToken(context)
                                                    if (accessToken != null && selectedPlaylist != null) {
                                                        coroutineScope.launch {
                                                            SpotifyRepository.removeTrackFromPlaylist(
                                                                accessToken,
                                                                selectedPlaylist!!.id,
                                                                track.id
                                                            ) { success, errorMsg ->
                                                                if (success) {
                                                                    // Recargar tracks
                                                                    coroutineScope.launch {
                                                                        localRepository.syncTracksFromSpotify(selectedPlaylist!!.id)
                                                                    }
                                                                } else {
                                                                    editError = errorMsg
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            onLikedStatusChanged = {
                                                // Recargar las Liked Songs cuando se modifica el estado
                                                loadLikedSongs()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        // Lista de tracks (solo visible cuando NO está en modo edición)
                        if (!isEditing) {
                            // Estado para recomendaciones
                            var recommendedSongs by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
                            var isLoadingRecommendations by remember { mutableStateOf(false) }
                            var recommendationError by remember { mutableStateOf<String?>(null) }

                            // Cargar recomendaciones cuando se carga la playlist
                            LaunchedEffect(playlistTracks) {
                                if (playlistTracks.isNotEmpty()) {
                                    isLoadingRecommendations = true
                                    recommendationError = null
                                    coroutineScope.launch {
                                        try {
                                            // Extraer nombres de artistas de las canciones - obtener múltiples artistas distintos
                                            val artistNames = mutableSetOf<String>()

                                            // Iterar sobre todas las canciones para recolectar artistas distintos
                                            for (track in playlistTracks) {
                                                val trackArtists = track.getArtistNames()
                                                // Dividir por comas si hay múltiples artistas
                                                val artistList = trackArtists.split(",").map { it.trim() }
                                                artistNames.addAll(artistList)

                                                // Detener cuando tengamos suficientes artistas (máximo 10)
                                                if (artistNames.size >= 10) {
                                                    break
                                                }
                                            }

                                            val finalArtistList = artistNames.toList().take(10)

                                            if (finalArtistList.isNotEmpty()) {
                                                val recommendations = getRecommendations(context, finalArtistList)
                                                // ahora getRecommendations devuelve List<SpotifyTrack>
                                                recommendedSongs = recommendations
                                            }
                                        } catch (e: Exception) {
                                            Log.e("PlaylistScreen", "Error loading recommendations: ${e.message}")
                                            recommendationError = e.message
                                        } finally {
                                            isLoadingRecommendations = false
                                        }
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Prepara trackEntities - si no hay en DB, crear temporales
                                val trackEntitiesList = tracksFromDB.ifEmpty {
                                    // Crear TrackEntities temporales para álbumes u otras fuentes sin BD
                                    playlistTracks.mapIndexed { trackIndex, track ->
                                        TrackEntity(
                                            id = "temp_${selectedPlaylist?.id}_${track.id}",
                                            playlistId = selectedPlaylist?.id ?: "unknown",
                                            spotifyTrackId = track.id,
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
                                        spotifyId = track.id,
                                        spotifyUrl = "https://open.spotify.com/track/${track.id}"
                                    )
                                    val isPlaying = currentPlayingTrack?.spotifyTrackId == track.id
                                    SongListItem(
                                        song = song,
                                        trackEntities = trackEntitiesList,
                                        index = index,
                                        playerViewModel = playerViewModel,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier.fillMaxWidth(),
                                        isCurrentlyPlaying = isPlaying,
                                        onLikedStatusChanged = {
                                            // Recargar las Liked Songs cuando se modifica el estado
                                            loadLikedSongs()
                                        }
                                    )
                                }

                                // Sección de álbumes del artista (solo si hay un artista seleccionado)
                                if (selectedArtist != null && artistAlbums.isNotEmpty()) {
                                    item {
                                        Spacer(Modifier.height(24.dp))
                                        Text(
                                            text = Translations.get(context, "albums"),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            items(artistAlbums.size) { index ->
                                                val album = artistAlbums[index]
                                                Column(
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .clickable {
                                                            // Cargar los tracks del álbum
                                                            isViewingAlbumFromArtist = true
                                                            isLoadingTracks = true
                                                            val accessToken = Config.getSpotifyAccessToken(context)
                                                            if (accessToken != null) {
                                                                SpotifyRepository.getAlbumTracks(accessToken, album.id) { tracks, errorMsg ->
                                                                    isLoadingTracks = false
                                                                    if (tracks != null) {
                                                                        // Crear una playlist temporal para mostrar el álbum
                                                                        selectedPlaylist = SpotifyPlaylist(
                                                                            id = album.id,
                                                                            name = album.name,
                                                                            description = "Album by ${album.getArtistNames()}",
                                                                            tracks = com.plyr.network.SpotifyPlaylistTracks(null, album.totaltracks ?: tracks.size),
                                                                            images = album.images
                                                                        )
                                                                        playlistTracks = tracks
                                                                        selectedPlaylistEntity = null
                                                                        // NO limpiar artista ni álbumes aquí para poder volver
                                                                    } else {
                                                                        Log.e("PlaylistScreen", "Error loading album tracks: $errorMsg")
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    AsyncImage(
                                                        model = album.getImageUrl(),
                                                        contentDescription = "Portada de ${album.name}",
                                                        modifier = Modifier
                                                            .size(150.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        placeholder = null,
                                                        error = null,
                                                        fallback = null
                                                    )

                                                    Text(
                                                        text = album.name,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        ),
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Sección de recomendaciones (usando SpotifyTrack real)
                                if (recommendedSongs.isNotEmpty()) {
                                    item {
                                        Spacer(Modifier.height(24.dp))
                                        Text(
                                            text = Translations.get(context, "similar_songs"),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                    }

                                    // Construir TrackEntity reales a partir de SpotifyTrack
                                    val recommendedTrackEntities = recommendedSongs.mapIndexed { i, t ->
                                        TrackEntity(
                                            id = "spotify_${t.id}",
                                            playlistId = "recommendations",
                                            spotifyTrackId = t.id,
                                            name = t.name,
                                            artists = t.getArtistNames(),
                                            youtubeVideoId = null,
                                            audioUrl = null,
                                            position = i,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }

                                    items(recommendedTrackEntities.size) { index ->
                                        val t = recommendedSongs[index]
                                        val entity = recommendedTrackEntities[index]
                                        val songListItem = Song(
                                            number = index + 1,
                                            title = t.name,
                                            artist = t.getArtistNames(),
                                            spotifyId = t.id,
                                            spotifyUrl = "https://open.spotify.com/track/${t.id}"
                                        )
                                        val isPlaying = currentPlayingTrack?.spotifyTrackId == t.id

                                        SongListItem(
                                            song = songListItem,
                                            trackEntities = recommendedTrackEntities,
                                            index = index,
                                            playerViewModel = playerViewModel,
                                            coroutineScope = coroutineScope,
                                            modifier = Modifier.fillMaxWidth(),
                                            isCurrentlyPlaying = isPlaying,
                                            onLikedStatusChanged = {
                                                // si se modifica liked desde aquí, recargar liked songs
                                                loadLikedSongs()
                                            }
                                        )
                                    }
                                }

                                // Mostrar indicador de carga
                                if (isLoadingRecommendations) {
                                    item {
                                        Spacer(Modifier.height(24.dp))
                                        Text(
                                            text = Translations.get(context, "loading_recommendations"),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.tertiary
                                            ),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                }

                                // Mostrar error si existe
                                recommendationError?.let {
                                    item {
                                        Spacer(Modifier.height(24.dp))
                                        Text(
                                            text = "⚠ Error loading similar: $it",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
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
                            item = if (isViewingAlbumFromArtist && selectedPlaylist != null) {
                                // Es un álbum visto desde un artista
                                ShareableItem(
                                    spotifyId = selectedPlaylist!!.id,
                                    spotifyUrl = "https://open.spotify.com/album/${selectedPlaylist!!.id}",
                                    youtubeId = null,
                                    title = selectedPlaylist!!.name,
                                    artist = selectedPlaylist!!.description ?: "Album",
                                    type = ShareType.ALBUM
                                )
                            } else if (selectedArtist != null) {
                                // Es un artista
                                ShareableItem(
                                    spotifyId = selectedArtist!!.id,
                                    spotifyUrl = "https://open.spotify.com/artist/${selectedArtist!!.id}",
                                    youtubeId = null,
                                    title = selectedArtist!!.name,
                                    artist = selectedArtist!!.genres?.joinToString(", ") ?: "Artist",
                                    type = ShareType.ARTIST
                                )
                            } else {
                                // Es una playlist
                                ShareableItem(
                                    spotifyId = selectedPlaylist!!.id,
                                    spotifyUrl = "https://open.spotify.com/playlist/${selectedPlaylist!!.id}",
                                    youtubeId = null,
                                    title = selectedPlaylist!!.name,
                                    artist = "Playlist",
                                    type = ShareType.PLAYLIST
                                )
                            },
                            onDismiss = { showShareDialog = false }
                        )
                    }
                }
            }


        // Diálogo de recorte de portada (solo playlists locales youtube_ en modo edición)
        coverPickUri?.let { uri ->
            val entity = selectedPlaylistEntity
            if (entity != null && isEditing && entity.spotifyId.startsWith("youtube_")) {
                CoverCropDialog(
                    uri = uri,
                    onDismiss = { coverPickUri = null },
                    onConfirm = { cropped ->
                        coverPickUri = null
                        coroutineScope.launch {
                            val path = CoverImageManager.save(
                                context,
                                entity.spotifyId.removePrefix("youtube_"),
                                cropped
                            )
                            if (path != null) {
                                localRepository.updatePlaylistImage(entity.spotifyId, path)
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
    var isPublic by remember { mutableStateOf(true) }
    var playlistType by remember { mutableStateOf(0) } // 0 = Spotify, 1 = YouTube
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Estados para el buscador de canciones
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var selectedTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }

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
        MultiToggle(
            options = listOf("spotify", "youtube"),
            initialIndex = 0,
            onChange = { index -> playlistType = index }
        )
        if (playlistType == 0) {
            Spacer(Modifier.height(8.dp))
            MultiToggle(
                options = listOf("public", "private"),
                initialIndex = if (isPublic) 0 else 1,
                onChange = { index -> isPublic = (index == 0) }
            )
        }
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
                        if (playlistType == 1) {
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
                                        SpotifyTrack(
                                            id = video.videoId,
                                            name = video.title,
                                            artists = listOf(SpotifyArtist(video.uploader))
                                        )
                                    }
                                } else {
                                    error = "YouTube search failed"
                                }
                            }
                        } else {
                            val accessToken = Config.getSpotifyAccessToken(context)
                            if (accessToken != null) {
                                coroutineScope.launch {
                                    SpotifyRepository.searchAll(accessToken, searchQuery) { results, errorMsg ->
                                        isSearching = false
                                        if (results != null) {
                                            searchResults = results.tracks.items
                                        } else {
                                            error = errorMsg
                                        }
                                    }
                                }
                            } else {
                                isSearching = false
                                error = "Spotify not connected"
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
                    id = "spotify_search_${track.id}_$trackIndex",
                    playlistId = "spotify_search_${System.currentTimeMillis()}",
                    spotifyTrackId = track.id,
                    name = track.name,
                    artists = track.getArtistNames(),
                    youtubeVideoId = null,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }

            searchResults.take(10).forEachIndexed { index, track ->
                val isPlaying = currentPlayingTrack?.spotifyTrackId == track.id
                SongListItem(
                    song = Song(
                        number = index + 1,
                        title = track.name,
                        artist = track.getArtistNames(),
                        youtubeId = track.id,
                        spotifyUrl = "https://open.spotify.com/track/${track.id}"
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
                    id = "spotify_search_${track.id}_$trackIndex",
                    playlistId = "spotify_search_${System.currentTimeMillis()}",
                    spotifyTrackId = track.id,
                    name = track.name,
                    artists = track.getArtistNames(),
                    youtubeVideoId = null,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }

            selectedTracks.forEachIndexed { index, track ->
                val isPlaying = currentPlayingTrack?.spotifyTrackId == track.id
                SongListItem(
                    song = Song(
                        number = index + 1,
                        title = track.name,
                        artist = track.getArtistNames(),
                        youtubeId = track.id,
                        spotifyUrl = "https://open.spotify.com/track/${track.id}"
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
                    if (playlistType == 1) {
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
                    } else {
                        val accessToken = Config.getSpotifyAccessToken(context)
                        if (accessToken != null) {
                            val trackIds = selectedTracks.map { it.id }
                            SpotifyRepository.createPlaylist(
                                accessToken,
                                playlistName,
                                playlistDesc,
                                isPublic,
                                trackIds
                            ) { success, errMsg ->
                                isLoading = false
                                if (success) {
                                    // Sincronizar playlists después de crear
                                    coroutineScope.launch {
                                        localRepository.syncPlaylistsFromSpotify()
                                    }
                                    onPlaylistCreated()
                                } else {
                                    error = errMsg ?: "Unknown error"
                                }
                            }
                        } else {
                            isLoading = false
                            error = "Spotify not connected"
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

@Composable
fun SpotifyPlaylistDetailView(
    playlist: SpotifyPlaylist,
    tracks: List<SpotifyTrack>,
    trackEntities: List<TrackEntity>? = null,
    isLoading: Boolean,
    error: String?,
    onStart: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    var showShareDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Titulo(playlist.name)

        // Botones de acción (usar tokens del tema en lugar de hex literals)
        ActionButtonsGroup(
            buttons = listOf(
                ActionButtonData(">", MaterialTheme.colorScheme.primary, onStart, tracks.isNotEmpty()),
                ActionButtonData("<rnd>", MaterialTheme.colorScheme.tertiary, onRandom, tracks.isNotEmpty()),
                ActionButtonData("<save>", MaterialTheme.colorScheme.secondary, onSave, true),
                ActionButtonData("<share>", MaterialTheme.colorScheme.error, { showShareDialog = true }, true)
            )
        )

        // Estados de carga y error
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "$ loading tracks...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                )
            }
        }

        error?.let {
            Text(
                "ERR: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        //listado canciones
        if (tracks.isNotEmpty()) {
            SongList(
                playlist = playlist,
                tracks = tracks,
                trackEntities = trackEntities,
                playerViewModel = playerViewModel,
                coroutineScope = coroutineScope
            )
        }
    }

    if (showShareDialog) {
        ShareDialog(
            item = ShareableItem(
                spotifyId = playlist.id,
                spotifyUrl = "https://open.spotify.com/playlist/${playlist.id}",
                youtubeId = null,
                title = playlist.name,
                artist = "Playlist",
                type = ShareType.PLAYLIST
            ),
            onDismiss = { showShareDialog = false }
        )
    }
}
