package com.plyr.ui

import android.content.Context
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import com.plyr.assistant.AssistantStorage
import com.plyr.assistant.AssistantTTSHelper
import com.plyr.assistant.ChatMessage
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
    val bottomExclusionPx = with(density) { 120.dp.toPx() } // Excluir zona inferior para FloatingMusicControl
    val maxPullPx = with(density) { 200.dp.toPx() }
    val activationPx = with(density) { 60.dp.toPx() }
    val holdMs = 800L

    var pullOffset by remember { mutableStateOf(0f) }
    var overlayVisible by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var interimText by remember { mutableStateOf("") }

    val pullProgress = (pullOffset / maxPullPx).coerceIn(0f, 1f)
    val animatedScale by animateFloatAsState(targetValue = 0.8f + 0.4f * pullProgress)
    val scope = rememberCoroutineScope()
    val assistantVoiceHelper = remember { AssistantVoiceHelper(context) }
    val assistantManager = remember { AssistantManager(context) }
    val assistantTTS = remember { AssistantTTSHelper(context) }

    // Permission launcher: if granted, start listening immediately
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isListening = true
            assistantVoiceHelper.startListening()
        }
    }

    var holdJob by remember { mutableStateOf<Job?>(null) }

    // Voice listener setup
    DisposableEffect(Unit) {
        val listener = object : AssistantVoiceHelper.VoiceListener {
            override fun onPartial(text: String) {
                interimText = text
            }
            override fun onResult(text: String) {
                isListening = false
                interimText = ""
                // persist and process result
                val userMsg = ChatMessage("user", text)
                val messages = AssistantStorage.loadChat(context).toMutableList()
                messages.add(userMsg)
                AssistantStorage.saveChat(context, messages)

                // analyze + perform and save reply
                scope.launch {
                    val result = withContext(Dispatchers.Default) { assistantManager.analyze(text) }
                    val vm = playerViewModel
                    if (vm == null) {
                        return@launch
                    }
                    val reply = withContext(Dispatchers.Default) { assistantManager.perform(result, vm) }
                    val assistantMsg = ChatMessage("assistant", reply)
                    val msgs2 = AssistantStorage.loadChat(context).toMutableList()
                    msgs2.add(assistantMsg)
                    AssistantStorage.saveChat(context, msgs2)

                    // Reproducir la respuesta por audio
                    assistantTTS.speak(reply)
                }
            }
            override fun onError(errorCode: Int) {
                isListening = false
                interimText = ""
            }
            override fun onReady() {
                // no-op
            }
        }
        assistantVoiceHelper.setListener(listener)
        onDispose {
            assistantVoiceHelper.cancel()
            assistantVoiceHelper.destroy()
            assistantTTS.destroy()
        }
    }

    // Language pulldown gesture on the top of the Box

    PlyrScreenContainer {
        val verticalScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset: Offset ->
                            // start from anywhere except bottom area (FloatingMusicControl)
                            pullOffset = 0f
                            val screenHeight = size.height.toFloat()
                            if (offset.y > (screenHeight - bottomExclusionPx)) {
                                // ignore - in bottom exclusion zone
                            } else {
                                overlayVisible = true
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!overlayVisible) return@detectVerticalDragGestures
                            // Aplicar resistencia más fuerte: cuanto más se arrastra, más resistencia hay
                            val resistance = 0.3f - (pullOffset / maxPullPx) * 0.2f
                            val dampedDrag = dragAmount * resistance
                            pullOffset = (pullOffset + dampedDrag).coerceIn(0f, maxPullPx * 0.5f)
                            // start hold job if passed activation threshold
                            if (pullOffset >= activationPx && holdJob == null) {
                                holdJob = scope.launch {
                                    // wait for holdMs; if still pulled, navigate to chat
                                    delay(holdMs)
                                    if (overlayVisible && pullOffset >= activationPx) {
                                        overlayVisible = false
                                        pullOffset = 0f
                                        onNavigateToScreen(Screen.ASSISTANT)
                                    }
                                }
                            }
                            // cancel hold if released below threshold
                            if (pullOffset < activationPx) {
                                holdJob?.cancel()
                                holdJob = null
                            }
                        },
                        onDragEnd = {
                            if (!overlayVisible) return@detectVerticalDragGestures
                            val pulledEnough = pullOffset >= activationPx
                            // If holdJob is active and not completed, cancel it (we handle hold inside the job)
                            holdJob?.cancel()
                            holdJob = null
                            if (pulledEnough) {
                                // Detener TTS si está sonando
                                assistantTTS.stop()
                                // Quick release => start voice
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    isListening = true
                                    assistantVoiceHelper.startListening()
                                }
                            }
                            // reset
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
                // Imagen ASCII centrada arriba de los botones
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

                // ActionButtonsGroup centrado
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

            // Overlay mic animation coming from top
            if (overlayVisible || isListening) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = if (isListening) 12.dp else (-24).dp + (pullOffset / density.density).dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isListening) {
                            // Mostrar X para cancelar cuando está escuchando
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
                                Text(interimText, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Escuchando...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            // Mostrar micro cuando está arrastrando
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
