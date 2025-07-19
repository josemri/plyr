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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.plyr.ui.theme.TerminalTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay

@Composable
fun AudioListScreen(
    context: Context,
    onVideoSelected: (String, String) -> Unit // Ahora pasa ID y título
) {
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Terminal-style header
        Text(
            text = "$ plyr_audio_search",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
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
                            text = "×",
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
            modifier = Modifier.fillMaxWidth()
        ) {
            items(results) { item ->
                val id = item.url.toUri().getQueryParameter("v")
                if (id != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { 
                                // Limpiar resultados y búsqueda al seleccionar
                                searchQuery = ""
                                results = emptyList()
                                error = null
                                onVideoSelected(id, item.title) // Pasar tanto ID como título
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2C2C2C).copy(alpha = 0.8f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
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
                            // Texto con marquee para líneas largas
                            MarqueeText(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    color = Color(0xFFE0E0E0)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "●",
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
}

// Composable para texto marquee mejorado
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    var textWidth by remember { mutableStateOf(0) }
    var containerWidth by remember { mutableStateOf(0) }
    val shouldAnimate = textWidth > containerWidth && containerWidth > 0
    
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimate) -(textWidth - containerWidth).toFloat() else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (shouldAnimate) (text.length * 80) else 0,
                easing = LinearEasing,
                delayMillis = 1000
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "marquee_animation"
    )
    
    Box(
        modifier = modifier
            .onSizeChanged { size ->
                containerWidth = size.width
            }
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier
                .onSizeChanged { size ->
                    textWidth = size.width
                }
                .offset(x = with(LocalDensity.current) { animatedOffset.toDp() })
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun AudioListScreenPreview() {
    TerminalTheme {
        AudioListScreen(
            context = LocalContext.current,
            onVideoSelected = { _, _ -> } // Actualizar la preview
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, name = "Audio List Terminal Theme")
@Composable
fun AudioListScreenTerminalPreview() {
    TerminalTheme {
        AudioListScreen(
            context = LocalContext.current,
            onVideoSelected = { _, _ -> } // Actualizar la preview
        )
    }
}