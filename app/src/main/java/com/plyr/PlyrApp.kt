package com.plyr

import android.app.Application
import com.plyr.database.PlaylistLocalRepository
import com.plyr.viewmodel.ImportViewModel
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlyrApp : Application() {
    lateinit var playerViewModel: PlayerViewModel
    lateinit var importViewModel: ImportViewModel

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Scope de aplicación para operaciones que deben sobrevivir a la navegación
    // (B23): sincronizar no debe cortarse a mitad por salir de la pantalla de ajustes.
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        playerViewModel = PlayerViewModel(this)
        importViewModel = ImportViewModel(this)
        appScope.launch {
            PlaylistLocalRepository(this@PlyrApp).ensureLikedSongsPlaylist()
        }
    }
}