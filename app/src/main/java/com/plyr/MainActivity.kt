package com.plyr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plyr.service.MusicService
import com.plyr.ui.AudioListScreen
import com.plyr.ui.ExoPlyrScreen
import com.plyr.ui.FloatingMusicControls
import com.plyr.ui.theme.TerminalTheme
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.SpotifyRepository
import com.plyr.utils.Config
import com.plyr.utils.SpotifyAuthEvent
import android.net.Uri

class MainActivity : ComponentActivity() {
    
    private var musicService: MusicService? = null
    private var bound = false
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manejar callback de Spotify OAuth
        handleSpotifyCallback(intent)
        
        // Habilitar edge-to-edge para manejo del status bar
        enableEdgeToEdge()
        
        // Iniciar y conectar al servicio
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            val playerViewModel: PlayerViewModel = viewModel()
            var currentScreen by remember { mutableStateOf("list") }
            var selectedVideoId by remember { mutableStateOf<String?>(null) }
            
            // Obtener el tema seleccionado
            val selectedTheme = remember { mutableStateOf(com.plyr.utils.Config.getTheme(this@MainActivity)) }
            val isDarkTheme = selectedTheme.value == "dark"

            TerminalTheme(isDark = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Main container with floating controls at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding() // Padding para el status bar
                    ) {
                    // Main content area with bottom padding for floating controls
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 140.dp) // Reducido de 180dp para acomodar controles más compactos
                    ) {
                        when (currentScreen) {
                            "player" -> {
                                playerViewModel.exoPlayer?.let { player ->
                                    ExoPlyrScreen(
                                        player = player,
                                        onBack = { currentScreen = "list" }
                                    )
                                }
                            }
                            else -> {
                                AudioListScreen(
                                    context = this@MainActivity,
                                    onVideoSelected = { videoId, title ->
                                        selectedVideoId = videoId
                                        playerViewModel.initializePlayer()
                                        playerViewModel.loadAudio(videoId, title)
                                        musicService?.playAudio(videoId)
                                    },
                                    onThemeChanged = { newTheme ->
                                        selectedTheme.value = newTheme
                                    }
                                )
                            }
                        }
                    }

                    // Floating music controls always visible at bottom
                    FloatingMusicControls(
                        playerViewModel = playerViewModel,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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
            if (uri.scheme == "plyr" && uri.host == "spotify") {
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error")
                
                when {
                    error != null -> {
                        // Usuario canceló o error en autorización
                        println("Spotify OAuth error: $error")
                        SpotifyAuthEvent.onAuthComplete(false, "cancelled_by_user")
                    }
                    code != null -> {
                        // Éxito - intercambiar código por tokens
                        SpotifyRepository.exchangeCodeForTokens(code) { tokens, tokenError ->
                            if (tokens != null && tokenError == null) {
                                // Guardar tokens
                                Config.setSpotifyTokens(
                                    this,
                                    tokens.accessToken,
                                    tokens.refreshToken,
                                    tokens.expiresIn
                                )
                                println("Spotify OAuth success: Tokens guardados")
                                SpotifyAuthEvent.onAuthComplete(true, "connected_successfully")
                            } else {
                                println("Error intercambiando tokens: $tokenError")
                                SpotifyAuthEvent.onAuthComplete(false, "token_exchange_failed")
                            }
                        }
                    }
                }
            }
        }
    }
}
