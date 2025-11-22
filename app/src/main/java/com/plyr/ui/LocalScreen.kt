package com.plyr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.plyr.database.DownloadedTrackEntity
import com.plyr.database.PlaylistDatabase
import com.plyr.database.TrackEntity
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
import androidx.compose.ui.window.Dialog
import com.plyr.service.detectAudioFromUri

@Composable
fun LocalScreen(
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var downloadedTracks by remember { mutableStateOf<List<DownloadedTrackEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<DownloadedTrackEntity?>(null) }

    // Estados para importar archivos
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var importTrackName by remember { mutableStateOf("") }
    var importArtistName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectionStatus by remember { mutableStateOf<String?>(null) } // "detected", "error" o null

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
        onBack()
    }

    // Cargar tracks descargados
    LaunchedEffect(Unit) {
        try {
            val database = PlaylistDatabase.getDatabase(context)
            database.downloadedTrackDao().getAllDownloadedTracks().collect { tracks ->
                downloadedTracks = tracks
                isLoading = false
                Log.d("LocalScreen", "Loaded ${tracks.size} downloaded tracks")
            }
        } catch (e: Exception) {
            error = "Error loading downloaded tracks: ${e.message}"
            isLoading = false
            Log.e("LocalScreen", "Error loading tracks", e)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Título
        Titulo(Translations.get(context, "plyr_local"))

        // Botón de añadir archivo debajo del título
        ActionButtonsGroup(
            buttons = listOf(
                ActionButtonData(
                    text = "<add>",
                    color = Color(0xFF4ECDC4),
                    onClick = {
                        filePickerLauncher.launch("audio/*")
                    }
                )
            )
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF4ECDC4))
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
                            color = Color(0xFFFF6B6B)
                        )
                    )
                }
            }
            downloadedTracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
				    Text(
                        text = Translations.get(context, "No tracks downloaded"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF95A5A6)
                        )
                    )
                }
            }
            else -> {
                // Lista de tracks
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(downloadedTracks) { index, track ->
                        // Convertir DownloadedTrackEntity a TrackEntity para SongListItem
                        val song = Song(
                            number = index + 1,
                            title = track.name,
                            artist = track.artists,
                            spotifyId = track.spotifyTrackId,
                            youtubeId = track.youtubeVideoId
                        )

                        SongListItem(
                            song = song,
                            trackEntities = downloadedTracks.map { dt ->
                                val trackEntity = TrackEntity(
                                    id = dt.id,
                                    playlistId = "local",
                                    spotifyTrackId = dt.spotifyTrackId,
                                    name = dt.name,
                                    artists = dt.artists,
                                    youtubeVideoId = dt.youtubeVideoId,
                                    audioUrl = dt.localFilePath, // Esta ruta debe usarse para reproducción local
                                    position = downloadedTracks.indexOf(dt)
                                )
                                Log.d("LocalScreen", "TrackEntity creado - audioUrl: ${trackEntity.audioUrl}")
                                trackEntity
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
    }

    // Diálogo de confirmación de eliminación
    if (showDeleteDialog && trackToDelete != null) {
        PlyrConfirmDialog(
            title = Translations.get(context, "delete track"),
            message = Translations.get(context, "Song {{track_name}} will be removed permanently").replace("{{track_name}}", trackToDelete?.name ?: ""),
            confirmText = Translations.get(context, "delete"),
            cancelText = "cancel",
            onConfirm = {
                coroutineScope.launch {
                    trackToDelete?.let { track ->
                        val success = DownloadManager.deleteDownloadedTrack(context, track)
                        if (success) {
                            Log.d("LocalScreen", "Track deleted successfully")
                        } else {
                            error = "Failed to delete track"
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
        var loadingFrame by remember { mutableStateOf(0) }

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
                    .background(Color(0xFF181818))
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
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    if (isImporting) {
                        // Mostrar progreso de importación
                        CircularProgressIndicator(
                            progress = { importProgress / 100f },
                            color = Color(0xFF4ECDC4),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "$importProgress%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            )
                        )
                    } else if (importError != null) {
                        // Mostrar error
                        Text(
                            text = "✗",
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = Color(0xFFFF6B6B)
                            )
                        )
                        Text(
                            text = importError ?: "Error",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFF6B6B)
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ActionButtonsGroup(
                            buttons = listOf(
                                ActionButtonData(
                                    text = Translations.get(context, "close"),
                                    color = Color(0xFF4ECDC4),
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
                            enabled = !isDetecting, // Bloquear cuando está detectando
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color(0xFF555555),
                                focusedLabelColor = Color(0xFF4ECDC4),
                                unfocusedLabelColor = Color(0xFF888888),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color(0xFF888888),
                                disabledBorderColor = Color(0xFF333333),
                                disabledLabelColor = Color(0xFF666666)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = importArtistName,
                            onValueChange = { importArtistName = it },
                            label = { Text(Translations.get(context, "artist_name")) },
                            enabled = !isDetecting, // Bloquear cuando está detectando
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color(0xFF555555),
                                focusedLabelColor = Color(0xFF4ECDC4),
                                unfocusedLabelColor = Color(0xFF888888),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color(0xFF888888),
                                disabledBorderColor = Color(0xFF333333),
                                disabledLabelColor = Color(0xFF666666)
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
                                            isDetecting -> Color(0xFF4ECDC4)
                                            detectionStatus == "detected" -> Color(0xFF4CAF50) // Verde para éxito
                                            detectionStatus == "error" -> Color(0xFFFF6B6B) // Rojo para error
                                            else -> Color(0xFFFFB74D) // Naranja por defecto
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
                                        color = Color(0xFF4ECDC4),
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
                                modifier = Modifier
                                    .widthIn(min = 280.dp, max = 280.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
