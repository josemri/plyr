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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plyr.database.PlaylistLocalRepository
import com.plyr.ui.components.*
import com.plyr.ui.theme.PlyrSymbols
import com.plyr.ui.utils.calculateResponsiveDimensionsFallback
import com.plyr.utils.Translations
import com.plyr.utils.UpdateChecker
import com.plyr.utils.UrlParser
import com.plyr.utils.getPackageInfoCompat

@SuppressLint("DiscouragedApi")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    context: Context,
    onNavigateToScreen: (Screen) -> Unit,
    onOpenPlaylist: (String) -> Unit = {}
) {
    val dimensions = calculateResponsiveDimensionsFallback()

    val showExitMessage by remember { mutableStateOf(false) }
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
                    if (intrinsic != Size.Unspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                        imgModifier = imgModifier.aspectRatio(intrinsic.width / intrinsic.height)
                    }
                    Column(
                        modifier = Modifier
                            .widthIn(max = dimensions.imageMaxWidth)
                            .padding(end = 16.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = Translations.get(context, "app_logo"),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = imgModifier
                        )
                        VersionBadge(context = context)
                    }
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
                    Column(
                        modifier = Modifier.widthIn(max = dimensions.imageMaxWidth),
                        horizontalAlignment = Alignment.End
                    ) {
                        Image(
                            painter = painter,
                            contentDescription = Translations.get(context, "app_logo"),
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = imgModifier
                        )
                        VersionBadge(context = context)
                    }
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
 * Indicador compacto de versión, colocado bajo el logo.
 *
 * Si la app está al día muestra solo "v1.1.0". Si hay una release nueva muestra
 * "● actualiza v1.1.0 → v1.2.0" y, si la release trae APK, es pulsable para abrir
 * la descarga. Ocupa una sola línea en cualquier caso.
 */
@Composable
private fun VersionBadge(context: Context) {
    val uriHandler = LocalUriHandler.current

    val currentVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfoCompat(context.packageName).versionName
        }.getOrNull() ?: "1.0"
    }

    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    LaunchedEffect(context) {
        updateInfo = UpdateChecker.checkForUpdate(context)
    }

    val latestVersion = updateInfo
        ?.takeIf { it.isUpdateAvailable && it.latestVersion.isNotBlank() }
        ?.latestVersion

    val downloadUrl = if (latestVersion != null) updateInfo?.downloadUrl.orEmpty() else ""

    val text = if (latestVersion != null) {
        "${PlyrSymbols.BULLET} ${Translations.get(context, "update_available")} " +
            "v$currentVersion ${PlyrSymbols.ARROW} v$latestVersion"
    } else {
        "v$currentVersion"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (latestVersion != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = downloadUrl.isNotBlank()) { uriHandler.openUri(downloadUrl) }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
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
    val playlists = PlaylistLocalRepository.visiblePlaylists(playlistEntities)

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
            val isLiked = playlist.remoteId == "liked_songs"
            val coverUrl = if (isLiked) null else UrlParser.normalizeYoutubeThumb(playlist.imageUrl)
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
                if (isLiked) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♥",
                            fontSize = 48.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else if (coverUrl != null) {
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
                    color = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
