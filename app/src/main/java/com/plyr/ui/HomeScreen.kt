package com.plyr.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plyr.database.PlaylistLocalRepository
import com.plyr.ui.components.*
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.viewmodel.PlayerViewModel

@SuppressLint("DiscouragedApi")
@Composable
fun HomeScreen(
    context: Context,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToScreen: (Screen) -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onCreatePlaylist: () -> Unit = {}
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

            // Botones - el carrusel de playlists se muestra entre search y queue
            val topButtons = listOf(
                ActionButtonData(
                    text = "< ${Translations.get(context, "home_search")} >",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToScreen(Screen.SEARCH)
                    }
                )
            )
            val bottomButtons = listOf(
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
                            buttons = topButtons,
                            isHorizontal = false,
                            spacing = dimensions.itemSpacing,
                            modifier = Modifier.wrapContentWidth()
                        )

                        HomePlaylistCarousel(
                            context = context,
                            onOpenPlaylist = onOpenPlaylist,
                            onCreatePlaylist = onCreatePlaylist
                        )

                        ActionButtonsGroup(
                            buttons = bottomButtons,
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
                        buttons = topButtons,
                        isHorizontal = false,
                        spacing = dimensions.itemSpacing,
                        modifier = Modifier.wrapContentWidth()
                    )

                    HomePlaylistCarousel(
                        context = context,
                        onOpenPlaylist = onOpenPlaylist,
                        onCreatePlaylist = onCreatePlaylist
                    )

                    ActionButtonsGroup(
                        buttons = bottomButtons,
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

/**
 * Carrusel horizontal de playlists (solo playlists, sin secciones de Spotify).
 * Empieza siempre por el principio (primera playlist a la izquierda).
 */
@Composable
private fun HomePlaylistCarousel(
    context: Context,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val playlistRepository = remember { PlaylistLocalRepository(context) }
    val playlistEntities by playlistRepository.getAllPlaylistsLiveData()
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists = playlistEntities.filter {
        it.spotifyId != "liked_songs" && !it.spotifyId.startsWith("album_")
    }

    // Estado no restaurable para que el carrusel arranque siempre por el principio
    val listState = remember { LazyListState() }
    // Al entrar en Home (o cuando cargan las playlists), mostrar la primera a la izquierda
    LaunchedEffect(playlists) {
        if (playlists.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists, key = { it.spotifyId }) { playlist ->
            val coverUrl = UrlParser.normalizeYoutubeThumb(playlist.imageUrl)
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenPlaylist(playlist.spotifyId)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = playlist.name.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }

        item(key = "create_playlist") {
            Column(
                modifier = Modifier
                    .width(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCreatePlaylist()
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = Translations.get(context, "home_new_playlist"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
