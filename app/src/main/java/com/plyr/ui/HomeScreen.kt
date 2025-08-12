package com.plyr.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    context: Context,
    onNavigateToScreen: (Screen) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var backPressedTime by remember { mutableStateOf(0L) }
    var showExitMessage by remember { mutableStateOf(false) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime > 2000) {
            backPressedTime = currentTime
            showExitMessage = true
            CoroutineScope(Dispatchers.Main).launch {
                delay(2000)
                showExitMessage = false
            }
        } else {
            (context as? Activity)?.finish()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$ plyr_home",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val options = remember {
            listOf(
                MenuOption(Screen.SEARCH, "> search"),
                MenuOption(Screen.PLAYLISTS, "> playlists"),
                MenuOption(Screen.QUEUE, "> queue"),
                MenuOption(Screen.CONFIG, "> settings")
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { option ->
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        color = Color.White
                    ),
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(option.screen)
                        }
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showExitMessage) {
            Text(
                text = "> Press back again to exit",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE74C3C)
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }
    }
}
