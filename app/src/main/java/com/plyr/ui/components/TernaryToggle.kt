package com.plyr.ui.components

import androidx.compose.runtime.Composable

/**
 * Backwards-compatible TernaryToggle implemented using MultiToggle
 */
@Composable
fun TernaryToggle(
    option1: String,
    option2: String,
    option3: String,
    initialValue: Int = 0,
    onChange: ((Int) -> Unit)? = null
) {
    MultiToggle(
        options = listOf(option1, option2, option3),
        initialIndex = initialValue,
        onChange = onChange
    )
}
