package com.plyr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import com.plyr.ui.theme.*

/**
 * Componentes UI reutilizables para mantener consistencia visual en toda la aplicación
 */

// === COMPONENTES DE TEXTO ===

@Composable
fun PlyrCommandTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${PlyrSymbols.COMMAND}$text",
        style = PlyrTextStyles.commandTitle(),
        modifier = modifier.padding(bottom = PlyrSpacing.large)
    )
}

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
        style = PlyrTextStyles.menuOption(),
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
fun PlyrTrackItem(
    title: String,
    artist: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(PlyrSpacing.small)
            .fillMaxWidth()
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

        if (artist.isNotBlank()) {
            Text(
                text = artist,
                style = PlyrTextStyles.trackArtist(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlyrSeparator(
    modifier: Modifier = Modifier
) {
    Text(
        text = PlyrSymbols.SEPARATOR,
        style = PlyrTextStyles.separator(),
        modifier = modifier.padding(PlyrSpacing.small)
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

@Composable
fun PlyrSelectableRow(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        options.forEachIndexed { index, option ->
            PlyrSelectableOption(
                text = option,
                isSelected = selectedOption == option,
                onClick = { onOptionSelected(option) }
            )

            if (index < options.size - 1) {
                PlyrSeparator()
            }
        }
    }
}

@Composable
fun PlyrSelectableOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Text(
        text = text,
        style = PlyrTextStyles.selectableOption(isSelected),
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(PlyrSpacing.small)
    )
}

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

@Composable
fun PlyrSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        title?.let {
            PlyrMenuOption(
                text = it,
                onClick = { },
                enabled = false
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(PlyrSpacing.medium),
            content = content
        )

        Spacer(modifier = Modifier.height(PlyrSpacing.xxl))
    }
}

// === COMPONENTES DE BOTONES ===

@Composable
fun PlyrPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(PlyrDimensions.buttonHeight),
        enabled = enabled,
        colors = PlyrButtonStyles.primaryButton(),
        shape = PlyrShapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun PlyrSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(PlyrDimensions.buttonHeight),
        enabled = enabled,
        colors = PlyrButtonStyles.secondaryButton(),
        shape = PlyrShapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// === COMPONENTES DE INPUT ===

@Composable
fun PlyrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                style = PlyrTextStyles.trackArtist()
            )
        },
        modifier = modifier.height(PlyrDimensions.inputHeight),
        enabled = enabled,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = PlyrShapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// === COMPONENTES DE ESTADO ===

@Composable
fun PlyrLoadingIndicator(
    text: String = "loading",
    modifier: Modifier = Modifier
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

@Composable
fun PlyrStatusMessage(
    message: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Text(
        text = "${PlyrSymbols.PROMPT} $message",
        style = if (isError) PlyrTextStyles.errorText() else PlyrTextStyles.infoText(),
        modifier = modifier
    )
}

// === COMPONENTES DE LISTA ===

@Composable
fun PlyrListItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(PlyrSpacing.small)
            .fillMaxWidth()
            .height(PlyrDimensions.listItemHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

            subtitle?.let {
                Text(
                    text = it,
                    style = PlyrTextStyles.trackArtist(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        trailing?.invoke()
    }
}

// === ESPACIADORES PREDEFINIDOS ===

@Composable
fun PlyrSmallSpacer() = Spacer(modifier = Modifier.height(PlyrSpacing.small))

@Composable
fun PlyrMediumSpacer() = Spacer(modifier = Modifier.height(PlyrSpacing.medium))

@Composable
fun PlyrLargeSpacer() = Spacer(modifier = Modifier.height(PlyrSpacing.large))

@Composable
fun PlyrXLSpacer() = Spacer(modifier = Modifier.height(PlyrSpacing.xl))
