package com.plyr.ui.utils

import com.plyr.network.AppArtist
import com.plyr.network.AppTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la lógica pura extraída de `PlaylistScreen`: interpretación de ids
 * de YouTube, autor desde la descripción y mapeo de resultados de búsqueda.
 */
class PlaylistMappingsTest {

    // --- youtubeAuthorFromDescription ---

    @Test
    fun authorIsNullWhenDescriptionIsNull() {
        assertNull(youtubeAuthorFromDescription(null))
    }

    @Test
    fun authorIsNullWhenDescriptionHasNoPrefix() {
        assertNull(youtubeAuthorFromDescription("Una playlist cualquiera"))
    }

    @Test
    fun authorExtractsChannelName() {
        assertEquals("USER", youtubeAuthorFromDescription("YouTube Playlist by USER"))
    }

    @Test
    fun authorIsNullWhenPrefixIsFollowedOnlyByWhitespace() {
        assertNull(youtubeAuthorFromDescription("YouTube Playlist by    "))
    }

    @Test
    fun authorIsNullWhenOnlyPrefix() {
        assertNull(youtubeAuthorFromDescription("YouTube Playlist by "))
    }

    // --- isYouTubePlaylistId / stripYouTubePlaylistId ---

    @Test
    fun youtubeIdIsDetected() {
        assertTrue(isYouTubePlaylistId("youtube_abc"))
        assertFalse(isYouTubePlaylistId("spotify_abc"))
        assertFalse(isYouTubePlaylistId("liked_songs"))
        assertFalse(isYouTubePlaylistId(null))
    }

    @Test
    fun stripRemovesOnlyThePrefix() {
        assertEquals("abc", stripYouTubePlaylistId("youtube_abc"))
        assertEquals("liked_songs", stripYouTubePlaylistId("liked_songs"))
    }

    // --- isYouTubeVideoId ---

    @Test
    fun videoIdMustBeExactlyElevenChars() {
        assertTrue(isYouTubeVideoId("dQw4w9WgXcQ"))
        assertFalse(isYouTubeVideoId("dQw4w9WgXc"))
        assertFalse(isYouTubeVideoId("dQw4w9WgXcQQ"))
    }

    // --- buildSearchTrackEntities ---

    private fun track(id: String, name: String, artist: String) =
        AppTrack(id = id, name = name, artists = listOf(AppArtist(artist)))

    @Test
    fun mapsFieldsAndIdFormat() {
        val tracks = listOf(
            track("aaaaaaaaaaa", "Uno", "Artista A"),
            track("bbbbbbbbbbb", "Dos", "Artista B"),
        )

        val entities = buildSearchTrackEntities(tracks, idPrefix = "yt_search", timestamp = 1234L)

        assertEquals(2, entities.size)
        assertEquals("yt_search_aaaaaaaaaaa_0", entities[0].id)
        assertEquals("yt_search_bbbbbbbbbbb_1", entities[1].id)
        assertEquals("yt_search_1234", entities[0].playlistId)
        assertEquals("aaaaaaaaaaa", entities[0].remoteTrackId)
        assertEquals("Uno", entities[0].name)
        assertEquals("Artista A", entities[0].artists)
        assertEquals(0, entities[0].position)
        assertEquals(1, entities[1].position)
        assertEquals(1234L, entities[0].lastSyncTime)
        assertNull(entities[0].youtubeVideoId)
        assertNull(entities[0].audioUrl)
    }

    @Test
    fun capsAtMaxTracks() {
        val tracks = (1..15).map { track("video$it", "T$it", "A") }

        val entities = buildSearchTrackEntities(tracks, idPrefix = "yt_search", timestamp = 1L)

        assertEquals(10, entities.size)
    }

    @Test
    fun joinsMultipleArtists() {
        val multi = AppTrack(
            id = "video",
            name = "T",
            artists = listOf(AppArtist("A"), AppArtist("B")),
        )

        val entities = buildSearchTrackEntities(listOf(multi), idPrefix = "p", timestamp = 0L)

        assertEquals("A, B", entities[0].artists)
    }
}
