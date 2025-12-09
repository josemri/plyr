package com.plyr.ui

import android.content.Context
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.plyr.ui.components.*
import com.plyr.utils.Translations
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.assistant.AssistantVoiceHelper
import com.plyr.assistant.AssistantManager
import com.plyr.assistant.AssistantTTSHelper
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    context: Context,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToScreen: (Screen) -> Unit
) {
    var showExitMessage by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // pull-down related states
    val density = LocalDensity.current
    val bottomExclusionPx = with(density) { 120.dp.toPx() }
    val maxPullPx = with(density) { 200.dp.toPx() }
    val activationPx = with(density) { 60.dp.toPx() }

    var pullOffset by remember { mutableStateOf(0f) }
    var overlayVisible by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var interimText by remember { mutableStateOf("") }
    var recognizedCommand by remember { mutableStateOf("") }

    // Assistant response with typewriter effect
    var assistantResponse by remember { mutableStateOf("") }
    var displayedResponse by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }

    val pullProgress = (pullOffset / maxPullPx).coerceIn(0f, 1f)
    val scope = rememberCoroutineScope()
    val assistantVoiceHelper = remember { AssistantVoiceHelper(context) }
    val assistantManager = remember { AssistantManager(context) }
    val assistantTTS = remember { AssistantTTSHelper(context) }

    // Typewriter effect
    LaunchedEffect(assistantResponse) {
        if (assistantResponse.isNotEmpty()) {
            isTyping = true
            displayedResponse = ""
            val responseToType = assistantResponse // Store local copy
            for (i in responseToType.indices) {
                // Check if response was cleared during typing
                if (assistantResponse.isEmpty()) {
                    displayedResponse = ""
                    break
                }
                displayedResponse = responseToType.substring(0, i + 1)
                delay(20) // velocidad de escritura
            }
            isTyping = false
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isListening = true
            assistantVoiceHelper.startListening()
        }
    }

    // Function to dismiss response
    fun dismissResponse() {
        assistantResponse = ""
        displayedResponse = ""
        assistantTTS.stop()
    }

    // Voice listener setup
    DisposableEffect(Unit) {
        val listener = object : AssistantVoiceHelper.VoiceListener {
            override fun onPartial(text: String) {
                interimText = text
            }
            override fun onResult(text: String) {
                isListening = false
                isProcessing = true
                interimText = ""

                // analyze + perform and show response
                scope.launch {
                    val result = withContext(Dispatchers.Default) { assistantManager.analyze(text) }

                    // Mostrar comando entendido
                    recognizedCommand = when(result.intent) {
                        "play" -> Translations.get(context, "assistant_cmd_play")
                        "pause" -> Translations.get(context, "assistant_cmd_pause")
                        "next" -> Translations.get(context, "assistant_cmd_next")
                        "previous" -> Translations.get(context, "assistant_cmd_previous")
                        "play_search" -> "${Translations.get(context, "assistant_cmd_play_song")}: ${result.entities["query"] ?: ""}"
                        "volume_up" -> Translations.get(context, "assistant_cmd_volume") + " ↑"
                        "volume_down" -> Translations.get(context, "assistant_cmd_volume") + " ↓"
                        "shuffle" -> Translations.get(context, "assistant_cmd_shuffle")
                        "sleep_timer" -> Translations.get(context, "assistant_cmd_sleep_timer")
                        "who_sings" -> Translations.get(context, "assistant_cmd_who_sings")
                        else -> result.intent
                    }

                    val vm = playerViewModel ?: return@launch
                    val reply = withContext(Dispatchers.Default) { assistantManager.perform(result, vm) }

                    isProcessing = false
                    recognizedCommand = ""

                    // Show response with typewriter effect
                    assistantResponse = reply

                    // Speak the response
                    assistantTTS.speak(reply)
                }
            }
            override fun onError(errorCode: Int) {
                isListening = false
                isProcessing = false
                interimText = ""
                recognizedCommand = ""
            }
            override fun onReady() {}
        }
        assistantVoiceHelper.setListener(listener)
        onDispose {
            assistantVoiceHelper.cancel()
            assistantVoiceHelper.destroy()
            assistantTTS.destroy()
            assistantManager.close()
        }
    }

    PlyrScreenContainer {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset: Offset ->
                            pullOffset = 0f
                            val screenHeight = size.height.toFloat()
                            if (offset.y > (screenHeight - bottomExclusionPx)) {
                                // ignore - in bottom exclusion zone
                            } else {
                                overlayVisible = true
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // If response is visible and user scrolls up, dismiss it
                            if (assistantResponse.isNotEmpty() && dragAmount < 0) {
                                dismissResponse()
                                return@detectVerticalDragGestures
                            }

                            if (!overlayVisible) return@detectVerticalDragGestures
                            val resistance = 0.3f - (pullOffset / maxPullPx) * 0.2f
                            val dampedDrag = dragAmount * resistance
                            pullOffset = (pullOffset + dampedDrag).coerceIn(0f, maxPullPx * 0.5f)
                        },
                        onDragEnd = {
                            if (!overlayVisible) return@detectVerticalDragGestures
                            val pulledEnough = pullOffset >= activationPx
                            if (pulledEnough) {
                                assistantTTS.stop()
                                dismissResponse()
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    isListening = true
                                    assistantVoiceHelper.startListening()
                                }
                            }
                            pullOffset = 0f
                            overlayVisible = false
                        },
                        onDragCancel = {
                            pullOffset = 0f
                            overlayVisible = false
                        }
                    )
                }
        ) {
            // Top-right settings icon
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToScreen(Screen.CONFIG)
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = Translations.get(context, "settings"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // ASCII arts list
            val asciiResIds = remember {
                val ids = mutableListOf<Int>()
                for (i in 1..50) {
                    val name = "ascii_" + i
                    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
                    if (resId != 0) ids.add(resId)
                }
                ids
            }
            val selectedRes = remember(asciiResIds) {
                if (asciiResIds.isNotEmpty()) asciiResIds.random() else 0
            }

            // Main content column centered in the screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ASCII image
                if (selectedRes != 0) {
                    val painter = painterResource(id = selectedRes)
                    val intrinsic = painter.intrinsicSize
                    var imgModifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                    if (intrinsic != Size.Unspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                        imgModifier = imgModifier.aspectRatio(intrinsic.width / intrinsic.height)
                    }
                    Image(
                        painter = painter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = imgModifier
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // ActionButtonsGroup
                val buttons = mutableListOf(
                     ActionButtonData(
                         text = "< ${Translations.get(context, "home_search")} >",
                         color = MaterialTheme.colorScheme.primary,
                         onClick = {
                             haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             onNavigateToScreen(Screen.SEARCH)
                         }
                     ),
                     ActionButtonData(
                         text = "< ${Translations.get(context, "home_playlists")} >",
                         color = MaterialTheme.colorScheme.primary,
                         onClick = {
                             haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             onNavigateToScreen(Screen.PLAYLISTS)
                         }
                     ),
                     ActionButtonData(
                         text = "< ${Translations.get(context, "home_queue")} >",
                         color = MaterialTheme.colorScheme.primary,
                         onClick = {
                             haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             onNavigateToScreen(Screen.QUEUE)
                         }
                     ),
                     ActionButtonData(
                         text = "< ${Translations.get(context, "home_local")} >",
                         color = MaterialTheme.colorScheme.primary,
                         onClick = {
                             haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             onNavigateToScreen(Screen.LOCAL)
                         }
                     )
                 )

                ActionButtonsGroup(
                    buttons = buttons,
                    isHorizontal = false,
                    spacing = 12.dp,
                    modifier = Modifier.wrapContentWidth()
                )

                if (showExitMessage) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PlyrErrorText(
                        text = Translations.get(context, "exit_message"),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            // Assistant response overlay (positioned at bottom, doesn't affect button layout)
            if (displayedResponse.isNotEmpty() || isProcessing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp)
                        .padding(horizontal = 24.dp)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dismissResponse()
                        }
                ) {
                    if (isProcessing) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Translations.get(context, "assistant_processing"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (recognizedCommand.isNotBlank()) {
                                Text(
                                    text = " → $recognizedCommand",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Text(
                            text = displayedResponse + if (isTyping) "▌" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Overlay mic animation coming from top
            if (overlayVisible || isListening) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = if (isListening) 12.dp else (-24).dp + (pullOffset / density.density).dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isListening) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    assistantVoiceHelper.cancel()
                                    isListening = false
                                    interimText = ""
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (interimText.isNotBlank()) {
                                Text(
                                    text = interimText,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            } else {
                                Text(
                                    text = Translations.get(context, "assistant_listening"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Mic",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
