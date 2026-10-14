package com.plyr.desktop.search

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.TrackRecord
import com.plyr.desktop.ui.theme.PlyrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de UI de [YouTubePlaylistDetailView] (M19): pistas, reproducir todo,
 * guardar y estados de carga/vacío. Se renderiza con estado falso, sin red.
 */
@OptIn(ExperimentalTestApi::class)
class YouTubePlaylistDetailViewTest {

    private fun track(index: Int) = TrackRecord(
        id = "t$index",
        playlistId = "youtube_PL1",
        remoteTrackId = "vid0000000$index",
        name = "Track $index",
        artists = "Artist",
        youtubeVideoId = "vid0000000$index",
        audioUrl = null,
        position = index,
        lastSyncTime = 0L,
    )

    private fun state(
        tracks: List<TrackRecord> = listOf(track(0), track(1)),
        isLoading: Boolean = false,
        isSaved: Boolean = false,
    ) = YouTubePlaylistDetailState(
        playlistId = "PL1",
        title = "Mix",
        uploader = "DJ",
        tracks = tracks,
        isLoading = isLoading,
        isSaved = isSaved,
    )

    @Test
    fun alPulsarUnaPistaReproduceDesdeElla() = runComposeUiTest {
        var playedIndex: Int? = null
        var playedSize: Int? = null
        setContent {
            PlyrTheme {
                YouTubePlaylistDetailView(
                    state = state(),
                    onPlayTracks = { tracks, index ->
                        playedSize = tracks.size
                        playedIndex = index
                    },
                    onToggleSave = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("youtube_playlist_track_1").performClick()

        assertEquals(1, playedIndex)
        assertEquals(2, playedSize)
    }

    @Test
    fun reproducirTodoEmpiezaPorLaPrimera() = runComposeUiTest {
        var playedIndex: Int? = -1
        setContent {
            PlyrTheme {
                YouTubePlaylistDetailView(
                    state = state(),
                    onPlayTracks = { _, index -> playedIndex = index },
                    onToggleSave = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("youtube_playlist_play_all").performClick()

        assertEquals(0, playedIndex)
    }

    @Test
    fun elBotonGuardarInvocaElCallback() = runComposeUiTest {
        var toggled = false
        setContent {
            PlyrTheme {
                YouTubePlaylistDetailView(
                    state = state(),
                    onPlayTracks = { _, _ -> },
                    onToggleSave = { toggled = true },
                    onBack = {},
                )
            }
        }

        onNodeWithTag("youtube_playlist_save").performClick()

        assertTrue(toggled)
    }

    @Test
    fun enCargaMuestraElIndicador() = runComposeUiTest {
        setContent {
            PlyrTheme {
                YouTubePlaylistDetailView(
                    state = state(tracks = emptyList(), isLoading = true),
                    onPlayTracks = { _, _ -> },
                    onToggleSave = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("youtube_playlist_loading").assertIsDisplayed()
    }

    @Test
    fun sinPistasMuestraElMensajeVacio() = runComposeUiTest {
        setContent {
            PlyrTheme {
                YouTubePlaylistDetailView(
                    state = state(tracks = emptyList()),
                    onPlayTracks = { _, _ -> },
                    onToggleSave = {},
                    onBack = {},
                )
            }
        }

        onNodeWithTag("youtube_playlist_empty").assertIsDisplayed()
    }

    @Test
    fun elBotonVolverInvocaElCallback() = runComposeUiTest {
        var back = false
        setContent {
            PlyrTheme {
                YouTubePlaylistDetailView(
                    state = state(),
                    onPlayTracks = { _, _ -> },
                    onToggleSave = {},
                    onBack = { back = true },
                )
            }
        }

        onNodeWithTag("back_from_youtube_playlist").performClick()

        assertTrue(back)
    }
}
