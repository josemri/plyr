package com.plyr.ui

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.plyr.utils.Config
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.service.YouTubeSearchManager
import com.plyr.ui.components.Song
import com.plyr.ui.components.SongListItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope



@Composable
fun PlaylistsScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val haptic = LocalHapticFeedback.current
    var dragOffsetX by remember { mutableStateOf(0f) }

    // Repositorio local y manager de búsqueda
    val localRepository = remember { PlaylistLocalRepository(context) }
    val youtubeSearchManager = remember { YouTubeSearchManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Estado para las playlists y autenticación
    val playlistsFromDB by localRepository.getAllPlaylistsLiveData().asFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSpotifyConnected by remember { mutableStateOf(Config.isSpotifyConnected(context)) }
    var isSyncing by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }

    // Convertir entidades a SpotifyPlaylist para compatibilidad con UI existente
    val playlists = playlistsFromDB.map { it.toSpotifyPlaylist() }

    // Estado para mostrar tracks de una playlist
    var selectedPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var selectedPlaylistEntity by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var isLoadingTracks by remember { mutableStateOf(false) }
    var isSearchingYouTubeIds by remember { mutableStateOf(false) }
    var showCreatePlaylistScreen by remember { mutableStateOf(false) }

    // Tracks observados desde la base de datos
    val tracksFromDB by if (selectedPlaylistEntity != null) {
        localRepository.getTracksByPlaylistLiveData(selectedPlaylistEntity!!.spotifyId)
            .asFlow()
            .collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { mutableStateOf(emptyList<TrackEntity>()) }
    }

    // Actualizar tracks cuando cambien en la DB
    LaunchedEffect(tracksFromDB) {
        if (selectedPlaylistEntity != null) {
            playlistTracks = tracksFromDB.map { it.toSpotifyTrack() }
        }
    }

    // Función para cargar playlists con sincronización automática
    val loadPlaylists = {
        if (!isSpotifyConnected) {
            error = "Spotify no está conectado"
        } else {
            isLoading = true
            error = null

            // Usar corrutina para operaciones asíncronas
            coroutineScope.launch {
                try {
                    val playlistEntities = localRepository.getPlaylistsWithAutoSync()
                    isLoading = false
                    // Las playlists se actualizan automáticamente a través del LiveData
                } catch (e: Exception) {
                    isLoading = false
                    error = "Error cargando playlists: ${e.message}"
                }
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
            error = "Playlist no encontrada en base de datos local"
        } else {
            // Usar corrutina para operaciones asíncronas
            coroutineScope.launch {
                try {
                    val trackEntities = localRepository.getTracksWithAutoSync(playlist.id)
                    isLoadingTracks = false
                    // Los tracks se actualizan automáticamente a través del LiveData

                    // NOTA: Ya no se necesita búsqueda masiva de YouTube IDs
                    // Los IDs se obtienen automáticamente cuando el usuario hace click en cada canción
                    Log.d("PlaylistScreen", "✅ Tracks cargados para playlist: ${playlist.name}. IDs de YouTube se obtendrán bajo demanda.")
                } catch (e: Exception) {
                    isLoadingTracks = false
                    error = "Error cargando tracks: ${e.message}"
                }
            }
        }
    }

    // Función para forzar sincronización completa
    val forceSyncAll = {
        if (!isSpotifyConnected) {
            error = "Spotify no está conectado"
        } else {
            isSyncing = true
            error = null

            coroutineScope.launch {
                try {
                    val success = localRepository.forceSyncAll()
                    isSyncing = false
                    if (!success) {
                        error = "Error en la sincronización"
                    }
                } catch (e: Exception) {
                    isSyncing = false
                    error = "Error en sincronización: ${e.message}"
                }
            }
        }
    }

    // Cargar playlists al iniciar si está conectado
    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected) {
            loadPlaylists()
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
        if (selectedPlaylist != null) {
            selectedPlaylist = null
            playlistTracks = emptyList()
        } else {
            onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (showCreatePlaylistScreen) {
            CreateSpotifyPlaylistScreen(
                onBack = { showCreatePlaylistScreen = false },
                onPlaylistCreated = { showCreatePlaylistScreen = false; loadPlaylists() }
            )
            return@Column
        }
        // Header
        Text(
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

        when {
            !isSpotifyConnected -> {
                // Estado no conectado
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

                    // Función para parar todas las reproducciones
                    fun stopAllPlayback() {
                        isRandomizing = false
                        isStarting = false
                        randomJob?.cancel()
                        startJob?.cancel()
                        randomJob = null
                        startJob = null
                        // Cancelar espera de canción y pausar el reproductor
                        playerViewModel?.cancelWaitForSong()
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

                                if (trackEntity != null && playerViewModel != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel?.initializePlayer()

                                    // Establecer la playlist completa con el track aleatorio seleccionado
                                    val currentTrackIndex = tracksFromDB.indexOf(trackEntity)
                                    if (currentTrackIndex >= 0) {
                                        playerViewModel?.setCurrentPlaylist(tracksFromDB, currentTrackIndex)
                                    }

                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel?.loadAudioFromTrack(trackEntity)
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

                                if (trackEntity != null && playerViewModel != null) {
                                    // Reproducir la canción usando PlayerViewModel
                                    playerViewModel?.initializePlayer()

                                    // Establecer la playlist completa desde el inicio (índice 0)
                                    val trackEntityIndex = tracksFromDB.indexOf(trackEntity)
                                    if (trackEntityIndex >= 0) {
                                        playerViewModel?.setCurrentPlaylist(tracksFromDB, trackEntityIndex)
                                    }

                                    // Cargar y reproducir - PlayerViewModel manejará la navegación automática
                                    playerViewModel?.loadAudioFromTrack(trackEntity)
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
                            // Botón <edit>
                            Text(
                                text = if (isEditing) "<done>" else "<edit>",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    color = if (isEditing) Color(0xFF4ECDC4) else Color(0xFF95A5A6)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        isEditing = !isEditing
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(8.dp)
                            )
                        }
                        if (isEditing) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Botón para añadir canción
                                Text(
                                    text = "<add song>",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = Color(0xFF4ECDC4)
                                    ),
                                    modifier = Modifier
                                        .clickable { /* TODO: lógica para añadir canción */ }
                                        .padding(8.dp)
                                )
                                // Botón para eliminar canción
                                Text(
                                    text = "<remove song>",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = Color(0xFFFF6B6B)
                                    ),
                                    modifier = Modifier
                                        .clickable { /* TODO: lógica para eliminar canción */ }
                                        .padding(8.dp)
                                )
                                // Cambiar título
                                var newTitle by remember { mutableStateOf(selectedPlaylist?.name ?: "") }
                                OutlinedTextField(
                                    value = newTitle,
                                    onValueChange = { newTitle = it },
                                    label = { Text("Playlist Title") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "<save title>",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = Color(0xFF4ECDC4)
                                    ),
                                    modifier = Modifier
                                        .clickable { /* TODO: lógica para guardar título */ }
                                        .padding(8.dp)
                                )
                                // Cambiar descripción
                                var newDesc by remember { mutableStateOf(selectedPlaylist?.description ?: "") }
                                OutlinedTextField(
                                    value = newDesc,
                                    onValueChange = { newDesc = it },
                                    label = { Text("Playlist Description") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "<save desc>",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        color = Color(0xFF4ECDC4)
                                    ),
                                    modifier = Modifier
                                        .clickable { /* TODO: lógica para guardar descripción */ }
                                        .padding(8.dp)
                                )
                            }
                        }
                        // Lista de tracks
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Prepara trackEntities una sola vez
                            val trackEntitiesList = tracksFromDB
                            items(playlistTracks.size) { index ->
                                val track = playlistTracks[index]
                                val song = Song(
                                    number = index + 1,
                                    title = track.name ?: "Sin título",
                                    artist = track.getArtistNames()
                                )
                                SongListItem(
                                    song = song,
                                    trackEntities = trackEntitiesList,
                                    index = index,
                                    playerViewModel = playerViewModel,
                                    coroutineScope = coroutineScope,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
                            items(playlists.size) { index ->
                                val playlist = playlists[index]
                                val playlistEntity = playlistsFromDB.find { it.spotifyId == playlist.id }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPlaylist = playlist
                                            loadPlaylistTracks(playlist)
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

                                    // Nombre de la playlist (opcional, se puede quitar si solo quieres las portadas)
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
    onPlaylistCreated: () -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var playlistDesc by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    BackHandler {
        onBack()
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "$ create_playlist",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // Opción Public
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { isPublic = true }
                    .padding(8.dp)
            ) {
                Text(
                    text = "public",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }
            // Opción Private
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { isPublic = false }
                    .padding(8.dp)
            ) {
                Text(
                    text = "private",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (!isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
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
                    // Acción de crear playlist
                    isLoading = true
                    error = null
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        SpotifyRepository.createPlaylist(
                            accessToken,
                            playlistName,
                            playlistDesc,
                            isPublic
                        ) { success, errMsg ->
                            isLoading = false
                            if (success) onPlaylistCreated() else error = errMsg ?: "Unknown error"
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
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRandom: () -> Unit,
    onSave: () -> Unit,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header con botón de retroceso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ${playlist.name}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Botones de acción
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            ActionButton(
                text = "<start>",
                color = Color(0xFF4ECDC4),
                onClick = onStart,
                enabled = tracks.isNotEmpty()
            )
            ActionButton(
                text = "<rand>",
                color = Color(0xFFFFD93D),
                onClick = onRandom,
                enabled = tracks.isNotEmpty()
            )
            ActionButton(
                text = "<save>",
                color = Color(0xFF7FB069),
                onClick = onSave,
                enabled = true
            )
        }

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

        // Lista de tracks
        if (tracks.isNotEmpty()) {
            // Crear trackEntities una sola vez fuera del LazyColumn
            val trackEntities = tracks.mapIndexed { trackIndex, spotifyTrack ->
                TrackEntity(
                    id = "spotify_${spotifyTrack.id}",
                    playlistId = playlist.id,
                    spotifyTrackId = spotifyTrack.id,
                    name = spotifyTrack.name,
                    artists = spotifyTrack.getArtistNames(),
                    youtubeVideoId = null,
                    audioUrl = null,
                    position = trackIndex,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(tracks.size) { index ->
                    val track = tracks[index]
                    val song = Song(
                        number = index + 1,
                        title = track.name ?: "Sin título",
                        artist = track.getArtistNames()
                    )
                    SongListItem(
                        song = song,
                        trackEntities = trackEntities,
                        index = index,
                        playerViewModel = playerViewModel,
                        coroutineScope = coroutineScope,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = if (enabled) color else Color(0xFF95A5A6)
        ),
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(8.dp)
    )
}
