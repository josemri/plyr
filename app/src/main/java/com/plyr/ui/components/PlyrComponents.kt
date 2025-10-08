package com.plyr.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import com.plyr.ui.theme.*

@Composable
fun PlyrMenuOption(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current

    Text(
        text = "${PlyrSymbols.PROMPT}$text",
        style = PlyrTextStyles.menuOption().copy(textAlign = TextAlign.Center),
        modifier = modifier
            .clickable(enabled = enabled) {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            }
            .padding(PlyrSpacing.xs)
    )
}

@Composable
fun PlyrErrorText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = PlyrTextStyles.errorText(),
        modifier = modifier
    )
}

@Composable
fun PlyrInfoText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = PlyrTextStyles.infoText(),
        modifier = modifier
    )
}

// === COMPONENTES DE SELECCIÓN ===

// === COMPONENTES DE LAYOUT ===

@Composable
fun PlyrScreenContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PlyrSpacing.large),
        verticalArrangement = Arrangement.spacedBy(PlyrSpacing.medium),
        content = content
    )
}

// === COMPONENTES DE BOTONES ===


// === COMPONENTES DE ESTADO ===

@Composable
fun PlyrLoadingIndicator(
    text: String = "loading",
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$text${PlyrSymbols.LOADING}",
            style = PlyrTextStyles.trackArtist()
        )
    }
}

// === COMPONENTES DE LISTA ===

// === ESPACIADORES PREDEFINIDOS ===

@Composable
fun PlyrSmallSpacer() = Spacer(modifier = Modifier.height(PlyrSpacing.small))

@Composable
fun PlyrMediumSpacer() = Spacer(modifier = Modifier.height(PlyrSpacing.medium))

