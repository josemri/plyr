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

    override fun onCreate() {
        super.onCreate()
        playerViewModel = PlayerViewModel(this)
        importViewModel = ImportViewModel(this)
        appScope.launch {
            PlaylistLocalRepository(this@PlyrApp).ensureLikedSongsPlaylist()
        }
    }
}