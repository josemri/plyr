package com.plyr.ui.components

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.plyr.database.TrackEntity

import com.plyr.utils.Config
import com.plyr.utils.Translations
import com.plyr.viewmodel.PlayerViewModel

/**
 * Data class para pasar información de la canción al menú
 */
data class SongMenuData(
    val title: String,
    val artist: String,
    val remoteId: String? = null,
    val youtubeId: String? = null,
    val trackEntity: TrackEntity? = null
)

/**
 * Helper function para verificar si es un ID remoto válido
 */
private fun isValidRemoteId(id: String?): Boolean {
    if (id == null || id.isBlank()) return false
    if (id.startsWith("recommended_") || id.startsWith("temp_")) return false
    return true
}

/**
 * Diálogo de menú de canción reutilizable
 */
@Composable
fun SongMenuDialog(
    context: Context,
    songData: SongMenuData,
    playerViewModel: PlayerViewModel?,
    onDismiss: () -> Unit,
    onLikedStatusChanged: (() -> Unit)? = null
) {

    Dialog(onDismissRequest = {
        onDismiss()
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = songData.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = songData.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    )
                }

                // Botones de acción
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Add to Queue
                    Text(
                        text = Translations.get(context, "add_to_queue"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                songData.trackEntity?.let { track ->
                                    playerViewModel?.addToQueue(track)
                                    Log.d("SongMenuDialog", "✓ Track added to queue: ${track.name}")
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
                                onDismiss()
                                val shareText = "${songData.title} - ${songData.artist}"
                                val shareUrl = songData.youtubeId?.let { "https://www.youtube.com/watch?v=$it" }
                                val fullShareText = if (shareUrl != null) 
                                    "$shareText\n$shareUrl" else shareText
                                
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, fullShareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
