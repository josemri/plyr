package com.plyr.ui.components

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plyr.ui.theme.PlyrDimensions
import com.plyr.ui.theme.PlyrSpacing
import com.plyr.ui.theme.PlyrTextStyles

@Composable
fun SongListItem(
    number: Int,
    title: String,
    artist: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .fillMaxWidth()
            .height(32.dp), // Standard height, no extra padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        Text(
            text = number.toString(),
            style = PlyrTextStyles.trackArtist(),
            modifier = Modifier.padding(end = PlyrSpacing.small) // Less space between number and text
        )

        // Song title and artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = if (isSelected)
                    PlyrTextStyles.selectableOption(true)
                else
                    PlyrTextStyles.trackTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = PlyrTextStyles.trackArtist(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 0.dp) // Remove extra space above artist
            )
        }

        // Action button ("*")
        IconButton(onClick = {
            Log.d("SongListItem", "Track options clicked for: $title")
        }, modifier = Modifier.size(32.dp)) { // Smaller button
            Text(text = "*", style = PlyrTextStyles.menuOption())
        }
    }
}
