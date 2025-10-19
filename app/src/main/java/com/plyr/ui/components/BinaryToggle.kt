package com.plyr.ui.components

import androidx.compose.runtime.Composable

/**
 * Backwards-compatible BinaryToggle implemented using MultiToggle
 */
@Composable
fun BinaryToggle(
    option1: String,
    option2: String,
    initialValue: Boolean = true,
    onChange: ((Boolean) -> Unit)? = null
) {
    // Map boolean initial to index
    val initialIndex = if (initialValue) 0 else 1

    MultiToggle(
        options = listOf(option1, option2),
        initialIndex = initialIndex,
        onChange = { idx -> onChange?.invoke(idx == 0) }
    )
}
