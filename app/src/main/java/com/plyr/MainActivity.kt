package com.plyr

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.plyr.service.MusicService
import com.plyr.ui.AudioListScreen
import com.plyr.ui.FloatingMusicControls
import com.plyr.ui.theme.PlyrTheme
import com.plyr.network.SpotifyRepository
import com.plyr.utils.Config
import com.plyr.utils.SpotifyAuthEvent
import com.plyr.model.AudioItem
import com.plyr.database.TrackEntity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var musicService: MusicService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            (application as PlyrApp).playerViewModel.onMediaSessionUpdate = { player ->
                musicService?.setupMediaSession(player)
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            musicService = null
            (application as PlyrApp).playerViewModel.onMediaSessionUpdate = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 123)
        }

        handleSpotifyCallback(intent)
        enableEdgeToEdge()

        Intent(this, MusicService::class.java).also {
            startService(it)
            bindService(it, serviceConnection, BIND_AUTO_CREATE)
        }

        setContent {
            val playerViewModel = (application as PlyrApp).playerViewModel
            val theme = remember { mutableStateOf(Config.getTheme(this)) }

            PlyrTheme(darkTheme = theme.value == "dark") {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 140.dp)) {
                            AudioListScreen(
                                context = this@MainActivity,
                                onVideoSelectedFromSearch = { _, _, results, index ->
                                    playerViewModel.initializePlayer()

                                    val playlist = results.mapIndexed { i, item ->
                                        TrackEntity(
                                            id = "search_${item.videoId}_$i",
                                            playlistId = "search_${System.currentTimeMillis()}",
                                            spotifyTrackId = "",
                                            name = item.title,
                                            artists = item.channel,
                                            youtubeVideoId = item.videoId,
                                            audioUrl = null,
                                            position = i,
                                            lastSyncTime = System.currentTimeMillis()
                                        )
                                    }

                                    playerViewModel.setCurrentPlaylist(playlist, index)
                                    lifecycleScope.launch { playerViewModel.loadAudioFromTrack(playlist[index]) }
                                },
                                onThemeChanged = { theme.value = it },
                                playerViewModel = playerViewModel
                            )
                        }

                        FloatingMusicControls(
                            playerViewModel = playerViewModel,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            (application as PlyrApp).playerViewModel.pausePlayer()
            stopService(Intent(this, MusicService::class.java))
        }
        unbindService(serviceConnection)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallback(intent)
    }

    private fun handleSpotifyCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "plyr" && uri.host == "spotify") {
                uri.getQueryParameter("code")?.let { code ->
                    SpotifyRepository.exchangeCodeForTokens(this, code) { tokens, error ->
                        if (tokens != null && error == null) {
                            Config.setSpotifyTokens(this, tokens.accessToken, tokens.refreshToken, tokens.expiresIn)
                            SpotifyAuthEvent.onAuthComplete(true, "connected_successfully")
                        } else {
                            SpotifyAuthEvent.onAuthComplete(false, "token_exchange_failed")
                        }
                    }
                } ?: SpotifyAuthEvent.onAuthComplete(false, "cancelled_by_user")
            }
        }
    }
}
