package com.plyr

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
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
import com.plyr.service.MusicService
import com.plyr.ui.AudioListScreen
import com.plyr.ui.FloatingMusicControls
import com.plyr.ui.theme.PlyrTheme
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyTokens
import com.plyr.utils.Config
import com.plyr.utils.SpotifyAuthEvent
import com.plyr.model.AudioItem
import com.plyr.database.TrackEntity
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * MainActivity - Entry point for Plyr app
 *
 * Handles:
 * - MusicService connection for background playback
 * - UI setup and navigation
 * - Spotify OAuth authentication
 * - Theme management
 * - Coordination with PlayerViewModel
 */
class MainActivity : ComponentActivity() {
    private var musicService: MusicService? = null
    private var bound = false
    private var isAppClosing = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            // Set the callback for MediaSession setup
            val playerViewModel = (application as PlyrApp).playerViewModel
            musicService?.let { svc ->
                svc.playerViewModel = playerViewModel // <-- Añade esta línea
                playerViewModel.onStartMediaSession = { exoPlayer ->
                    svc.startMediaSession(exoPlayer)
                }
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            // Remove callback when disconnected
            val playerViewModel = (application as PlyrApp).playerViewModel
            playerViewModel.onStartMediaSession = null
            musicService?.playerViewModel = null // <-- Añade esta línea
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permiso de notificaciones en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 123)
            }
        }

        handleSpotifyCallback(intent)
        enableEdgeToEdge()
        setupMusicService()
        setupUIContent()
    }

    private fun setupMusicService() {
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, BIND_AUTO_CREATE)
        }
    }

    private fun setupUIContent() {
        setContent {
            val playerViewModel = (application as PlyrApp).playerViewModel
            val selectedTheme = remember { mutableStateOf(Config.getTheme(this@MainActivity)) }
            val isDarkTheme = selectedTheme.value == "dark"
            PlyrTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContainer(
                        playerViewModel = playerViewModel,
                        selectedTheme = selectedTheme
                    )
                }
            }
        }
    }

    @Composable
    private fun MainScreenContainer(
        playerViewModel: PlayerViewModel,
        selectedTheme: MutableState<String>
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp)
            ) {
                ScreenContent(
                    playerViewModel = playerViewModel,
                    selectedTheme = selectedTheme
                )
            }
            FloatingMusicControls(
                playerViewModel = playerViewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }

    @Composable
    private fun ScreenContent(
        playerViewModel: PlayerViewModel,
        selectedTheme: MutableState<String>
    ) {
        AudioListScreen(
            context = this@MainActivity,
            onVideoSelectedFromSearch = { videoId, title, searchResults, selectedIndex ->
                handleVideoSelectionFromSearch(videoId, title, searchResults, selectedIndex, playerViewModel)
            },
            onThemeChanged = { newTheme ->
                selectedTheme.value = newTheme
            },
            playerViewModel = playerViewModel
        )
    }


    private fun handleVideoSelectionFromSearch(
        videoId: String,
        title: String,
        searchResults: List<AudioItem>,
        selectedIndex: Int,
        playerViewModel: PlayerViewModel
    ) {
        playerViewModel.initializePlayer()
        val searchPlaylist = searchResults.mapIndexed { index, audioItem ->
            TrackEntity(
                id = "search_${audioItem.videoId}_$index",
                playlistId = "search_results_${System.currentTimeMillis()}",
                spotifyTrackId = "",
                name = audioItem.title,
                artists = audioItem.channel,
                youtubeVideoId = audioItem.videoId,
                audioUrl = null,
                position = index,
                lastSyncTime = System.currentTimeMillis()
            )
        }
        playerViewModel.setCurrentPlaylist(searchPlaylist, selectedIndex)
        playerViewModel.loadAudio(videoId, title)
        searchPlaylist.mapNotNull { it.youtubeVideoId }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isAppClosing || isFinishing) {
            stopMusicServiceCompletely()
        } else {
            disconnectMusicService()
        }
    }

    private fun stopMusicServiceCompletely() {
        musicService?.let {
            val playerViewModel = (application as PlyrApp).playerViewModel
            playerViewModel.pausePlayer()
            stopService(Intent(this, MusicService::class.java))
        }
        disconnectMusicService()
    }

    private fun disconnectMusicService() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallback(intent)
    }

    private fun handleSpotifyCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (isSpotifyCallback(uri)) {
                processSpotifyAuthResult(uri)
            }
        }
    }

    private fun isSpotifyCallback(uri: Uri): Boolean {
        return uri.scheme == "plyr" && uri.host == "spotify"
    }

    private fun processSpotifyAuthResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        when {
            error != null -> handleSpotifyAuthError(error)
            code != null -> handleSpotifyAuthSuccess(code)
        }
    }

    private fun handleSpotifyAuthError(@Suppress("UNUSED_PARAMETER") error: String) {
        SpotifyAuthEvent.onAuthComplete(false, "cancelled_by_user")
    }

    private fun handleSpotifyAuthSuccess(code: String) {
        SpotifyRepository.exchangeCodeForTokens(this, code) { tokens, tokenError ->
            if (tokens != null && tokenError == null) {
                saveSpotifyTokens(tokens)
                SpotifyAuthEvent.onAuthComplete(true, "connected_successfully")
            } else {
                SpotifyAuthEvent.onAuthComplete(false, "token_exchange_failed")
            }
        }
    }

    private fun saveSpotifyTokens(tokens: SpotifyTokens) {
        Config.setSpotifyTokens(
            this,
            tokens.accessToken,
            tokens.refreshToken,
            tokens.expiresIn
        )
    }
}
