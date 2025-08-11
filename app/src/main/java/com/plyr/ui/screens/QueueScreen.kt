package com.plyr.ui.screens

import android.content.Context
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plyr.ui.components.MarqueeText
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(
    context: Context,
    onBack: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Handle back button
    BackHandler {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "$ plyr_queue",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                color = Color(0xFF4ECDC4)
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Queue content
        if (playerViewModel != null) {
            val queueState by playerViewModel.queueState.collectAsStateWithLifecycle()
            val currentQueue = queueState.queue

            if (currentQueue.isNotEmpty()) {
                // Queue header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current queue [${currentQueue.size}]",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = Color(0xFFFFD93D)
                        )
                    )

                    // Clear queue button
                    TextButton(
                        onClick = {
                            playerViewModel.clearQueue()
                            Log.d("QueueScreen", "Queue cleared by user")
                        }
                    ) {
                        Text(
                            text = "clear",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF95A5A6)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Queue track list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        count = currentQueue.size,
                        key = { index -> currentQueue[index].id }
                    ) { index ->
                        val track = currentQueue[index]
                        val isCurrentTrack = queueState.currentIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .clickable {
                                    coroutineScope.launch {
                                        if (queueState.currentIndex != index) {
                                            playerViewModel.playQueueFromIndex(index)
                                        } else {
                                            playerViewModel.resumeIfPaused()
                                        }
                                    }
                                    Log.d("QueueScreen", "Starting queue from index: $index")
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Position and status indicator
                            Text(
                                text = if (isCurrentTrack) "♪ " else "${index + 1}. ",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = if (isCurrentTrack) Color(0xFF4ECDC4) else Color(0xFF95A5A6)
                                ),
                                modifier = Modifier.width(32.dp)
                            )

                            // Track name
                            MarqueeText(
                                text = track.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = if (isCurrentTrack) Color(0xFFE0E0E0) else Color(0xFFBDC3C7)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // Remove from queue button
                            TextButton(
                                onClick = {
                                    playerViewModel.removeFromQueue(index)
                                    Log.d("QueueScreen", "Removed track from queue at index: $index")
                                }
                            ) {
                                Text(
                                    text = "×",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF95A5A6)
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty queue message
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Color(0xFF95A5A6)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            }
        } else {
            Text(
                text = "Player not available",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = Color(0xFFE74C3C)
                ),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
