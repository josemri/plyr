package com.plyr.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import com.plyr.database.TrackEntity
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.SpotifyRepository
import com.plyr.utils.Config
import com.plyr.utils.DownloadManager
import com.plyr.utils.Translations
import com.plyr.database.PlaylistDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.plyr.ui.theme.PlyrSpacing
import com.plyr.ui.theme.PlyrTextStyles

// Data class para unificar los datos de la canción
data class Song(
    val number: Int,
    val title: String,
    val artist: String,
    val spotifyId: String? = null,
    val youtubeId: String? = null,
    val spotifyUrl: String? = null
)

// Helper function para obtener icono y color según la acción
@Composable
private fun getSwipeIconAndColor(action: String): Pair<String, Color> {
    return when (action) {
        Config.SWIPE_ACTION_ADD_TO_QUEUE -> "+" to MaterialTheme.colorScheme.primary
        Config.SWIPE_ACTION_ADD_TO_LIKED -> "♥" to MaterialTheme.colorScheme.error
        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> "≡" to MaterialTheme.colorScheme.tertiary
        Config.SWIPE_ACTION_SHARE -> "⤴" to MaterialTheme.colorScheme.secondary
        Config.SWIPE_ACTION_DOWNLOAD -> "↓" to MaterialTheme.colorScheme.primary
        else -> "?" to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun SongListItem(
    song: Song,
    trackEntities: List<TrackEntity>,
    index: Int,
    playerViewModel: PlayerViewModel?,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isCurrentlyPlaying: Boolean = false, // Indica si esta canción está sonando actualmente
    onLikedStatusChanged: (() -> Unit)? = null,
    customButtonIcon: String? = null, // Nueva: Icono personalizado para el botón (ej: "+")
    customButtonAction: (() -> Unit)? = null // Nueva: Acción personalizada para el botón
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showPopup by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var userPlaylists by remember { mutableStateOf<List<com.plyr.network.SpotifyPlaylist>>(emptyList()) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var addToPlaylistError by remember { mutableStateOf<String?>(null) }
    var addToPlaylistSuccess by remember { mutableStateOf(false) }
    var isLoadingTrackInfo by remember { mutableStateOf(false) }
    var fetchedTrackInfo by remember { mutableStateOf<com.plyr.network.SpotifyTrack?>(null) }
    var fetchInfoError by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf<Boolean?>(null) }

    // Obtener las acciones configuradas y sus iconos/colores
    val swipeRightAction = Config.getSwipeRightAction(context)
    val swipeLeftAction = Config.getSwipeLeftAction(context)
    val (rightIcon, rightColor) = getSwipeIconAndColor(swipeRightAction)
    val (leftIcon, leftColor) = getSwipeIconAndColor(swipeLeftAction)

    // Cargar playlists cuando se abre el diálogo (ya sea desde swipe o desde popup)
    LaunchedEffect(showPlaylistDialog) {
        if (showPlaylistDialog && !isLoadingPlaylists) {
            isLoadingPlaylists = true
            addToPlaylistError = null
            addToPlaylistSuccess = false

            val accessToken = Config.getSpotifyAccessToken(context)
            if (accessToken != null) {
                SpotifyRepository.getUserPlaylists(accessToken) { playlists, error ->
                    isLoadingPlaylists = false
                    if (playlists != null) {
                        userPlaylists = playlists
                    } else {
                        addToPlaylistError = error ?: "Error cargando playlists"
                    }
                }
            } else {
                isLoadingPlaylists = false
                addToPlaylistError = "Token de Spotify no disponible"
            }
        }
    }

    // Swipe gesture state
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 30.dp.toPx() } // Umbral muy bajo, solo para detectar intención

    // Reset swipe position
    fun resetSwipe() {
        coroutineScope.launch {
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        // Background actions - Right swipe (like/favorite)
        if (offsetX.value > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { offsetX.value.toDp() })
                    .background(Color.Transparent),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = rightIcon,
                    color = rightColor,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        // Background actions - Left swipe (add to queue)
        if (offsetX.value < 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (-offsetX.value).toDp() })
                    .align(Alignment.CenterEnd)
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = leftIcon,
                    color = leftColor,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Main content (draggable)
        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    offsetX.value > swipeThreshold -> {
                                        val action = Config.getSwipeRightAction(context)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        executeSwipeAction(
                                            action = action,
                                            song = song,
                                            context = context,
                                            playerViewModel = playerViewModel,
                                            trackEntities = trackEntities,
                                            index = index,
                                            coroutineScope = coroutineScope,
                                            onLikedStatusChanged = onLikedStatusChanged,
                                            onShowPlaylistDialog = { showPlaylistDialog = true },
                                            onShowShareDialog = { showShareDialog = true }
                                        )
                                        resetSwipe()
                                    }
                                    offsetX.value < -swipeThreshold -> {
                                        val action = Config.getSwipeLeftAction(context)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        executeSwipeAction(
                                            action = action,
                                            song = song,
                                            context = context,
                                            playerViewModel = playerViewModel,
                                            trackEntities = trackEntities,
                                            index = index,
                                            coroutineScope = coroutineScope,
                                            onLikedStatusChanged = onLikedStatusChanged,
                                            onShowPlaylistDialog = { showPlaylistDialog = true },
                                            onShowShareDialog = { showShareDialog = true }
                                        )
                                        resetSwipe()
                                    }
                                    else -> {
                                        // Return to center
                                        resetSwipe()
                                    }
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                val newValue = (offsetX.value + dragAmount).coerceIn(-200f, 150f)
                                offsetX.snapTo(newValue)
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetX.value.absoluteValue < 10f) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        playerViewModel?.let { viewModel ->
                            if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                viewModel.clearPlayerState()

                                viewModel.setCurrentPlaylist(trackEntities, index)
                                val selectedTrackEntity = trackEntities[index]
                                coroutineScope.launch {
                                    try {
                                        viewModel.loadAudioFromTrack(selectedTrackEntity)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    } else {
                        resetSwipe()
                    }
                }
                .fillMaxWidth()
                .height(32.dp)
                .background(Color.Transparent),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number
            Text(
                text = song.number.toString(),
                style = PlyrTextStyles.trackArtist(),
                modifier = Modifier.padding(end = PlyrSpacing.small, start = 8.dp)
            )
            // Song title and artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = when {
                        isCurrentlyPlaying -> PlyrTextStyles.trackTitle().copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        isSelected -> PlyrTextStyles.selectableOption(true)
                        else -> PlyrTextStyles.trackTitle()
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = if (isCurrentlyPlaying)
                        PlyrTextStyles.trackArtist().copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    else
                        PlyrTextStyles.trackArtist(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 0.dp)
                )
            }
            // Botón personalizable
            IconButton(onClick = {
                if (customButtonAction != null) {
                    customButtonAction()
                } else {
                    showPopup = true
                }
            }, modifier = Modifier.size(32.dp)) {
                Text(
                    text = customButtonIcon ?: "*",
                    style = PlyrTextStyles.menuOption(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Solo mostrar popup si no hay acción personalizada
    if (showPopup && customButtonAction == null) {
        // Cargar información de la canción cuando se abre el popup
        LaunchedEffect(showPopup) {
            if (showPopup) {
                // Resetear estados previos
                isLoadingTrackInfo = false
                fetchInfoError = null
                fetchedTrackInfo = null

                val sId = song.spotifyId
                // Si no hay spotifyId real o es un placeholder (recomendación/temporal), NO llamar a la API
                if (sId == null || sId.isBlank() || sId.startsWith("recommended_") || sId.startsWith("temp_")) {
                    // No intentamos obtener datos desde Spotify para ids placeholder.
                    // Mostrar la info básica (song.title / song.artist) en el diálogo.
                    isLoadingTrackInfo = false
                    fetchInfoError = null
                    isLiked = null
                } else {
                    isLoadingTrackInfo = true
                    fetchInfoError = null
                    val accessToken = Config.getSpotifyAccessToken(context)
                    if (accessToken != null) {
                        // Obtener info del track
                        SpotifyRepository.getTrackInfo(accessToken, sId) { trackInfo, error ->
                            isLoadingTrackInfo = false
                            if (trackInfo != null) {
                                fetchedTrackInfo = trackInfo
                            } else {
                                fetchInfoError = error ?: "Error fetching track info"
                            }
                        }

                        // Verificar si está en Liked Songs
                        SpotifyRepository.checkSavedTrack(accessToken, sId) { liked, error ->
                            if (error == null) {
                                isLiked = liked
                            }
                        }
                    } else {
                        isLoadingTrackInfo = false
                        fetchInfoError = "Token de Spotify no disponible"
                    }
                }
            }
        }

        Dialog(onDismissRequest = {
            showPopup = false
            fetchedTrackInfo = null
            fetchInfoError = null
        }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Sección de información del track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        when {
                            isLoadingTrackInfo -> {
                                // Estado de carga
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = Translations.get(context, "loading"),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                            fetchInfoError != null -> {
                                // Error
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "✗",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = fetchInfoError ?: "Error",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.error
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            fetchedTrackInfo != null -> {
                                // Mostrar información de la canción
                                val trackInfo = fetchedTrackInfo
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item {
                                        Text(
                                            text = trackInfo?.name ?: song.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    item {
                                        Text(
                                            text = trackInfo?.artists?.joinToString(", ") { it.name } ?: song.artist,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                            )
                                        )
                                    }
                                    trackInfo?.album?.let { album ->
                                        item {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Album: ${album.name}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                                )
                                            )
                                        }
                                        album.releaseDate?.let { date ->
                                            item {
                                                Text(
                                                    text = "Release: $date",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Text(
                                            text = "Duration: ${trackInfo?.durationMs?.let { ms -> 
                                                val minutes = ms / 60000
                                                val seconds = "%02d".format((ms % 60000) / 1000)
                                                "$minutes:$seconds"
                                            } ?: "N/A"}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                }
                            }
                            else -> {
                                // Mostrar info básica mientras carga
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Botones de acción
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Add to Playlist
                        Text(
                            text = Translations.get(context, "add_to_playlist"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isRealSpotifyId(song.spotifyId) && Config.isSpotifyConnected(context)) {
                                        showPopup = false
                                        showPlaylistDialog = true
                                        isLoadingPlaylists = true
                                        addToPlaylistError = null

                                        val accessToken = Config.getSpotifyAccessToken(context)
                                        if (accessToken != null) {
                                            SpotifyRepository.getUserPlaylists(accessToken) { playlists, error ->
                                                isLoadingPlaylists = false
                                                if (playlists != null) {
                                                    userPlaylists = playlists
                                                } else {
                                                    addToPlaylistError = error ?: "Error cargando playlists"
                                                }
                                            }
                                        } else {
                                            isLoadingPlaylists = false
                                            addToPlaylistError = "Token de Spotify no disponible"
                                        }
                                    } else {
                                        Log.d("SongListItem", "No se puede añadir a playlist: spotifyId inválido o no conectado")
                                        showPopup = false
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Add to Queue
                        Text(
                            text = Translations.get(context, "add_to_queue"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPopup = false
                                    playerViewModel?.let { viewModel ->
                                        if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                            val trackToAdd = trackEntities[index]
                                            viewModel.addToQueue(trackToAdd)
                                            Log.d("SongListItem", "✓ Track added to queue: ${trackToAdd.name}")
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Share
                        Text(
                            text = Translations.get(context, "share"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareDialog = true
                                    showPopup = false
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Like / Unlike
                        Text(
                            text = if (isLiked == true) Translations.get(context, "remove_from_liked_songs") else Translations.get(context, "add_to_liked_songs"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Acción de agregar/quitar de Liked Songs
                                    showPopup = false
                                    isLiked?.let { currentlyLiked ->
                                        val accessToken = Config.getSpotifyAccessToken(context)
                                        if (accessToken != null && isRealSpotifyId(song.spotifyId)) {
                                            isLoadingTrackInfo = true
                                            if (currentlyLiked) {
                                                // Quitar de Liked Songs
                                                SpotifyRepository.removeTrack(accessToken, song.spotifyId!!) { success, error ->
                                                    isLoadingTrackInfo = false
                                                    if (success) {
                                                        isLiked = false
                                                        Log.d("SongListItem", "✓ Canción quitada de Liked Songs")
                                                        onLikedStatusChanged?.invoke()
                                                    } else {
                                                        Log.e("SongListItem", "Error quitando canción de Liked Songs: $error")
                                                    }
                                                }
                                            } else {
                                                // Añadir a Liked Songs
                                                SpotifyRepository.saveTrack(accessToken, song.spotifyId!!) { success, error ->
                                                    isLoadingTrackInfo = false
                                                    if (success) {
                                                        isLiked = true
                                                        Log.d("SongListItem", "✓ Canción añadida a Liked Songs")
                                                        onLikedStatusChanged?.invoke()
                                                    } else {
                                                        Log.e("SongListItem", "Error añadiendo canción a Liked Songs: $error")
                                                    }
                                                }
                                            }
                                        } else {
                                            Log.d("SongListItem", "No se puede (des)marcar Liked: spotifyId inválido o no conectado")
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        // Download
                        Text(
                            text = Translations.get(context, "download"),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPopup = false
                                    coroutineScope.launch {
                                        // Obtener el youtubeId
                                        val trackEntity = if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                                            trackEntities[index]
                                        } else null

                                        val initialYoutubeId = trackEntity?.youtubeVideoId ?: song.youtubeId

                                        // Si no tenemos youtubeId, buscarlo
                                        val finalYoutubeId = if (initialYoutubeId == null) {
                                            withContext(Dispatchers.IO) {
                                                val searchQuery = "${song.title} ${song.artist}"
                                                Log.d("SongListItem", "YouTube ID not available, searching with query: '$searchQuery'")
                                                val foundId = com.plyr.network.YouTubeManager.searchVideoId(searchQuery)
                                                Log.d("SongListItem", "YouTube search result: ${if (foundId != null) "Found ID: $foundId" else "NOT FOUND"}")
                                                foundId
                                            }
                                        } else {
                                            Log.d("SongListItem", "Using existing YouTube ID: $initialYoutubeId")
                                            initialYoutubeId
                                        }

                                        if (finalYoutubeId != null) {
                                            // Verificar si ya está descargada usando YouTube ID
                                            val database = PlaylistDatabase.getDatabase(context)
                                            val alreadyDownloaded = database.downloadedTrackDao()
                                                .isTrackDownloadedByYoutubeId(finalYoutubeId) > 0

                                            if (alreadyDownloaded) {
                                                Log.d("SongListItem", "Track already downloaded")
                                            } else {
                                                Log.d("SongListItem", "Starting download: ${song.title}")
                                                DownloadManager.downloadTrack(
                                                    context = context,
                                                    spotifyTrackId = song.spotifyId,
                                                    youtubeVideoId = finalYoutubeId,
                                                    trackName = song.title,
                                                    artists = song.artist,
                                                    onProgress = { progress ->
                                                        Log.d("SongListItem", "Download progress: $progress%")
                                                    },
                                                    onComplete = { success, error ->
                                                        if (success) {
                                                            Log.d("SongListItem", "✓ Download completed: ${song.title}")
                                                        } else {
                                                            Log.e("SongListItem", "✗ Download failed: $error")
                                                        }
                                                    }
                                                )
                                            }
                                        } else {
                                            Log.e("SongListItem", "Cannot download: YouTube video not found for query: '${song.title} ${song.artist}'")
                                        }
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Diálogo de selección de playlist
    if (showPlaylistDialog) {
        Dialog(onDismissRequest = {
            showPlaylistDialog = false
            addToPlaylistSuccess = false
            addToPlaylistError = null
        }) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
                    .fillMaxWidth(0.9f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Contenido del diálogo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        when {
                            isLoadingPlaylists -> {
                                // Estado de carga
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = Translations.get(context, "loading_playlists"),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                            addToPlaylistSuccess -> {
                                // Éxito
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = Translations.get(context, "track_added_successfully"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                }
                            }
                            addToPlaylistError != null -> {
                                // Error
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "✗",
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = addToPlaylistError ?: "Error",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.error
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            userPlaylists.isEmpty() -> {
                                // Sin playlists
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = Translations.get(context, "no_playlists_found"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    )
                                }
                            }
                            else -> {
                                // Lista de playlists
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(userPlaylists) { playlist ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        // Añadir la canción a la playlist
                                                        val accessToken = Config.getSpotifyAccessToken(context)
                                                        if (accessToken != null && isRealSpotifyId(song.spotifyId)) {
                                                            isLoadingPlaylists = true
                                                            SpotifyRepository.addTrackToPlaylist(
                                                                accessToken,
                                                                playlist.id,
                                                                song.spotifyId!!
                                                            ) { success, error ->
                                                                isLoadingPlaylists = false
                                                                if (success) {
                                                                    addToPlaylistSuccess = true
                                                                    Log.d("SongListItem", "✓ Canción añadida a '${playlist.name}'")
                                                                    // Cerrar el diálogo después de 1.5 segundos
                                                                    coroutineScope.launch {
                                                                        kotlinx.coroutines.delay(1500)
                                                                        showPlaylistDialog = false
                                                                        addToPlaylistSuccess = false
                                                                    }
                                                                } else {
                                                                    addToPlaylistError = error
                                                                    Log.e("SongListItem", "Error añadiendo canción: $error")
                                                                }
                                                            }
                                                        }
                                                    }
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = playlist.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    playlist.description?.let { desc ->
                                                        if (desc.isNotBlank()) {
                                                            Text(
                                                                text = desc,
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                ),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = ">",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
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
    }

    if (showShareDialog) {
        ShareDialog(
            item = ShareableItem(
                spotifyId = song.spotifyId,
                spotifyUrl = song.spotifyUrl,
                youtubeId = song.youtubeId,
                title = song.title,
                artist = song.artist,
                type = ShareType.TRACK
            ),
            onDismiss = { showShareDialog = false }
        )
    }
}

fun executeSwipeAction(
    action: String,
    song: Song,
    context: android.content.Context,
    playerViewModel: PlayerViewModel?,
    trackEntities: List<TrackEntity>,
    index: Int,
    coroutineScope: CoroutineScope,
    onLikedStatusChanged: (() -> Unit)?,
    onShowPlaylistDialog: () -> Unit,
    onShowShareDialog: () -> Unit
) {
    when (action) {
        Config.SWIPE_ACTION_ADD_TO_LIKED -> {
            // Añadir a favoritos
            val accessToken = Config.getSpotifyAccessToken(context)
            if (accessToken != null && song.spotifyId != null) {
                Log.d("SongListItem", "Calling saveTrack with spotifyId: ${song.spotifyId}")
                SpotifyRepository.saveTrack(accessToken, song.spotifyId) { success, error ->
                    if (success) {
                        onLikedStatusChanged?.invoke()
                        Log.d("SongListItem", "✓ Canción añadida a favoritos")
                    } else {
                        Log.e("SongListItem", "✗ Error añadiendo a favoritos: $error")
                    }
                }
            } else {
                Log.e("SongListItem", "✗ No se puede añadir a favoritos: accessToken=${accessToken != null}, spotifyId=${song.spotifyId}")
            }
        }
        Config.SWIPE_ACTION_ADD_TO_QUEUE -> {
            // Añadir a cola
            playerViewModel?.let { viewModel ->
                if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                    val trackToAdd = trackEntities[index]
                    viewModel.addToQueue(trackToAdd)
                    Log.d("SongListItem", "✓ Track added to queue: ${trackToAdd.name}")
                } else {
                    Log.e("SongListItem", "✗ Invalid index or empty trackEntities")
                }
            } ?: Log.e("SongListItem", "✗ PlayerViewModel is null")
        }
        Config.SWIPE_ACTION_ADD_TO_PLAYLIST -> {
            // Añadir a playlist
            if (song.spotifyId != null && Config.isSpotifyConnected(context)) {
                onShowPlaylistDialog()
            } else {
                Log.d("SongListItem", "No se puede añadir a playlist: sin Spotify ID o no conectado")
            }
        }
        Config.SWIPE_ACTION_SHARE -> {
            // Compartir
            onShowShareDialog()
        }
        Config.SWIPE_ACTION_DOWNLOAD -> {
            // Descargar
            coroutineScope.launch {
                // Obtener el youtubeId
                val trackEntity = if (trackEntities.isNotEmpty() && index in trackEntities.indices) {
                    trackEntities[index]
                } else null

                val initialYoutubeId = trackEntity?.youtubeVideoId ?: song.youtubeId

                // Si no tenemos youtubeId, buscarlo
                val finalYoutubeId = initialYoutubeId
                    ?: withContext(Dispatchers.IO) {
                        val searchQuery = "${song.title} ${song.artist}"
                        Log.d("SongListItem", "YouTube ID not available, searching with query: '$searchQuery'")
                        com.plyr.network.YouTubeManager.searchVideoId(searchQuery)
                    }

                if (finalYoutubeId != null) {
                    // Verificar si ya está descargada usando YouTube ID
                    val database = PlaylistDatabase.getDatabase(context)
                    val alreadyDownloaded = database.downloadedTrackDao()
                        .isTrackDownloadedByYoutubeId(finalYoutubeId) > 0

                    if (alreadyDownloaded) {
                        Log.d("SongListItem", "Track already downloaded")
                    } else {
                        Log.d("SongListItem", "Starting download: ${song.title}")
                        DownloadManager.downloadTrack(
                            context = context,
                            spotifyTrackId = song.spotifyId,
                            youtubeVideoId = finalYoutubeId,
                            trackName = song.title,
                            artists = song.artist,
                            onProgress = { progress ->
                                Log.d("SongListItem", "Download progress: $progress%")
                            },
                            onComplete = { success, error ->
                                if (success) {
                                    Log.d("SongListItem", "✓ Download completed: ${song.title}")
                                } else {
                                    Log.e("SongListItem", "✗ Download failed: $error")
                                }
                            }
                        )
                    }
                } else {
                    Log.e("SongListItem", "Cannot download: YouTube video not found")
                }
            }
        }
        else -> {
            Log.w("SongListItem", "Acción desconocida para swipe: $action")
        }
    }
}

// Helper para validar si el spotifyId es real (no un placeholder generado localmente)
fun isRealSpotifyId(sId: String?): Boolean {
    return sId != null && sId.isNotBlank() && !sId.startsWith("recommended_") && !sId.startsWith("temp_")
}
