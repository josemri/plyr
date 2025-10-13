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
import androidx.compose.ui.graphics.Color
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
import coil.compose.AsyncImage
import com.plyr.database.*
import com.plyr.network.SpotifyPlaylist
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyTrack
import com.plyr.network.SpotifyAlbum
import com.plyr.network.SpotifyArtistFull
import com.plyr.utils.Config
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import com.plyr.ui.components.ShareDialog
import com.plyr.ui.components.ShareableItem
import com.plyr.ui.components.ShareType
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi

//botones de accion
import com.plyr.ui.components.*



@OptIn(DelicateCoroutinesApi::class)
@Composable
fun PlaylistsScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val haptic = LocalHapticFeedback.current

    // Repositorio local y manager de búsqueda
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()

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
    var likedSongsCount by remember { mutableStateOf(0) }

    // Actualizar contador de Liked Songs
    LaunchedEffect(likedSongsPlaylist) {
        likedSongsCount = likedSongsPlaylist.size
    }

    // Estado para álbumes guardados
    var savedAlbums by remember { mutableStateOf<List<SpotifyAlbum>>(emptyList()) }
    var isLoadingSavedAlbums by remember { mutableStateOf(false) }
    var savedAlbumsCount by remember { mutableStateOf(0) }

    // Estado para artistas seguidos
    var followedArtists by remember { mutableStateOf<List<SpotifyArtistFull>>(emptyList()) }
    var isLoadingFollowedArtists by remember { mutableStateOf(false) }
    var followedArtistsCount by remember { mutableStateOf(0) }

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

    // Estado para los álbumes del artista seleccionado
    var selectedArtist by remember { mutableStateOf<SpotifyArtistFull?>(null) }
    var artistAlbums by remember { mutableStateOf<List<SpotifyAlbum>>(emptyList()) }
    var isLoadingArtistAlbums by remember { mutableStateOf(false) }

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
            // Usar corrutina para operaciones asíncronas
            coroutineScope.launch {
                localRepository.getTracksWithAutoSync(playlist.id)
                isLoadingTracks = false
            }
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

    // Manejar botón de retroceso del sistema (modificar para que al salir de album de artista vuelva a artista no a lista principal)
    BackHandler {
        if (selectedPlaylist != null) {
            // Si estamos en modo edición con cambios sin guardar, mostrar diálogo
            if (isEditing && hasUnsavedChanges) {
                showExitEditDialog = true
            } else {
                // Salir de la playlist y resetear modo edición
                isEditing = false
                hasUnsavedChanges = false
                selectedPlaylist = null
                playlistTracks = emptyList()
                // Limpiar artista y sus álbumes al salir
                selectedArtist = null
                artistAlbums = emptyList()
            }
        } else {
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
            CreateSpotifyPlaylistScreen(
                onBack = { showCreatePlaylistScreen = false },
                onPlaylistCreated = { showCreatePlaylistScreen = false; loadPlaylists() },
                playerViewModel = playerViewModel
            )
            return@Column
        }
        // Header
        Text( //TITULO PRINCIPAL (modificar para que use themes)
            text = if (selectedPlaylist == null) "$ plyr_lists" else "$ ${selectedPlaylist!!.name}",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Botón de sincronización manual (solo visible si está conectado y no es una playlist individual)
        if (isSpotifyConnected && selectedPlaylist == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botón de sincronización
                Text(
                    text = if (isSyncing) "<syncing...>" else "<sync>",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (isSyncing) Color(0xFFFFD93D) else Color(0xFF4ECDC4)
                    ),
                    modifier = Modifier
                        .clickable(enabled = !isSyncing) {
                            forceSyncAll()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(8.dp)
                )
                // New button
                Text(
                    text = "<new>",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF4ECDC4)
                    ),
                    modifier = Modifier
                        .clickable(enabled = !isSyncing) {
                            // Set state to show create playlist screen
                            showCreatePlaylistScreen = true
                        }
                        .padding(8.dp)
                )

                // Indicador de estado
                Text(
                    text = when {
                        isSyncing -> "Sincronizando..."
                        playlists.isNotEmpty() -> "${playlists.size} playlists"
                        else -> "Sin datos locales"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when { // Estado no conectado
            !isSpotifyConnected -> {

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "● ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFFF6B6B)
                        )
                    )
                    Text(
                        text = "$ spotify_not_connected",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Conecta tu cuenta en Config primero",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }

            selectedPlaylist != null -> {
                // Vista de tracks de playlist
                if (isLoadingTracks) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "● ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFD93D)
                            )
                        )
                        Text(
                            text = "$ loading_tracks...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = Color(0xFF95A5A6)
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


                    // Función para randomización simplificada - solo reproduce un track aleatorio
                    fun startRandomizing() {
                        stopAllPlayback()
                        isRandomizing = true

                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            randomJob = GlobalScope.launch {
                                val randomTrack = playlistTracks.random()
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == randomTrack.id }

                                println("� RANDOM: ${randomTrack.getDisplayName()}")

                                if (trackEntity != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel.initializePlayer()

                                    // Establecer la playlist completa con el track aleatorio seleccionado
                                    val currentTrackIndex = tracksFromDB.indexOf(trackEntity)
                                    if (currentTrackIndex >= 0) {
                                        playerViewModel.setCurrentPlaylist(tracksFromDB, currentTrackIndex)
                                    }

                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel.loadAudioFromTrack(trackEntity)
                                } else {
                                    println("⚠️ TrackEntity no encontrado para: ${randomTrack.getDisplayName()}")
                                }

                                isRandomizing = false
                            }
                        }
                    }

                    // Función para reproducción ordenada simplificada - solo inicia desde el primer track
                    fun startOrderedPlayback() {
                        stopAllPlayback()
                        isStarting = true

                        if (playlistTracks.isNotEmpty() && playerViewModel != null) {
                            startJob = GlobalScope.launch {
                                val firstTrack = playlistTracks.first()
                                val trackEntity = tracksFromDB.find { it.spotifyTrackId == firstTrack.id }

                                println("🎵 START [${selectedPlaylist!!.name}]: ${firstTrack.getDisplayName()}")

                                if (trackEntity != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel.initializePlayer()

                                    // Establecer la playlist completa desde el inicio (índice 0)
                                    val trackEntityIndex = tracksFromDB.indexOf(trackEntity)
                                    if (trackEntityIndex >= 0) {
                                        playerViewModel.setCurrentPlaylist(tracksFromDB, trackEntityIndex)
                                    }

                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel.loadAudioFromTrack(trackEntity)
                                } else {
                                    println("⚠️ TrackEntity no encontrado para: ${firstTrack.getDisplayName()}")
                                }

                                isStarting = false
                            }
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!isEditing) {
                                // Botones visibles solo cuando NO está en modo edición

                                // Botón <start>
                                Text(
                                    text = if (isStarting) "<stop>" else "<start>",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp,
                                        color = if (isStarting) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            if (isStarting) {
                                                stopAllPlayback()
                                            } else {
                                                startOrderedPlayback()
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        .padding(8.dp)
                                )

                                // Botón <rand>
                                Text(
                                    text = if (isRandomizing) "<stop>" else "<rand>",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp,
                                        color = if (isRandomizing) Color(0xFFFF6B6B) else Color(0xFFFFD93D)
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            if (isRandomizing) {
                                                stopAllPlayback()
                                            } else {
                                                startRandomizing()
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        .padding(8.dp)
                                )

                                // Botón <share>
                                Text(
                                    text = "<share>",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp,
                                        color = Color(0xFFFF6B9D)
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            showShareDialog = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        .padding(8.dp)
                                )
                            }

                            // Botón <edit> o <save>
                            Text(
                                text = if (isEditing) "<save>" else "<edit>",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    color = if (isEditing) Color(0xFF7FB069) else Color(0xFF95A5A6)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        if (isEditing) {
                                            // Al hacer clic en save, verificar si hay cambios sin guardar
                                            if (hasUnsavedChanges) {
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
                                    .padding(8.dp)
                            )

                            // Botón <delete> - solo visible en modo edición
                            if (isEditing) {
                                var showDeleteDialog by remember { mutableStateOf(false) }

                                Text(
                                    text = "<delete>",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 16.sp,
                                        color = Color(0xFFFF6B6B)
                                    ),
                                    modifier = Modifier
                                        .clickable {
                                            showDeleteDialog = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        .padding(8.dp)
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
                                                    color = Color(0xFF4ECDC4)
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
                                                                    // Recargar la lista de playlists
                                                                    loadPlaylists()
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
                                                        color = Color(0xFFFF6B6B)
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
                                                        color = Color(0xFF4ECDC4)
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            }
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
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                // Título de la sección
                                item {
                                    Text(
                                        text = "> edit_playlist",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 16.sp,
                                            color = Color(0xFF4ECDC4)
                                        ),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                // Cambiar nombre
                                item {
                                    OutlinedTextField(
                                        value = newTitle,
                                        onValueChange = { newTitle = it },
                                        label = { Text("Playlist name") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }

                                // Cambiar descripción
                                item {
                                    OutlinedTextField(
                                        value = newDesc,
                                        onValueChange = { newDesc = it },
                                        label = { Text("Description") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }

                                // Sección de buscador de canciones
                                item {
                                    Text(
                                        text = "> add_tracks",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 16.sp,
                                            color = Color(0xFF4ECDC4)
                                        ),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }

                                // Campo de búsqueda
                                item {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = { Text("Search tracks") },
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
                                                color = Color(0xFFFFD93D)
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
                                                color = Color(0xFFE0E0E0)
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
                                            customButtonIcon = "+",
                                            customButtonAction = {
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
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // Mostrar error si hay
                                editError?.let {
                                    item {
                                        Spacer(Modifier.height(8.dp))
                                        Text("Error: $it", color = Color.Red, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
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
                                                color = Color(0xFF4ECDC4)
                                            )
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }

                                    items(playlistTracks.size) { index ->
                                        val track = playlistTracks[index]
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
                                            customButtonIcon = "x",
                                            customButtonAction = {
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
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Prepara trackEntities - si no hay en DB, crear temporales
                                val trackEntitiesList = if (tracksFromDB.isNotEmpty()) {
                                    tracksFromDB
                                } else {
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
                                    SongListItem(
                                        song = song,
                                        trackEntities = trackEntitiesList,
                                        index = index,
                                        playerViewModel = playerViewModel,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier.fillMaxWidth(),
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
                                            text = "> albums",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF4ECDC4)
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
                                                                        // Limpiar artista y sus álbumes al salir
                                                                        selectedArtist = null
                                                                        artistAlbums = emptyList()
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
                                                        contentDescription = "Album cover",
                                                        modifier = Modifier
                                                            .size(120.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    )

                                                    Text(
                                                        text = album.name,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            color = Color(0xFFE0E0E0)
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
                                        color = Color(0xFF4ECDC4)
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
                                            color = Color(0xFFFF6B6B)
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
                                            color = Color(0xFF4ECDC4)
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
                                spotifyId = selectedPlaylist!!.id,
                                spotifyUrl = "https://open.spotify.com/playlist/${selectedPlaylist!!.id}",
                                youtubeId = null,
                                title = selectedPlaylist!!.name,
                                artist = "Playlist", //selectedPlaylist!!.owner?.display_name ?: "Playlist",
                                type = ShareType.PLAYLIST
                            ),
                            onDismiss = { showShareDialog = false }
                        )
                    }
                }
            }

            else -> {
                // Vista principal de playlists
                if (isLoading || isSyncing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isSyncing) "$ syncing_from_spotify..." else "$ loading_playlists...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFFFD93D)
                            )
                        )
                    }
                } else {
                    // Estado cuando no está cargando ni sincronizando
                    if (playlists.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No playlists found",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF95A5A6)
                                )
                            )
                        }
                    } else {
                        // Grilla de portadas de playlists
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Primer item: Liked Songs
                            if (likedSongsCount > 0) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Mostrar las Liked Songs como una playlist especial desde DB
                                                selectedPlaylist = SpotifyPlaylist(
                                                    id = "liked_songs",
                                                    name = "Liked Songs",
                                                    description = "Your favorite tracks on Spotify",
                                                    tracks = com.plyr.network.SpotifyPlaylistTracks(null, likedSongsCount),
                                                    images = null
                                                )
                                                // Buscar la playlist entity de Liked Songs
                                                selectedPlaylistEntity = playlistsFromDB.find { it.spotifyId == "liked_songs" }
                                                isLoadingTracks = true

                                                // Cargar tracks desde la base de datos
                                                coroutineScope.launch {
                                                    localRepository.getTracksWithAutoSync("liked_songs")
                                                    isLoadingTracks = false
                                                }
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        // Icono de corazón para Liked Songs (en lugar de portada)
                                        Box(
                                            modifier = Modifier
                                                .size(150.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Fondo degradado
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.foundation.Canvas(
                                                    modifier = Modifier.fillMaxSize()
                                                ) {
                                                    drawRect(
                                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color(0xFF4ECDC4),
                                                                Color(0xFF7FB069)
                                                            )
                                                        )
                                                    )
                                                }
                                                // Emoji de corazón
                                                Text(
                                                    text = "♥",
                                                    style = MaterialTheme.typography.displayLarge.copy(
                                                        fontSize = 64.sp,
                                                        color = Color.White
                                                    )
                                                )
                                            }
                                        }

                                        // Nombre de la playlist
                                        Text(
                                            text = "Liked Songs",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFFE0E0E0)
                                            ),
                                            modifier = Modifier.padding(top = 8.dp),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            // Resto de las playlists
                            items(playlists.size) { index ->
                                val playlist = playlists[index]
                                val playlistEntity = playlistsFromDB.find { it.spotifyId == playlist.id }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Verificar si hay cambios sin guardar antes de cambiar de playlist
                                            if (isEditing && hasUnsavedChanges) {
                                                pendingPlaylist = playlist
                                                showExitEditDialog = true
                                            } else {
                                                // Resetear modo edición al cambiar de playlist
                                                isEditing = false
                                                hasUnsavedChanges = false
                                                selectedPlaylist = playlist
                                                loadPlaylistTracks(playlist)
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Portada de la playlist
                                    AsyncImage(
                                        model = playlistEntity?.imageUrl,
                                        contentDescription = "Portada de ${playlist.name}",
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        placeholder = null,
                                        error = null,
                                        fallback = null
                                    )

                                    // Nombre de la playlist
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        ),
                                        modifier = Modifier.padding(top = 8.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            // Álbumes guardados
                            items(savedAlbums.size) { index ->
                                val albumEntity = savedAlbums[index]

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Cargar los tracks del álbum desde Spotify API
                                            isLoadingTracks = true
                                            val accessToken = Config.getSpotifyAccessToken(context)
                                            if (accessToken != null) {
                                                SpotifyRepository.getAlbumTracks(accessToken, albumEntity.id) { tracks, errorMsg ->
                                                    isLoadingTracks = false
                                                    if (tracks != null) {
                                                        // Crear una playlist temporal para mostrar el álbum
                                                        selectedPlaylist = SpotifyPlaylist(
                                                            id = albumEntity.id,
                                                            name = albumEntity.name,
                                                            description = "Album by ${albumEntity.getArtistNames()}",
                                                            tracks = com.plyr.network.SpotifyPlaylistTracks(null, albumEntity.totaltracks ?: tracks.size),
                                                            images = albumEntity.images
                                                        )
                                                        playlistTracks = tracks
                                                        selectedPlaylistEntity = null
                                                    } else {
                                                        Log.e("PlaylistScreen", "Error loading album tracks: $errorMsg")
                                                    }
                                                }
                                            } else {
                                                isLoadingTracks = false
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Portada del álbum
                                    AsyncImage(
                                        model = albumEntity.getImageUrl(),
                                        contentDescription = "Portada de ${albumEntity.name}",
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        placeholder = null,
                                        error = null,
                                        fallback = null
                                    )

                                    // Nombre del álbum
                                    Text(
                                        text = albumEntity.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        ),
                                        modifier = Modifier.padding(top = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )

                                    // Artista del álbum
                                    Text(
                                        text = albumEntity.getArtistNames(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color(0xFF95A5A6)
                                        ),
                                        modifier = Modifier.padding(top = 2.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            // Artistas seguidos
                            items(followedArtists.size) { index ->
                                val artist = followedArtists[index]

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Cargar los tracks y álbumes del artista
                                            isLoadingTracks = true
                                            isLoadingArtistAlbums = true
                                            selectedArtist = artist
                                            val accessToken = Config.getSpotifyAccessToken(context)
                                            if (accessToken != null) {
                                                // Cargar top tracks
                                                SpotifyRepository.getArtistTopTracks(accessToken, artist.id) { tracks, errorMsg ->
                                                    isLoadingTracks = false
                                                    if (tracks != null) {
                                                        // Crear una playlist temporal para mostrar los tracks del artista
                                                        selectedPlaylist = SpotifyPlaylist(
                                                            id = artist.id,
                                                            name = artist.name,
                                                            description = "Top tracks by ${artist.name}",
                                                            tracks = com.plyr.network.SpotifyPlaylistTracks(null, tracks.size),
                                                            images = artist.images
                                                        )
                                                        playlistTracks = tracks
                                                        selectedPlaylistEntity = null
                                                    } else {
                                                        Log.e("PlaylistScreen", "Error loading artist tracks: $errorMsg")
                                                    }
                                                }

                                                // Cargar álbumes del artista
                                                SpotifyRepository.getArtistAlbums(accessToken, artist.id) { albums, errorMsg ->
                                                    isLoadingArtistAlbums = false
                                                    if (albums != null) {
                                                        artistAlbums = albums
                                                        Log.d("PlaylistScreen", "Loaded ${albums.size} albums for ${artist.name}")
                                                    } else {
                                                        Log.e("PlaylistScreen", "Error loading artist albums: $errorMsg")
                                                    }
                                                }
                                            }
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Portada del artista (usar imagen del artista)
                                    AsyncImage(
                                        model = artist.getImageUrl(),
                                        contentDescription = "Artista ${artist.name}",
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(75.dp)),
                                        placeholder = null,
                                        error = null,
                                        fallback = null
                                    )

                                    // Nombre del artista
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFFE0E0E0)
                                        ),
                                        modifier = Modifier.padding(top = 8.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateSpotifyPlaylistScreen(
    onBack: () -> Unit,
    onPlaylistCreated: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    var playlistName by remember { mutableStateOf("") }
    var playlistDesc by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
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

    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Titulo("create_playlist")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = playlistName,
            onValueChange = { playlistName = it },
            label = { Text("Playlist name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = playlistDesc,
            onValueChange = { playlistDesc = it },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        BinaryToggle(
            option1 = "public",
            option2 = "private",
            initialValue = isPublic,
            onChange = { isPublic = it }
        )
        // Campo de búsqueda
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search tracks") },
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
                    color = Color(0xFFFFD93D)
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
                    color = Color(0xFF4ECDC4)
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
        Text(
            text = if (isLoading) "<creating...>" else "<create>",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = if (isLoading) Color(0xFFFFD93D) else Color(0xFF4ECDC4)
            ),
            modifier = Modifier
                .clickable(enabled = !isLoading && playlistName.isNotBlank()) {
                    // Acción de crear playlist con las canciones seleccionadas
                    isLoading = true
                    error = null
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
                .padding(8.dp)
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("Error: $it", color = Color.Red)
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

        // Botones de acción
        ActionButtonsGroup(
            buttons = listOf(
                ActionButtonData("<start>", Color(0xFF4ECDC4), onStart, tracks.isNotEmpty()),
                ActionButtonData("<rand>", Color(0xFFFFD93D), onRandom, tracks.isNotEmpty()),
                ActionButtonData("<save>", Color(0xFF7FB069), onSave, true),
                ActionButtonData("<share>", Color(0xFFFF6B9D), { showShareDialog = true }, true)
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
                        color = Color(0xFFFFD93D)
                    )
                )
            }
        }

        error?.let {
            Text(
                "ERR: $it",
                color = Color(0xFFFF6B6B),
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
