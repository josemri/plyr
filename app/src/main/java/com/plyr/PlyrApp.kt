package com.plyr

import android.app.Application
import com.plyr.viewmodel.ImportViewModel
import com.plyr.viewmodel.PlayerViewModel

class PlyrApp : Application() {
    lateinit var playerViewModel: PlayerViewModel
    lateinit var importViewModel: ImportViewModel

    override fun onCreate() {
        super.onCreate()
        playerViewModel = PlayerViewModel(this)
        importViewModel = ImportViewModel(this)
    }
}