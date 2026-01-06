package com.plyr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable collapsible section component for ConfigScreen and similar screens.
 * Provides a consistent UI pattern for expandable/collapsible content sections.
 *
 * @param title Main title text for the section
 * @param statusText Optional status text shown on the right (e.g., "configured", "enabled")
 * @param statusColor Color for the status text
 * @param initialExpanded Whether the section starts expanded
 * @param content Composable content to show when expanded
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    statusColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    initialExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var isExpanded by remember { mutableStateOf(initialExpanded) }
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier) {
        // Header row - clickable to expand/collapse
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = statusColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Expandable content
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, bottom = 16.dp),
                content = content
            )
        }
    }
}

