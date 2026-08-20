package com.plyr.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plyr.database.PlaylistDatabase
import com.plyr.database.PlaylistLocalRepository
import com.plyr.ui.components.*
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.Translations
import com.plyr.utils.UrlParser
import com.plyr.viewmodel.PlayerViewModel

@SuppressLint("DiscouragedApi")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    context: Context,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToScreen: (Screen) -> Unit,
    onOpenPlaylist: (String) -> Unit = {}
) {
    val dimensions = calculateResponsiveDimensionsFallback()

    var showExitMessage by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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

    val buttons = listOf(
        ActionButtonData(
            text = "< ${Translations.get(context, "home_feed")} >",
            color = MaterialTheme.colorScheme.primary,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onNavigateToScreen(Screen.FEED)
            }
        )
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (dimensions.showSideBySideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedRes != 0) {
                    val painter = painterResource(id = selectedRes)
                    val intrinsic = painter.intrinsicSize
                    var imgModifier = Modifier
                        .widthIn(max = dimensions.imageMaxWidth)
                        .heightIn(max = dimensions.imageMaxHeight)
                        .padding(end = 16.dp)
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

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HomePlaylistCarousel(
                        context = context,
                        onOpenPlaylist = onOpenPlaylist
                    )

                    ActionButtonsGroup(
                        buttons = buttons,
                        isHorizontal = true,
                        spacing = 24.dp,
                        fontSize = 20.sp,
                        modifier = Modifier.wrapContentWidth()
                    )

                    if (showExitMessage) {
                        Spacer(modifier = Modifier.height(24.dp))
                        PlyrErrorText(
                            text = Translations.get(context, "exit_message"),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (selectedRes != 0) {
                    val painter = painterResource(id = selectedRes)
                    val intrinsic = painter.intrinsicSize
                    var imgModifier = Modifier
                        .widthIn(max = dimensions.imageMaxWidth)
                        .heightIn(max = dimensions.imageMaxHeight)
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
                    Spacer(modifier = Modifier.height(40.dp))
                }

                HomePlaylistCarousel(
                    context = context,
                    onOpenPlaylist = onOpenPlaylist
                )

                Spacer(modifier = Modifier.height(24.dp))
                ActionButtonsGroup(
                    buttons = buttons,
                    isHorizontal = true,
                    spacing = 24.dp,
                    fontSize = 20.sp,
                    modifier = Modifier.wrapContentWidth()
                )

                if (showExitMessage) {
                    Spacer(modifier = Modifier.height(24.dp))
                    PlyrErrorText(
                        text = Translations.get(context, "exit_message"),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

/**
 * Carrusel horizontal de playlists.
 * Empieza siempre por el principio (primera playlist a la izquierda).
 */
@Composable
private fun HomePlaylistCarousel(
    context: Context,
    onOpenPlaylist: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val playlistRepository = remember { PlaylistLocalRepository(context) }
    val playlistEntities by playlistRepository.getAllPlaylistsLiveData()
        .asFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists = playlistEntities.filter {
        it.remoteId != "liked_songs" && !it.remoteId.startsWith("album_")
    }

    val listState = remember { LazyListState() }
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
        items(playlists, key = { it.remoteId }) { playlist ->
            val coverUrl = UrlParser.normalizeYoutubeThumb(playlist.imageUrl)
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenPlaylist(playlist.remoteId)
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
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
    }
}
