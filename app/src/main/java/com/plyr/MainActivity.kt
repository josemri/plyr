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
import com.plyr.network.SpotifyTokens
import com.plyr.utils.Config
import com.plyr.utils.SpotifyAuthEvent
import android.net.Uri

/**
 * MainActivity - Actividad principal de la aplicación
 * 
 * Esta actividad maneja:
 * - Conexión con el MusicService para reproducción en segundo plano
 * - Navegación entre pantallas (lista de audio, reproductor, configuración)
 * - Callbacks de OAuth de Spotify para autenticación
 * - Configuración del tema de la aplicación
 * - Coordinación entre UI y PlayerViewModel
 * 
 * La aplicación usa un diseño modular con:
 * - PlayerViewModel para lógica de reproducción
 * - MusicService para reproducción en segundo plano
 * - Componentes UI independientes y reutilizables
 */
class MainActivity : ComponentActivity() {
    
    // === PROPIEDADES ===
    
    /** Referencia al servicio de música para reproducción en segundo plano */
    private var musicService: MusicService? = null
    
    /** Indica si el servicio está conectado */
    private var bound = false
    
    // === CONFIGURACIÓN DEL SERVICIO ===
    
    /**
     * Conexión con el MusicService para comunicación bidireccional.
     */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            bound = true
            println("MainActivity: Servicio de música conectado")
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            println("MainActivity: Servicio de música desconectado")
        }
    }    
    // === CICLO DE VIDA DE LA ACTIVIDAD ===
    
    /**
     * Inicializa la actividad, configura el servicio de música y establece el contenido UI.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Procesar callback de Spotify OAuth si existe
        handleSpotifyCallback(intent)
        
        // Configurar UI edge-to-edge
        enableEdgeToEdge()
        
        // Configurar servicio de música
        setupMusicService()
        
        // Configurar contenido UI
        setupUIContent()
    }
    
    /**
     * Configura e inicia el servicio de música.
     */
    private fun setupMusicService() {
        Intent(this, MusicService::class.java).also { intent ->
            startService(intent)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }
    
    /**
     * Configura el contenido principal de la UI.
     */
    private fun setupUIContent() {
        setContent {
            val playerViewModel: PlayerViewModel = viewModel()
            var currentScreen by remember { mutableStateOf("list") }
            var selectedVideoId by remember { mutableStateOf<String?>(null) }
            
            // Gestión del tema
            val selectedTheme = remember { mutableStateOf(Config.getTheme(this@MainActivity)) }
            val isDarkTheme = selectedTheme.value == "dark"

            TerminalTheme(isDark = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContainer(
                        currentScreen = currentScreen,
                        onScreenChange = { currentScreen = it },
                        selectedVideoId = selectedVideoId,
                        onVideoIdChange = { selectedVideoId = it },
                        playerViewModel = playerViewModel,
                        selectedTheme = selectedTheme
                    )
                }
            }
        }
    }
    
    /**
     * Contenedor principal que organiza las pantallas y controles flotantes.
     */
    @Composable
    private fun MainScreenContainer(
        currentScreen: String,
        onScreenChange: (String) -> Unit,
        selectedVideoId: String?,
        onVideoIdChange: (String?) -> Unit,
        playerViewModel: PlayerViewModel,
        selectedTheme: MutableState<String>
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Área de contenido principal con padding para controles flotantes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp)
            ) {
                ScreenContent(
                    currentScreen = currentScreen,
                    onScreenChange = onScreenChange,
                    onVideoIdChange = onVideoIdChange,
                    playerViewModel = playerViewModel,
                    selectedTheme = selectedTheme
                )
            }

            // Controles flotantes de música
            FloatingMusicControls(
                playerViewModel = playerViewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )
        }
    }
    
    /**
     * Maneja el contenido de las diferentes pantallas.
     */
    @Composable
    private fun ScreenContent(
        currentScreen: String,
        onScreenChange: (String) -> Unit,
        onVideoIdChange: (String?) -> Unit,
        playerViewModel: PlayerViewModel,
        selectedTheme: MutableState<String>
    ) {
        when (currentScreen) {
            "player" -> {
                playerViewModel.exoPlayer?.let { player ->
                    ExoPlyrScreen(
                        player = player,
                        onBack = { onScreenChange("list") }
                    )
                }
            }
            else -> {
                AudioListScreen(
                    context = this@MainActivity,
                    onVideoSelected = { videoId, title ->
                        handleVideoSelection(videoId, title, onVideoIdChange, playerViewModel)
                    },
                    onThemeChanged = { newTheme ->
                        selectedTheme.value = newTheme
                    },
                    playerViewModel = playerViewModel
                )
            }
        }
    }
    
    /**
     * Maneja la selección de un video para reproducir.
     */
    private fun handleVideoSelection(
        videoId: String,
        title: String,
        onVideoIdChange: (String?) -> Unit,
        playerViewModel: PlayerViewModel
    ) {
        onVideoIdChange(videoId)
        playerViewModel.initializePlayer()
        playerViewModel.loadAudio(videoId, title)
        musicService?.playAudio(videoId)
    }    
    /**
     * Limpia recursos al destruir la actividad.
     */
    override fun onDestroy() {
        super.onDestroy()
        disconnectMusicService()
    }
    
    /**
     * Desconecta el servicio de música si está conectado.
     */
    private fun disconnectMusicService() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
    
    // === MANEJO DE INTENTS ===
    
    /**
     * Maneja nuevos intents, especialmente callbacks de Spotify OAuth.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyCallback(intent)
    }
    
    // === AUTENTICACIÓN DE SPOTIFY ===
    
    /**
     * Procesa callbacks de OAuth de Spotify cuando el usuario autoriza la aplicación.
     * 
     * @param intent Intent que puede contener datos de callback de Spotify
     */
    private fun handleSpotifyCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (isSpotifyCallback(uri)) {
                processSpotifyAuthResult(uri)
            }
        }
    }
    
    /**
     * Verifica si el URI es un callback de Spotify OAuth.
     * @param uri URI a verificar
     * @return true si es un callback de Spotify
     */
    private fun isSpotifyCallback(uri: Uri): Boolean {
        return uri.scheme == "plyr" && uri.host == "spotify"
    }
    
    /**
     * Procesa el resultado de la autorización de Spotify.
     * @param uri URI con los parámetros de respuesta de OAuth
     */
    private fun processSpotifyAuthResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        
        when {
            error != null -> handleSpotifyAuthError(error)
            code != null -> handleSpotifyAuthSuccess(code)
        }
    }
    
    /**
     * Maneja errores en la autorización de Spotify.
     * @param error Código de error recibido
     */
    private fun handleSpotifyAuthError(error: String) {
        println("MainActivity: Spotify OAuth error: $error")
        SpotifyAuthEvent.onAuthComplete(false, "cancelled_by_user")
    }
    
    /**
     * Maneja la autorización exitosa de Spotify.
     * @param code Código de autorización recibido
     */
    private fun handleSpotifyAuthSuccess(code: String) {
        SpotifyRepository.exchangeCodeForTokens(this, code) { tokens, tokenError ->
            if (tokens != null && tokenError == null) {
                saveSpotifyTokens(tokens)
                println("MainActivity: Spotify OAuth success: Tokens guardados")
                SpotifyAuthEvent.onAuthComplete(true, "connected_successfully")
            } else {
                println("MainActivity: Error intercambiando tokens: $tokenError")
                SpotifyAuthEvent.onAuthComplete(false, "token_exchange_failed")
            }
        }
    }
    
    /**
     * Guarda los tokens de Spotify en las preferencias.
     * @param tokens Tokens de acceso y refresh recibidos
     */
    private fun saveSpotifyTokens(tokens: SpotifyTokens) {
        Config.setSpotifyTokens(
            this,
            tokens.accessToken,
            tokens.refreshToken,
            tokens.expiresIn
        )
    }
}
