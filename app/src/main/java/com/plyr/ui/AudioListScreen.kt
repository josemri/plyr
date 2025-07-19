package com.plyr.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.plyr.model.AudioItem
import com.plyr.network.AudioRepository
import com.plyr.utils.Config
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import androidx.compose.ui.draw.clipToBounds

// Estados para navegación
enum class Screen {
    MAIN,
    CONFIG
}

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    
    when (currentScreen) {
        Screen.MAIN -> MainScreen(
            context = context,
            onVideoSelected = onVideoSelected,
            onOpenConfig = { currentScreen = Screen.CONFIG }
        )
        Screen.CONFIG -> ConfigScreen(
            context = context,
            onBack = { currentScreen = Screen.MAIN }
        )
    }
}

@Composable
fun MainScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit,
    onOpenConfig: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Terminal-style header con detección de deslizamiento
        var dragOffsetX by remember { mutableStateOf(0f) }
        
        Text(
            text = "$ plyr_search",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier
                .padding(bottom = 16.dp)
                .offset(x = dragOffsetX.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffsetX) > 100 && dragOffsetX > 0) {
                                onOpenConfig()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffsetX = 0f
                        }
                    ) { _, dragAmount ->
                        dragOffsetX += dragAmount / density
                    }
                }
        )

        // Search field with clear button and enter action
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { 
                Text(
                    "> search_audio",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp
                    )
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        results = emptyList()
                        error = null
                    }) {
                        Text(
                            text = "x",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank() && !isLoading) {
                        isLoading = true
                        error = null
                        results = emptyList()

                        AudioRepository.searchAudios(searchQuery) { list, err ->
                            isLoading = false
                            if (err != null) {
                                error = err
                            } else if (list != null) {
                                results = list
                            }
                        }
                    }
                }
            ),
            enabled = !isLoading,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4ECDC4),
                unfocusedBorderColor = Color(0xFF95A5A6),
                focusedLabelColor = Color(0xFF4ECDC4),
                unfocusedLabelColor = Color(0xFF95A5A6),
                focusedTextColor = Color(0xFFE0E0E0),
                unfocusedTextColor = Color(0xFFE0E0E0)
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
        )

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "● ",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFFFD93D)
                    )
                )
                Text(
                    "$ loading...",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF95A5A6)
                    )
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                "ERR: $it",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(results) { item ->
                val id = item.url.toUri().getQueryParameter("v")
                if (id != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                            .clickable { 
                                // Limpiar resultados y búsqueda al seleccionar
                                searchQuery = ""
                                results = emptyList()
                                error = null
                                onVideoSelected(id, item.title)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "> ",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                                color = Color(0xFF4ECDC4)
                            )
                        )
                        MarqueeText(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFFE0E0E0)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }
            }
        }
    }
}

// Composable para texto marquee mejorado
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var textWidth by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }
    val shouldAnimate = textWidth > containerWidth && containerWidth > 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) -(textWidth - containerWidth).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (shouldAnimate) maxOf(text.length * 100, 3000) else 0,
                easing = LinearEasing,
                delayMillis = 1500 // Pausa al inicio
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_animation"
    )
    
    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                containerWidth = size.width
            }
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { size ->
                    textWidth = size.width
                }
                .offset(x = with(density) { animatedOffset.toDp() })
        )
    }
}

@Composable
fun ConfigScreen(
    context: Context,
    onBack: () -> Unit
) {
    var apiUrl by remember { mutableStateOf(Config.getApiUrl(context)) }
    var apiToken by remember { mutableStateOf(Config.getApiToken(context)) }
    
    // Guardar automáticamente cuando cambien los valores
    LaunchedEffect(apiUrl) {
        if (apiUrl.isNotBlank()) {
            Config.setApiUrl(context, apiUrl)
        }
    }
    
    LaunchedEffect(apiToken) {
        if (apiToken.isNotBlank()) {
            Config.setApiToken(context, apiToken)
        }
    }
    
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header de configuración
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
            Text(
                text = "$ plyr_config",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                )
            )
            */

            var dragOffsetX by remember { mutableStateOf(0f) }

            Text(
                text = "$ plyr_config",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .offset(x = dragOffsetX.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(dragOffsetX) > 10 && dragOffsetX < 0) {
                                    onBack()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                dragOffsetX = 0f
                            }
                        ) { _, dragAmount ->
                            dragOffsetX += dragAmount / density
                        }
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Campo API URL
        Text(
            text = "> api_url",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = apiUrl,
            onValueChange = { apiUrl = it },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4ECDC4),
                unfocusedBorderColor = Color(0xFF95A5A6),
                focusedTextColor = Color(0xFFE0E0E0),
                unfocusedTextColor = Color(0xFFE0E0E0)
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            placeholder = {
                Text(
                    "https://abc123.com",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Campo API Token
        Text(
            text = "> api_token",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = Color(0xFF95A5A6)
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = apiToken,
            onValueChange = { apiToken = it },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4ECDC4),
                unfocusedBorderColor = Color(0xFF95A5A6),
                focusedTextColor = Color(0xFFE0E0E0),
                unfocusedTextColor = Color(0xFFE0E0E0)
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            placeholder = {
                Text(
                    "token_abc123xyz",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(30.dp))
        
        // Información de uso
        Column {
            Text(
                text = "$ info",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color(0xFF4ECDC4)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "• considera pagarme algo por esto, porfa\n• API URL: endpoint de tu servidor\n• API Token: para autenticación futura",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF95A5A6)
                ),
                lineHeight = 16.sp
            )
        }
        
        //Spacer(modifier = Modifier.weight(1f))
        
        // Status de configuración
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$ status:",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF95A5A6)
                )
            )
            
            Text(
                text = if (apiUrl.contains("ngrok.io")) "configured" else "pending",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (apiUrl.contains("ngrok.io")) Color(0xFF4ECDC4) else Color(0xFFFFD93D)
                )
            )
        }
    }
}