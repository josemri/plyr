package com.plyr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.database.DownloadedTrackEntity
import com.plyr.database.PlaylistDatabase
import com.plyr.database.TrackEntity
import com.plyr.database.LocalPlaylistEntity
import com.plyr.ui.components.*
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.utils.DownloadManager
import kotlinx.coroutines.launch
import android.util.Log
import com.plyr.utils.Translations
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.plyr.service.detectAudioFromUri
import coil.compose.AsyncImage
import androidx.compose.material3.MaterialTheme

@Composable
fun LocalScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var downloadedTracks by remember { mutableStateOf<List<DownloadedTrackEntity>>(emptyList()) }
    var localPlaylists by remember { mutableStateOf<List<LocalPlaylistEntity>>(emptyList()) }
    var selectedLocalPlaylist by remember { mutableStateOf<LocalPlaylistEntity?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<DownloadedTrackEntity>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<DownloadedTrackEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDescription by remember { mutableStateOf("") }

    // Estados para editar playlist
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<LocalPlaylistEntity?>(null) }
    var editPlaylistName by remember { mutableStateOf("") }
    var editPlaylistDescription by remember { mutableStateOf("") }

    // Estados para eliminar playlist
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<LocalPlaylistEntity?>(null) }

    // Estados para añadir canción a playlist
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<DownloadedTrackEntity?>(null) }

    // Estados para importar archivos
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var importTrackName by remember { mutableStateOf("") }
    var importArtistName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableIntStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectionStatus by remember { mutableStateOf<String?>(null) }

    // Launcher para seleccionar archivo de audio
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            // Intentar extraer nombre de archivo
            val fileName = context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
            importTrackName = fileName?.substringBeforeLast(".") ?: ""
            importArtistName = ""
            detectionStatus = null // Resetear estado de detección para nuevo archivo
            showImportDialog = true
        }
    }

    // Handle back button
    BackHandler {
        if (selectedLocalPlaylist != null) {
            selectedLocalPlaylist = null
        } else {
            onBack()
        }
    }

    // Cargar playlists y tracks - Fixed version
    LaunchedEffect(Unit) {
        try {
            val database = PlaylistDatabase.getDatabase(context)

            // Launch separate coroutine for playlists collection
            launch {
                database.localPlaylistDao().getAllLocalPlaylists().collect { playlists ->
                    localPlaylists = playlists
                }
            }

            // Launch separate coroutine for downloaded tracks collection
            launch {
                database.downloadedTrackDao().getAllDownloadedTracks().collect { tracks ->
                    downloadedTracks = tracks
                    isLoading = false
                    Log.d("LocalScreen", "Loaded ${tracks.size} downloaded tracks")
                }
            }
        } catch (e: Exception) {
            error = "Error loading local playlists: ${e.message}"
            Log.e("LocalScreen", "Error loading playlists", e)
            isLoading = false
        }
    }

    // Cargar tracks según la playlist seleccionada
    LaunchedEffect(selectedLocalPlaylist) {
        if (selectedLocalPlaylist == null) {
            // Reset to show all tracks view
            selectedPlaylistTracks = emptyList()
            return@LaunchedEffect
        }

        isLoading = true
        try {
            val database = PlaylistDatabase.getDatabase(context)

            if (selectedLocalPlaylist!!.id == 0L) {
                // "All Tracks" - usar downloadedTracks ya cargados
                selectedPlaylistTracks = downloadedTracks
                isLoading = false
            } else {
                // Mostrar tracks de la playlist seleccionada
                launch {
                    database.localPlaylistDao().getTracksFromLocalPlaylist(selectedLocalPlaylist!!.id).collect { tracks ->
                        selectedPlaylistTracks = tracks
                        isLoading = false
                        Log.d("LocalScreen", "Loaded ${tracks.size} tracks from playlist ${selectedLocalPlaylist!!.name}")
                    }
                }
            }
        } catch (e: Exception) {
            error = "Error loading tracks: ${e.message}"
            Log.e("LocalScreen", "Error loading tracks", e)
            isLoading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Título
        Titulo(
            if (selectedLocalPlaylist == null)
                Translations.get(context, "plyr_local")
            else
                selectedLocalPlaylist!!.name
        )

        // Botones de acción
        if (selectedLocalPlaylist == null) {
            ActionButtonsGroup(
                buttons = listOf(
                    ActionButtonData(
                        text = "<add>",
                        color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                        onClick = {
                            filePickerLauncher.launch("audio/*")
                        }
                    ),
                    ActionButtonData(
                        text = "<new playlist>",
                        color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                        onClick = {
                            showCreatePlaylistDialog = true
                        }
                    )
                )
            )
        } else if (selectedLocalPlaylist!!.id != 0L) {
            // Botones para playlist seleccionada (no "All Tracks")
            ActionButtonsGroup(
                buttons = listOf(
                    ActionButtonData(
                        text = "<add>",
                        color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                        onClick = {
                            filePickerLauncher.launch("audio/*")
                        }
                    ),
                    ActionButtonData(
                        text = "<edit>",
                        color = MaterialTheme.colorScheme.tertiary, // antes Color(0xFFFFB74D)
                        onClick = {
                            playlistToEdit = selectedLocalPlaylist
                            editPlaylistName = selectedLocalPlaylist!!.name
                            editPlaylistDescription = selectedLocalPlaylist!!.description ?: ""
                            showEditPlaylistDialog = true
                        }
                    )
                )
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error ?: Translations.get(context, "unknown_error"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
            selectedLocalPlaylist != null -> {
                // Vista de tracks de playlist seleccionada
                if (selectedPlaylistTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Translations.get(context, "No tracks in playlist"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.outline // antes Color(0xFF95A5A6)
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(selectedPlaylistTracks) { index, track ->
                            val song = Song(
                                number = index + 1,
                                title = track.name,
                                artist = track.artists,
                                spotifyId = track.spotifyTrackId,
                                youtubeId = track.youtubeVideoId
                            )

                            SongListItem(
                                song = song,
                                trackEntities = selectedPlaylistTracks.map { dt ->
                                    TrackEntity(
                                        id = dt.id,
                                        playlistId = "local_${selectedLocalPlaylist!!.id}",
                                        spotifyTrackId = dt.spotifyTrackId,
                                        name = dt.name,
                                        artists = dt.artists,
                                        youtubeVideoId = dt.youtubeVideoId,
                                        audioUrl = dt.localFilePath,
                                        position = selectedPlaylistTracks.indexOf(dt)
                                    )
                                },
                                index = index,
                                playerViewModel = playerViewModel,
                                coroutineScope = coroutineScope,
                                customButtonIcon = "x",
                                customButtonAction = {
                                    trackToDelete = track
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
            localPlaylists.isEmpty() && downloadedTracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    	Text(
                        text = Translations.get(context, "No tracks downloaded"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.outline // antes Color(0xFF95A5A6)
                        )
                    )
                }
            }
            else -> {
                // Grilla de playlists y tracks (igual que PlaylistScreen)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mostrar "All Tracks" como primer item (equivalente a Liked Songs)
                    if (downloadedTracks.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedLocalPlaylist = LocalPlaylistEntity(
                                            id = 0,
                                            name = "All Tracks",
                                            description = "All your local tracks",
                                            imageUrl = null
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // compute gradient colors in composable scope (Canvas's draw lambda is not @Composable)
                                    val allTracksGradientColors = listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        drawRect(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = allTracksGradientColors
                                            )
                                        )
                                    }
                                    Text(
                                        text = "♪",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            fontSize = 64.sp,
                                            color = MaterialTheme.colorScheme.onSurface // antes Color.White
                                        )
                                    )
                                }

                                Text(
                                    text = "All Tracks",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant // antes Color(0xFFE0E0E0)
                                    ),
                                    modifier = Modifier.padding(top = 8.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Playlists locales
                    items(localPlaylists) { playlist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLocalPlaylist = playlist
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Portada de la playlist (placeholder con degradado)
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playlist.imageUrl != null) {
                                    AsyncImage(
                                        model = playlist.imageUrl,
                                        contentDescription = "Portada de ${playlist.name}",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    // Degradado por defecto (compute colors outside draw lambda)
                                    val playlistPlaceholderGradient = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        drawRect(
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = playlistPlaceholderGradient
                                            )
                                        )
                                    }
                                    Text(
                                        text = playlist.name.take(2).uppercase(),
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontSize = 48.sp,
                                            color = MaterialTheme.colorScheme.onSurface, // antes Color.White
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant // antes Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier.padding(top = 8.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Diálogo de confirmación de eliminación de pista
    if (showDeleteDialog && trackToDelete != null) {
        PlyrConfirmDialog(
            title = Translations.get(context, "delete track"),
            message = Translations.get(context, "Song {{track_name}} will be removed permanently").replace("{{track_name}}", trackToDelete?.name ?: ""),
            confirmText = Translations.get(context, "delete"),
            cancelText = "cancel",
            onConfirm = {
                coroutineScope.launch {
                    trackToDelete?.let { track ->
                        try {
                            val database = PlaylistDatabase.getDatabase(context)

                            // Si estamos en una playlist específica (no "All Tracks")
                            if (selectedLocalPlaylist != null && selectedLocalPlaylist!!.id != 0L) {
                                // Solo eliminar la relación de esta playlist
                                database.localPlaylistDao().removeTrackFromPlaylist(
                                    playlistId = selectedLocalPlaylist!!.id,
                                    trackId = track.id
                                )
                                Log.d("LocalScreen", "Track removed from playlist: ${selectedLocalPlaylist!!.name}")
                            } else {
                                // Si estamos en "All Tracks", eliminar completamente el track
                                val success = DownloadManager.deleteDownloadedTrack(context, track)
                                if (success) {
                                    Log.d("LocalScreen", "Track deleted successfully")
                                } else {
                                    error = "Failed to delete track"
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LocalScreen", "Error deleting track", e)
                            error = "Error deleting track: ${e.message}"
                        }
                    }
                    showDeleteDialog = false
                    trackToDelete = null
                }
            },
            onDismiss = {
                showDeleteDialog = false
                trackToDelete = null
            }
        )
    }

    // Diálogo de creación de playlist
    if (showCreatePlaylistDialog) {
        Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, // antes Color(0xFF181818)
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Translations.get(context, "new_playlist"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Nombre de la playlist
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(Translations.get(context, "playlist_name")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF555555)
                            focusedLabelColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedLabelColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            focusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            disabledTextColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant, // antes Color(0xFF333333)
                            disabledLabelColor = MaterialTheme.colorScheme.outline // antes Color(0xFF666666)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Descripción de la playlist (opcional)
                    OutlinedTextField(
                        value = newPlaylistDescription,
                        onValueChange = { newPlaylistDescription = it },
                        label = { Text(Translations.get(context, "description")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF555555)
                            focusedLabelColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedLabelColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            focusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            disabledTextColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant, // antes Color(0xFF333333)
                            disabledLabelColor = MaterialTheme.colorScheme.outline // antes Color(0xFF666666)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    // Botones Crear y Cancelar
                    ActionButtonsGroup(
                        buttons = listOf(
                            ActionButtonData(
                                text = Translations.get(context, "create"),
                                color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                onClick = {
                                    // Crear nueva playlist en la base de datos
                                    coroutineScope.launch {
                                        try {
                                            val database = PlaylistDatabase.getDatabase(context)
                                            val newPlaylist = LocalPlaylistEntity(
                                                id = 0,
                                                name = newPlaylistName,
                                                description = newPlaylistDescription,
                                                imageUrl = null
                                            )
                                            database.localPlaylistDao().insertLocalPlaylist(newPlaylist)
                                            Log.d("LocalScreen", "Playlist creada: $newPlaylistName")
                                            showCreatePlaylistDialog = false
                                            newPlaylistName = ""
                                            newPlaylistDescription = ""
                                        } catch (e: Exception) {
                                            Log.e("LocalScreen", "Error creando playlist", e)
                                        }
                                    }
                                }
                            ),
                            ActionButtonData(
                                text = Translations.get(context, "cancel"),
                                color = MaterialTheme.colorScheme.outline, // antes Color(0xFF95A5A6)
                                onClick = {
                                    showCreatePlaylistDialog = false
                                    newPlaylistName = ""
                                    newPlaylistDescription = ""
                                }
                            )
                        )
                    )
                }
            }
        }
    }

    // Diálogo de edición de playlist
    if (showEditPlaylistDialog && playlistToEdit != null) {
        Dialog(onDismissRequest = { showEditPlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, // antes Color(0xFF181818)
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Translations.get(context, "edit_playlist"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Nombre de la playlist
                    OutlinedTextField(
                        value = editPlaylistName,
                        onValueChange = { editPlaylistName = it },
                        label = { Text(Translations.get(context, "playlist_name")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF555555)
                            focusedLabelColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedLabelColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            focusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            disabledTextColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant, // antes Color(0xFF333333)
                            disabledLabelColor = MaterialTheme.colorScheme.outline // antes Color(0xFF666666)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Descripción de la playlist (opcional)
                    OutlinedTextField(
                        value = editPlaylistDescription,
                        onValueChange = { editPlaylistDescription = it },
                        label = { Text(Translations.get(context, "description")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF555555)
                            focusedLabelColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            unfocusedLabelColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            focusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            disabledTextColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                            disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant, // antes Color(0xFF333333)
                            disabledLabelColor = MaterialTheme.colorScheme.outline // antes Color(0xFF666666)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    // Botones Guardar y Cancelar
                    ActionButtonsGroup(
                        buttons = listOf(
                            ActionButtonData(
                                text = Translations.get(context, "save"),
                                color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                onClick = {
                                    // Guardar cambios en la playlist
                                    coroutineScope.launch {
                                        playlistToEdit?.let { playlist ->
                                            try {
                                                val database = PlaylistDatabase.getDatabase(context)
                                                val updatedPlaylist = playlist.copy(
                                                    name = editPlaylistName,
                                                    description = editPlaylistDescription
                                                )
                                                database.localPlaylistDao().updateLocalPlaylist(updatedPlaylist)
                                                Log.d("LocalScreen", "Playlist actualizada: $editPlaylistName")

                                                // Recargar el nombre y descripción de la playlist seleccionada
                                                selectedLocalPlaylist = updatedPlaylist

                                                showEditPlaylistDialog = false
                                                editPlaylistName = ""
                                                editPlaylistDescription = ""
                                            } catch (e: Exception) {
                                                Log.e("LocalScreen", "Error actualizando playlist", e)
                                            }
                                        }
                                    }
                                }
                            ),
                            ActionButtonData(
                                text = Translations.get(context, "cancel"),
                                color = MaterialTheme.colorScheme.outline, // antes Color(0xFF95A5A6)
                                onClick = {
                                    showEditPlaylistDialog = false
                                    editPlaylistName = ""
                                    editPlaylistDescription = ""
                                }
                            ),
                            ActionButtonData(
                                text = "delete",
                                color = MaterialTheme.colorScheme.error, // antes Color(0xFFFF6B6B)
                                onClick = {
                                    playlistToDelete = playlistToEdit
                                    showDeletePlaylistDialog = true
                                }
                            )
                        )
                    )
                }
            }
        }
    }

    // Diálogo de confirmación de eliminación de playlist
    if (showDeletePlaylistDialog && playlistToDelete != null) {
        PlyrConfirmDialog(
            title = Translations.get(context, "delete_playlist"),
            message = Translations.get(context, "Playlist {{playlist_name}} will be removed permanently").replace("{{playlist_name}}", playlistToDelete?.name ?: ""),
            confirmText = Translations.get(context, "delete"),
            cancelText = "cancel",
            onConfirm = {
                coroutineScope.launch {
                    playlistToDelete?.let { playlist ->
                        val success = DownloadManager.deleteLocalPlaylist(context, playlist)
                        if (success) {
                            Log.d("LocalScreen", "Playlist deleted successfully")
                            // Volver al listado de playlists
                            selectedLocalPlaylist = null
                            showEditPlaylistDialog = false
                        } else {
                            error = "Failed to delete playlist"
                        }
                    }
                    showDeletePlaylistDialog = false
                    playlistToDelete = null
                }
            },
            onDismiss = {
                showDeletePlaylistDialog = false
                playlistToDelete = null
            }
        )
    }

    // Diálogo de añadir a playlist
    if (showAddToPlaylistDialog && trackToAddToPlaylist != null) {
        Dialog(onDismissRequest = { showAddToPlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, // antes Color(0xFF181818)
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = Translations.get(context, "add_to_playlist"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Lista de playlists locales
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localPlaylists.size) { index ->
                            val playlist = localPlaylists[index]
                            // Item de playlist
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Añadir pista a la playlist seleccionada
                                        coroutineScope.launch {
                                            try {
                                                val database = PlaylistDatabase.getDatabase(context)
                                                database.localPlaylistDao().addTrackToPlaylist(
                                                    playlistId = playlist.id,
                                                    trackId = trackToAddToPlaylist!!.id
                                                )
                                                Log.d("LocalScreen", "Track added to playlist: ${playlist.name}")
                                                showAddToPlaylistDialog = false
                                                trackToAddToPlaylist = null
                                            } catch (e: Exception) {
                                                Log.e("LocalScreen", "Error adding track to playlist", e)
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant // antes Color(0xFF252525)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                color = MaterialTheme.colorScheme.onSurface, // antes Color.White
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        if (!playlist.description.isNullOrBlank()) {
                                            Text(
                                                text = playlist.description,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.outline // antes Color(0xFF888888)
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = "+",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = MaterialTheme.colorScheme.secondary // antes Color(0xFF4ECDC4)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Botón Cancelar
                    ActionButtonsGroup(
                        buttons = listOf(
                            ActionButtonData(
                                text = Translations.get(context, "cancel"),
                                color = MaterialTheme.colorScheme.outline, // antes Color(0xFF95A5A6)
                                onClick = {
                                    showAddToPlaylistDialog = false
                                }
                            )
                        )
                    )
                }
            }
        }
    }

    // Diálogo de importación
    if (showImportDialog && selectedFileUri != null) {
        // Animación ASCII para el estado de carga (estilo CAVA con patrones aleatorios)
        val loadingFrames = listOf(
            "▃▇▁▆▂█▄",
            "▆▂▅▁▇▃█",
            "▁▄█▃▆▅▂",
            "▇▅▂▄▁█▃",
            "▂█▆▇▄▁▅",
            "▅▁▃▂▇▄▆",
            "█▃▄▅▂▆▁",
            "▄▆▇▁▅▂█",
            "▃▂▆▄█▇▁",
            "▆▄▁▇▃▅█",
            "▁▇▅█▂▃▄",
            "▇▃█▂▆▁▅"
        )
        var loadingFrame by remember { mutableIntStateOf(0) }

        // Animar el frame de carga
        LaunchedEffect(isDetecting) {
            if (isDetecting) {
                while (isDetecting) {
                    kotlinx.coroutines.delay(100)
                    loadingFrame = (loadingFrame + 1) % loadingFrames.size
                }
            }
        }

        Dialog(onDismissRequest = {
            if (!isImporting && !isDetecting) {
                showImportDialog = false
                selectedFileUri = null
                importTrackName = ""
                importArtistName = ""
                importError = null
            }
        }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface) // antes Color(0xFF181818)
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = Translations.get(context, "import_audio_file"),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface, // antes Color.White
                            fontWeight = FontWeight.Bold
                        )
                    )

                    if (isImporting) {
                        // Mostrar progreso de importación
                        CircularProgressIndicator(
                            progress = { importProgress / 100f },
                            color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "$importProgress%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface // antes Color.White
                            )
                        )
                    } else if (importError != null) {
                        // Mostrar error
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = MaterialTheme.colorScheme.error // antes Color(0xFFFF6B6B)
                            )
                        )
                        Text(
                            text = importError ?: "Error",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.error // antes Color(0xFFFF6B6B)
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ActionButtonsGroup(
                            buttons = listOf(
                                ActionButtonData(
                                    text = Translations.get(context, "close"),
                                    color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                    onClick = {
                                        showImportDialog = false
                                        selectedFileUri = null
                                        importError = null
                                    }
                                )
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        // Formulario de entrada (siempre visible, bloqueado durante detección)
                        OutlinedTextField(
                            value = importTrackName,
                            onValueChange = { importTrackName = it },
                            label = { Text(Translations.get(context, "track_name")) },
                            enabled = !isDetecting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF555555)
                                focusedLabelColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                unfocusedLabelColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                                focusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                                disabledTextColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                                disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant, // antes Color(0xFF333333)
                                disabledLabelColor = MaterialTheme.colorScheme.outline // antes Color(0xFF666666)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = importArtistName,
                            onValueChange = { importArtistName = it },
                            label = { Text(Translations.get(context, "artist_name")) },
                            enabled = !isDetecting,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF555555)
                                focusedLabelColor = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                unfocusedLabelColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                                focusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface, // antes Color.White
                                disabledTextColor = MaterialTheme.colorScheme.outline, // antes Color(0xFF888888)
                                disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant, // antes Color(0xFF333333)
                                disabledLabelColor = MaterialTheme.colorScheme.outline // antes Color(0xFF666666)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Botones Detectar e Importar centrados
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionButtonsGroup(
                                buttons = listOf(
                                    ActionButtonData(
                                        text = when {
                                            isDetecting -> loadingFrames[loadingFrame]
                                            detectionStatus == "detected" -> Translations.get(context, "detected")
                                            detectionStatus == "error" -> Translations.get(context, "error")
                                            else -> Translations.get(context, "detect")
                                        },
                                        color = when {
                                            isDetecting -> MaterialTheme.colorScheme.secondary // antes Color(0xFF4ECDC4)
                                            detectionStatus == "detected" -> MaterialTheme.colorScheme.primary // antes Color(0xFF4CAF50)
                                            detectionStatus == "error" -> MaterialTheme.colorScheme.error // antes Color(0xFFFF6B6B)
                                            else -> MaterialTheme.colorScheme.tertiary // antes Color(0xFFFFB74D)
                                        },
                                        onClick = {
                                            if (!isDetecting && detectionStatus != "detected") {
                                                detectionStatus = null
                                                isDetecting = true
                                                coroutineScope.launch {
                                                    try {
                                                        val detectionResult = detectAudioFromUri(context, selectedFileUri!!)
                                                        if (detectionResult != null) {
                                                            importTrackName = detectionResult.title
                                                            importArtistName = detectionResult.artist
                                                            detectionStatus = "detected"
                                                            Log.d("LocalScreen", "✓ Audio detectado: ${detectionResult.title} - ${detectionResult.artist} (${detectionResult.precision}% precisión)")
                                                        } else {
                                                            detectionStatus = "error"
                                                            Log.w("LocalScreen", "No se pudo detectar el audio")
                                                        }
                                                    } catch (e: Exception) {
                                                        detectionStatus = "error"
                                                        Log.e("LocalScreen", "Error en detección", e)
                                                    } finally {
                                                        isDetecting = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isDetecting && detectionStatus != "detected"
                                    ),
                                    ActionButtonData(
                                        text = Translations.get(context, "import"),
                                        color = MaterialTheme.colorScheme.secondary, // antes Color(0xFF4ECDC4)
                                        onClick = {
                                            if (importTrackName.isNotBlank()) {
                                                isImporting = true
                                                importProgress = 0
                                                importError = null

                                                coroutineScope.launch {
                                                    DownloadManager.importLocalAudioFile(
                                                        context = context,
                                                        uri = selectedFileUri!!,
                                                        trackName = importTrackName,
                                                        artists = importArtistName,
                                                        playlistId = selectedLocalPlaylist?.id,
                                                        onProgress = { progress ->
                                                            importProgress = progress
                                                        },
                                                        onComplete = { success, errorMsg ->
                                                            isImporting = false
                                                            if (success) {
                                                                Log.d("LocalScreen", "✓ Archivo importado exitosamente")
                                                                showImportDialog = false
                                                                selectedFileUri = null
                                                                importTrackName = ""
                                                                importArtistName = ""
                                                            } else {
                                                                importError = errorMsg ?: "Import failed"
                                                                Log.e("LocalScreen", "✗ Error importando: $errorMsg")
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        },
                                        enabled = importTrackName.isNotBlank() && !isDetecting
                                    )
                                ),
                                modifier = Modifier.widthIn(min = 280.dp, max = 280.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
