package com.plyr.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.plyr.desktop.player.VlcAudioPlayer
import com.plyr.desktop.player.VlcEnvironment
import com.plyr.network.YouTubeManager
import com.plyr.player.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ventana mínima de escritorio para la prueba de sonido de M4.
 *
 * No es la UI definitiva (eso llega en M5/M7): solo permite buscar una canción
 * de YouTube y reproducirla con VLCJ, para comprobar con `./gradlew :desktop:run`
 * que el motor de audio funciona. Si falta `libvlc`, lo dice con claridad.
 */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "plyr — prueba de sonido (M4)") {
        MaterialTheme { SoundCheckScreen() }
    }
}

@Composable
private fun SoundCheckScreen() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("Daft Punk Get Lucky") }
    var status by remember { mutableStateOf("Listo. Busca una canción y pulsa Reproducir.") }
    var player by remember { mutableStateOf<VlcAudioPlayer?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Prueba de sonido · M4", style = MaterialTheme.typography.titleLarge)

        val libraryDir = remember { VlcEnvironment.findLibraryDir() }
        if (libraryDir == null) {
            Text(
                text = VlcEnvironment.missingMessage(),
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }
        Text("libvlc: ${libraryDir.absolutePath}", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar en YouTube") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = query.isNotBlank(),
                onClick = {
                    status = "Buscando…"
                    scope.launch {
                        player?.release()
                        player = null
                        val result = resolveAndPlay(query, libraryDir) { message -> status = message }
                        player = result
                    }
                },
            ) { Text("Reproducir") }

            Button(
                onClick = {
                    player?.stop()
                    status = "Detenido."
                },
            ) { Text("Parar") }
        }

        Spacer(Modifier.height(4.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Resuelve la URL de [query] y arranca la reproducción; devuelve el reproductor. */
private suspend fun resolveAndPlay(
    query: String,
    libraryDir: java.io.File,
    onStatus: (String) -> Unit,
): VlcAudioPlayer? = withContext(Dispatchers.IO) {
    val videoId = YouTubeManager.searchVideoId(query)
    if (videoId == null) {
        onStatus("No se encontró ningún resultado.")
        return@withContext null
    }
    onStatus("Vídeo: $videoId. Extrayendo audio…")
    val url = YouTubeManager.getAudioUrl(videoId)
    if (url == null) {
        onStatus("No se pudo extraer la URL de audio.")
        return@withContext null
    }

    val created = try {
        VlcAudioPlayer(libraryDir = libraryDir)
    } catch (e: VlcEnvironment.LibVlcMissingException) {
        onStatus(e.message ?: "libvlc no disponible.")
        return@withContext null
    }
    created.setMediaItems(
        listOf(AudioTrack(mediaId = videoId, title = query, artist = "", uri = url)),
        startIndex = 0,
    )
    created.prepare()
    created.play()
    onStatus("Reproduciendo: $query")
    created
}
