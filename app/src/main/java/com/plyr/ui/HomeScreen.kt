package com.plyr.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

            // Botones - queue y feed en la misma línea, settings debajo formando triángulo
            val buttons = listOf(
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
            val settingsButton = ActionButtonData(
                text = "< ${Translations.get(context, "home_settings")} >",
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToScreen(Screen.CONFIG)
                }
            )

            // Main content - responsivo según orientación y tamaño de pantalla
            if (dimensions.showSideBySideLayout) {
                // Layout horizontal para landscape en pantallas grandes
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
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

                    // Carrusel y botones en el otro lado
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HomePlaylistCarousel(
                            context = context,
                            onOpenPlaylist = onOpenPlaylist,
                            onCreatePlaylist = onCreatePlaylist
                        )

                        ActionButtonsGroup(
                            buttons = buttons,
                            isHorizontal = true,
                            spacing = 24.dp,
                            fontSize = 20.sp,
                            modifier = Modifier.wrapContentWidth()
                        )

                        ActionButton(
                            data = settingsButton,
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
                // Layout vertical
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
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
                        // Más espacio entre el logo y el carrusel de playlists
                        Spacer(modifier = Modifier.height(40.dp))
                    }

                    // Carrusel de playlists
                    HomePlaylistCarousel(
                        context = context,
                        onOpenPlaylist = onOpenPlaylist,
                        onCreatePlaylist = onCreatePlaylist
                    )

                    // Queue y feed en la misma línea, más separados del carrusel
                    Spacer(modifier = Modifier.height(24.dp))
                    ActionButtonsGroup(
                        buttons = buttons,
                        isHorizontal = true,
                        spacing = 24.dp,
                        fontSize = 20.sp,
                        modifier = Modifier.wrapContentWidth()
                    )

                    // Settings debajo, formando triángulo con queue y feed
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionButton(
                        data = settingsButton,
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

            // Barra superior: barra de búsqueda (al pulsarla va a SearchScreen)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Barra de búsqueda con la misma apariencia que la del SearchScreen
                // (OutlinedTextField con borde, label en monospace y botón QR)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToScreen(Screen.SEARCH)
                        },
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Translations.get(context, "search_placeholder"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Translations.get(context, "search_scan_qr"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
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
