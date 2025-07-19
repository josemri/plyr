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

            TerminalTheme {
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
                            .padding(bottom = 140.dp) // Menos espacio para controles más minimalistas
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
                                AudioListScreen(this@MainActivity) { videoId, title ->
                                    selectedVideoId = videoId
                                    playerViewModel.initializePlayer()
                                    playerViewModel.loadAudio(videoId, title)
                                    musicService?.playAudio(videoId)
                                    // Stay on list screen instead of switching to player
                                    // currentScreen = "player"
                                }
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

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
