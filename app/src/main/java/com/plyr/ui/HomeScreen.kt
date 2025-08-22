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
import com.plyr.ui.components.*
import com.plyr.ui.theme.*

@Composable
fun HomeScreen(
    context: Context,
    onNavigateToScreen: (Screen) -> Unit
) {
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

    PlyrScreenContainer {
        PlyrCommandTitle("plyr_home")

        val options = remember {
            listOf(
                MenuOption(Screen.SEARCH, "search"),
                MenuOption(Screen.PLAYLISTS, "playlists"),
                MenuOption(Screen.QUEUE, "queue"),
                MenuOption(Screen.CONFIG, "settings")
            )
        }

        options.forEach { option ->
            PlyrMenuOption(
                text = option.title,
                onClick = { onNavigateToScreen(option.screen) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (showExitMessage) {
            PlyrErrorText(
                text = "Press back again to exit",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
