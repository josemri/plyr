package com.plyr

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
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
import com.plyr.database.TrackEntity
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.isSystemInDarkTheme
import com.plyr.utils.NfcTagEvent
import com.plyr.utils.NfcReader



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

            // Determinar el modo efectivo: 'dark', 'light' o seguir el sistema
            val effectiveDark = when (theme.value) {
                "dark" -> true
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> isSystemInDarkTheme()
            }

            PlyrTheme(darkTheme = effectiveDark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReachabilityScaffold {
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
                                    lifecycleScope.launch {
                                        playerViewModel.loadAudioFromTrack(
                                            playlist[index]
                                        )
                                    }
                                },
                                onThemeChanged = { theme.value = it },
                                playerViewModel = playerViewModel
                            )
                        }

                        FloatingMusicControls(
                            playerViewModel = playerViewModel,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = 48.dp)
                        )
                    }
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

    override fun onResume() {
        super.onResume()
        // Activar automáticamente la lectura de NFC cuando la app está en primer plano
        NfcReader.startReading(this)
    }

    override fun onPause() {
        super.onPause()
        // Desactivar la lectura de NFC cuando la app no está en primer plano
        NfcReader.stopReading(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Manejar NFC tag (existente)
        handleNfcIntent(intent)

        // Manejar NFC tag para lectura de URLs
        handleNfcUrlRead(intent)

        // Manejar Spotify callback
        handleSpotifyCallback(intent)
    }

    private fun handleNfcUrlRead(intent: Intent?) {
        if (intent == null) return

        // Si estamos en modo escritura, no procesar la lectura para navegación
        // (pero el intent ya fue capturado, así que no se abrirá en el navegador)
        if (NfcTagEvent.isInWriteMode()) {
            android.util.Log.d("MainActivity", "🏷️ NFC Read skipped - Write mode active (tag captured for writing)")
            return
        }

        val url = NfcReader.processNfcIntent(intent)
        if (url != null) {
            val urlType = NfcReader.getUrlType(url)
            android.util.Log.d("MainActivity", "═══════════════════════════════════════")
            android.util.Log.d("MainActivity", "🏷️ NFC URL READ SUCCESS!")
            android.util.Log.d("MainActivity", "📍 URL: $url")
            android.util.Log.d("MainActivity", "🎵 Type: $urlType")
            android.util.Log.d("MainActivity", "═══════════════════════════════════════")

            // Detener el modo de lectura después de leer exitosamente
            NfcReader.stopReading(this)

            // Obtener el resultado parseado y enviarlo al evento global
            val scanResult = NfcReader.consumeScanResult()
            if (scanResult != null) {
                android.util.Log.d("MainActivity", "📤 Sending NFC result to SearchScreen - source: ${scanResult.source}, type: ${scanResult.type}, id: ${scanResult.id}")
                com.plyr.utils.NfcScanEvent.onNfcScanned(scanResult)
            }
        }
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED ||
            intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED) {

            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                android.util.Log.d("MainActivity", "🏷️ NFC Tag detected in onNewIntent: $tag")
                NfcTagEvent.onTagDetected(tag)
            } else {
                android.util.Log.w("MainActivity", "⚠️ NFC intent received but tag is null")
            }
        }
    }

    private fun handleSpotifyCallback(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "plyr" && uri.host == "spotify") {
                uri.getQueryParameter("code")?.let { code ->
                    SpotifyRepository.exchangeCodeForTokens(this, code) { tokens, error ->
                        if (tokens != null && error == null) {
                            Config.setSpotifyTokens(this, tokens.accessToken, tokens.refreshToken, tokens.expiresIn)

                            // Obtener el perfil del usuario y guardar el nombre de usuario
                            SpotifyRepository.getUserProfile(tokens.accessToken) { userProfile, profileError ->
                                if (userProfile != null && !userProfile.displayName.isNullOrBlank()) {
                                    Config.setSpotifyUserName(this, userProfile.displayName)
                                    android.util.Log.d("MainActivity", "✓ Nombre de usuario guardado: ${userProfile.displayName}")
                                } else {
                                    android.util.Log.d("MainActivity", "⚠ displayName es null o vacío: ${userProfile?.displayName}")
                                }
                            }

                            SpotifyAuthEvent.onAuthComplete(true, "connected_successfully")
                        } else {
                            SpotifyAuthEvent.onAuthComplete(false, "token_exchange_failed")
                        }
                    }
                } ?: SpotifyAuthEvent.onAuthComplete(false, "cancelled_by_user")
            }
        }
    }
    @Composable
    fun ReachabilityScaffold(content: @Composable () -> Unit) {
        var lowered by remember { mutableStateOf(false) }
        val density = LocalDensity.current
        val targetOffsetDp = with(density) { (if (lowered) 400f else 0f).toDp() }
        val animatedOffsetDp by animateDpAsState(targetValue = targetOffsetDp)

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset(y = animatedOffsetDp)
            ) {
                content()
            }

            // Zona sensible en la parte baja
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount > 50) lowered = true
                            if (dragAmount < -50) lowered = false
                        }
                    }
            )
        }
    }


}
