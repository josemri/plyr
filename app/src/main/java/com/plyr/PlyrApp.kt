package com.plyr

import android.app.Application
import com.plyr.core.NetworkConfig
import com.plyr.database.PlaylistLocalRepository
import com.plyr.viewmodel.DownloadViewModel
import com.plyr.viewmodel.ImportViewModel
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlyrApp : Application() {
    lateinit var playerViewModel: PlayerViewModel
    lateinit var importViewModel: ImportViewModel
    lateinit var downloadViewModel: DownloadViewModel

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Scope de aplicación para operaciones que deben sobrevivir a la navegación
    // (B23): sincronizar no debe cortarse a mitad por salir de la pantalla de ajustes.
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        // S9/S10: los valores de red (con overrides de build) viven en `:core`,
        // que no puede leer `BuildConfig` (Android). Se inyectan aquí al arrancar.
        NetworkConfig.supabaseUrl = BuildConfig.SUPABASE_URL
        NetworkConfig.supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
        NetworkConfig.youtubeRestrictedModeCookie = BuildConfig.YOUTUBE_RESTRICTED_MODE_COOKIE

        playerViewModel = PlayerViewModel(this)
        importViewModel = ImportViewModel(this)
        downloadViewModel = DownloadViewModel(this)
        appScope.launch {
            PlaylistLocalRepository(this@PlyrApp).liked.ensureLikedSongsPlaylist()
        }
    }
}