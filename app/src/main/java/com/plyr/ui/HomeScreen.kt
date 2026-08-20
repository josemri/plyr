package com.plyr.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
import kotlinx.coroutines.launch

@SuppressLint("DiscouragedApi")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    context: Context,
    playerViewModel: PlayerViewModel? = null,
    onNavigateToScreen: (Screen) -> Unit,
    onOpenPlaylist: (String) -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
    onSearchSubmitted: (String) -> Unit = {},
    onShowAllPlaylists: () -> Unit = {}
) {
    // Dimensiones responsivas basadas en el tamaño de pantalla
    val dimensions = calculateResponsiveDimensionsFallback()

    var showExitMessage by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Estado de búsqueda: el campo del Home toma el foco y al pulsar intro se busca
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Si se cierra el teclado sin haber buscado, volver al contenido del Home
    val isImeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus()
        }
    }

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

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Barra de búsqueda superior (real: toma el foco al pulsarla y busca al darle a intro)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = {
                    Text(
                        Translations.get(context, "search_placeholder"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isSearchActive = it.isFocused },
                trailingIcon = {
                    Row {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text(
                                    text = "x",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                        Text(
                            text = Translations.get(context, "search_scan_qr"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            onSearchSubmitted(searchQuery)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.secondary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        if (isSearchActive) {
            // Historial de búsquedas (en azul primario y sin botón <limpiar>)
            HomeSearchHistory(
                context = context,
                onHistoryClick = { query ->
                    onSearchSubmitted(query)
                }
            )
        } else {
            // Contenido principal
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
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
                        // Botón > para ver todas las playlists
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ">",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onShowAllPlaylists()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

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
                    // Botón > para ver todas las playlists
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onShowAllPlaylists()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
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
    }
}

/**
 * Historial de búsquedas del Home (en azul primario y sin botón <limpiar>).
 */
@Composable
private fun HomeSearchHistory(
    context: Context,
    onHistoryClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val searchHistoryDao = remember { PlaylistDatabase.getDatabase(context).searchHistoryDao() }
    val searchHistory by searchHistoryDao.getAllSearches().collectAsState(initial = emptyList())

    if (searchHistory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = Translations.get(context, "search_placeholder"),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(searchHistory, key = { it.id }) { historyItem ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Prefijo del motor de búsqueda original para buscar en el mismo
                        val queryWithPrefix = when (historyItem.searchEngine) {
                            "youtube" -> "yt:${historyItem.query}"
                            else -> historyItem.query
                        }
                        onHistoryClick(queryWithPrefix)
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = historyItem.query,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp, end = 4.dp)
                )
                IconButton(
                    onClick = {
                        // Borrar solo esta entrada del historial (sin botón <limpiar>)
                        scope.launch {
                            searchHistoryDao.deleteSearch(historyItem.id)
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(
                        text = "x",
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
 * Carrusel horizontal de playlists.
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
        it.remoteId != "liked_songs" && !it.remoteId.startsWith("album_")
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

        item(key = "create_playlist") {
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCreatePlaylist()
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
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
