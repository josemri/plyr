package com.plyr

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * SplashActivity - Pantalla de inicio completamente negra
 * 
 * Muestra una pantalla completamente negra durante 2 segundos
 * antes de navegar a la actividad principal.
 */
class SplashActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen for Android 12+ 
        installSplashScreen()
        
        super.onCreate(savedInstanceState)
        
        // Set content for fallback on older Android versions
        setContent {
            BlackScreen()
        }
        
        // Navigate to MainActivity after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2000)
    }
}

/**
 * Composable de la pantalla de splash completamente negra
 */
@Composable
fun BlackScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
 }
