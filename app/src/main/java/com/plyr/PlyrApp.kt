package com.plyr

import android.app.Application
import com.plyr.viewmodel.PlayerViewModel

class PlyrApp : Application() {
    lateinit var playerViewModel: PlayerViewModel

    override fun onCreate() {
        super.onCreate()
        playerViewModel = PlayerViewModel(this)
    }
}