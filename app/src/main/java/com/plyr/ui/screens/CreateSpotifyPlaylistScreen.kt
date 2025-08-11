import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plyr.network.SpotifyRepository
import com.plyr.utils.Config

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
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = playlistDesc,
            onValueChange = { playlistDesc = it },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(Modifier.height(8.dp))

        // Privacy selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // Public option
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

            // Private option
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

        // Create button
        Text(
            text = if (isLoading) "<creating...>" else "<create>",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = if (isLoading) Color(0xFFFFD93D) else Color(0xFF4ECDC4)
            ),
            modifier = Modifier
                .clickable(enabled = !isLoading && playlistName.isNotBlank()) {
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
                            if (success) {
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
            Text(
                text = "Error: $it",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE74C3C)
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Back button
        Text(
            text = "< cancel",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier
                .clickable { onBack() }
                .padding(8.dp)
        )
    }
}
