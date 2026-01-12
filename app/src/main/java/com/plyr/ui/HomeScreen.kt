package com.plyr.ui

import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.plyr.ui.components.*
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
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
import com.plyr.utils.Config
import kotlinx.coroutines.withContext
import com.plyr.utils.AssistantActivationEvent

@SuppressLint("DiscouragedApi")
@Composable
fun HomeScreen(
    context: Context,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToScreen: (Screen) -> Unit
) {
    // Dimensiones responsivas basadas en el tamaño de pantalla
    val dimensions = calculateResponsiveDimensionsFallback()

    var showExitMessage by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // pull-down related states
    val density = LocalDensity.current
    val bottomExclusionPx = with(density) { 120.dp.toPx() }
    val maxPullPx = with(density) { 200.dp.toPx() }
    val activationPx = with(density) { 60.dp.toPx() }

    var pullOffset by remember { mutableFloatStateOf(0f) }
    var overlayVisible by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var interimText by remember { mutableStateOf("") }

    // Assistant response with typewriter effect
    var assistantResponse by remember { mutableStateOf("") }
    var displayedResponse by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }

    // Animación CAVA para escucha y procesamiento
    val animationFrames = listOf(
        "▃▇▁▆▂█▄",
        "▆▂▅▁▇▃█",
        "▁▄█▃▆▅▂",
        "▇▅▂▄▁█▃",
        "▂█▆▇▄▁▅",
        "▅▁▃▂▇▄▆",
        "█▃▄▅▂▆▁",
        "▄▆▇▁▅▂█",
        "▃▂▆▄█▇▁",
        "▆▄▁▇▃▅█",
        "▁▇▅█▂▃▄",
        "▇▃█▂▆▁▅"
    )
    var animationFrame by remember { mutableIntStateOf(0) }

    // Animar durante escucha o procesamiento
    LaunchedEffect(isListening, isProcessing) {
        if (isListening || isProcessing) {
            while (isListening || isProcessing) {
                delay(100)
                animationFrame = (animationFrame + 1) % animationFrames.size
            }
        }
    }

    // Auto-dismiss de la respuesta después de 8 segundos
    LaunchedEffect(displayedResponse, isTyping) {
        if (displayedResponse.isNotEmpty() && !isTyping) {
            delay(8000)
            if (displayedResponse.isNotEmpty() && !isTyping) {
                assistantResponse = ""
                displayedResponse = ""
            }
        }
    }

    val scope = rememberCoroutineScope()
    val assistantVoiceHelper = remember { AssistantVoiceHelper(context) }
    val assistantManager = remember { AssistantManager(context) }
    // Use the singleton helper methods instead of constructing AssistantTTSHelper directly
    // (AssistantTTSHelper has a private constructor; use initializeIfNeeded / speakIfReady / stopIfNeeded / shutdownIfNeeded)

    // Typewriter effect
    LaunchedEffect(assistantResponse) {
        if (assistantResponse.isNotEmpty()) {
            isTyping = true
            displayedResponse = ""
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val responseToType = assistantResponse
            for (i in responseToType.indices) {
                if (assistantResponse.isEmpty()) {
                    displayedResponse = ""
                    break
                }
                displayedResponse = responseToType.take(i + 1)
                delay(20)
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

    // Escuchar evento de activación del asistente por shake
    val assistantActivationRequested by AssistantActivationEvent.activationRequested.collectAsState()

    LaunchedEffect(assistantActivationRequested) {
        if (assistantActivationRequested) {
            AssistantActivationEvent.consumeActivation()
            // Verificar si el asistente está habilitado
            if (!Config.isAssistantEnabled(context)) {
                return@LaunchedEffect
            }
            // Activar el asistente de voz
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                com.plyr.assistant.AssistantTTSHelper.stopIfNeeded()
                assistantResponse = ""
                displayedResponse = ""
                isListening = true
                assistantVoiceHelper.startListening()
            }
        }
    }

    // Function to dismiss response
    fun dismissResponse() {
        assistantResponse = ""
        displayedResponse = ""
        com.plyr.assistant.AssistantTTSHelper.stopIfNeeded()
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

                scope.launch {
                    val result = withContext(Dispatchers.Default) { assistantManager.analyze(text) }
                    val vm = playerViewModel ?: return@launch
                    val reply = withContext(Dispatchers.Default) { assistantManager.perform(result, vm) }

                    isProcessing = false
                    assistantResponse = reply
                    com.plyr.assistant.AssistantTTSHelper.speakIfReady(context, reply)
                }
            }
            override fun onError(errorCode: Int) {
                isListening = false
                isProcessing = false
                interimText = ""
            }
            override fun onReady() {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        assistantVoiceHelper.setListener(listener)
        onDispose {
            assistantVoiceHelper.cancel()
            assistantVoiceHelper.destroy()
            com.plyr.assistant.AssistantTTSHelper.shutdownIfNeeded()
            assistantManager.close()
        }
    }

    PlyrScreenContainer {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // ASCII arts list - definido aquí para usar en ambos layouts
            val asciiResIds = remember {
                val ids = mutableListOf<Int>()
                for (i in 1..50) {
                    val name = "ascii_$i"
                    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
                    if (resId != 0) ids.add(resId)
                }
                ids
            }
            val selectedRes = remember(asciiResIds) {
                if (asciiResIds.isNotEmpty()) asciiResIds.random() else 0
            }

            // ActionButtonsGroup - definido antes para usarlo en ambos layouts
            val buttons = listOf(
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
                ),
                ActionButtonData(
                    text = "< ${Translations.get(context, "home_feed")} >",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToScreen(Screen.FEED)
                    }
                )
            )

            // Main content - responsivo según orientación y tamaño de pantalla
            if (dimensions.showSideBySideLayout) {
                // Layout horizontal para landscape en pantallas grandes
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimensions.screenPadding)
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
                                onVerticalDrag = { _, dragAmount ->
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
                                    // Verificar si el asistente está habilitado
                                    if (pulledEnough && Config.isAssistantEnabled(context)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        com.plyr.assistant.AssistantTTSHelper.stopIfNeeded()
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
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ASCII image en un lado
                    if (selectedRes != 0) {
                        val painter = painterResource(id = selectedRes)
                        val intrinsic = painter.intrinsicSize
                        var imgModifier = Modifier
                            .widthIn(max = dimensions.imageMaxWidth)
                            .heightIn(max = dimensions.imageMaxHeight)
                            .padding(end = dimensions.sectionSpacing)
                        if (intrinsic != Size.Unspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                            imgModifier = imgModifier.aspectRatio(intrinsic.width / intrinsic.height)
                        }
                        Image(
                            painter = painter,
                            contentDescription = Translations.get(context, "app_logo"),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = imgModifier
                        )
                    }

                    // Botones en el otro lado
                    Column(
                        verticalArrangement = Arrangement.spacedBy(dimensions.itemSpacing),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ActionButtonsGroup(
                            buttons = buttons,
                            isHorizontal = false,
                            spacing = dimensions.itemSpacing,
                            modifier = Modifier.wrapContentWidth()
                        )

                        if (showExitMessage) {
                            Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
                            PlyrErrorText(
                                text = Translations.get(context, "exit_message"),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            } else {
                // Layout vertical SIN scroll para evitar conflicto con gestos del asistente
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimensions.screenPadding)
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
                                onVerticalDrag = { _, dragAmount ->
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
                                    // Verificar si el asistente está habilitado
                                    if (pulledEnough && Config.isAssistantEnabled(context)) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        com.plyr.assistant.AssistantTTSHelper.stopIfNeeded()
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
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ASCII image
                    if (selectedRes != 0) {
                        val painter = painterResource(id = selectedRes)
                        val intrinsic = painter.intrinsicSize
                        var imgModifier = Modifier
                            .widthIn(max = dimensions.imageMaxWidth)
                            .heightIn(max = dimensions.imageMaxHeight)
                            .padding(horizontal = dimensions.contentPadding)
                        if (intrinsic != Size.Unspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                            imgModifier = imgModifier.aspectRatio(intrinsic.width / intrinsic.height)
                        }
                        Image(
                            painter = painter,
                            contentDescription = Translations.get(context, "app_logo"),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = imgModifier
                        )
                        Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
                    }

                    // ActionButtonsGroup
                    ActionButtonsGroup(
                        buttons = buttons,
                        isHorizontal = false,
                        spacing = dimensions.itemSpacing,
                        modifier = Modifier.wrapContentWidth()
                    )

                    if (showExitMessage) {
                        Spacer(modifier = Modifier.height(dimensions.sectionSpacing))
                        PlyrErrorText(
                            text = Translations.get(context, "exit_message"),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Assistant response overlay with fade animation
            if (displayedResponse.isNotEmpty() || isProcessing) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = displayedResponse.isNotEmpty() || isProcessing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (dimensions.isLandscape) 60.dp else 100.dp)
                        .padding(horizontal = dimensions.screenPadding)
                ) {
                    Text(
                        text = if (isProcessing) animationFrames[animationFrame]
                               else displayedResponse + if (isTyping) "▌" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = if (dimensions.isLandscape) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dismissResponse()
                        }
                    )
                }
            }

            // Overlay mic animation coming from top
            if (overlayVisible || isListening) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = overlayVisible || isListening,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = if (isListening) 12.dp else (-24).dp + (pullOffset / density.density).dp)
                        .align(Alignment.TopCenter)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isListening) {
                            // Animación CAVA mientras escucha
                            Text(
                                text = animationFrames[animationFrame],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (interimText.isNotBlank()) {
                                Text(
                                    text = interimText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

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
                                    contentDescription = Translations.get(context, "cancel"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = Translations.get(context, "assistant"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Top-right settings icon - FUERA del contenido con gestos para recibir clics correctamente
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
        }
    }
}
