package com.plyr.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.plyr.ui.components.*
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.Translations
import com.plyr.viewmodel.PlayerViewModel

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
                    text = "< ${Translations.get(context, "home_feed")} >",
                    color = MaterialTheme.colorScheme.primary,
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
                        .padding(horizontal = dimensions.screenPadding),
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
                // Layout vertical
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = dimensions.screenPadding),
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

            // Top-right settings icon
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
