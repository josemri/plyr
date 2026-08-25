package com.plyr.utils

import com.plyr.database.PlaylistLocalRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SpotifyImporter: extractPlaylistId parsing, ImportResult,
 * data classes, and progress callback signature.
 */
class SpotifyImporterTest {

    // --- extractPlaylistId ---

    @Test
    fun extractPlaylistId_fullOpenSpotifyUrl() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId(
                "https://open.spotify.com/playlist/6Ue8iXcrM1F1NRoHsi7Vli?si=abc123"
            )
        )
    }

    @Test
    fun extractPlaylistId_fullOpenSpotifyUrlNoQuery() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/6Ue8iXcrM1F1NRoHsi7Vli")
        )
    }

    @Test
    fun extractPlaylistId_spotifyUri() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId("spotify:playlist:6Ue8iXcrM1F1NRoHsi7Vli")
        )
    }

    @Test
    fun extractPlaylistId_shortSpotifyDomain() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId("https://spotify.com/playlist/6Ue8iXcrM1F1NRoHsi7Vli")
        )
    }

    @Test
    fun extractPlaylistId_bare22CharId() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId("6Ue8iXcrM1F1NRoHsi7Vli")
        )
    }

    @Test
    fun extractPlaylistId_bareIdWithSpaces() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId("  6Ue8iXcrM1F1NRoHsi7Vli  ")
        )
    }

    @Test
    fun extractPlaylistId_invalidUrl() {
        assertNull(SpotifyImporter.extractPlaylistId("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun extractPlaylistId_emptyString() {
        assertNull(SpotifyImporter.extractPlaylistId(""))
    }

    @Test
    fun extractPlaylistId_tooShortId() {
        assertNull(SpotifyImporter.extractPlaylistId("abc123"))
    }

    @Test
    fun extractPlaylistId_tooLongId() {
        assertNull(SpotifyImporter.extractPlaylistId("6Ue8iXcrM1F1NRoHsi7VliX"))
    }

    @Test
    fun extractPlaylistId_trackUrlReturnsNull() {
        assertNull(
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/track/abc123")
        )
    }

    @Test
    fun extractPlaylistId_albumUrlReturnsNull() {
        assertNull(
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/album/abc123")
        )
    }

    @Test
    fun extractPlaylistId_hashFragment() {
        assertEquals(
            "6Ue8iXcrM1F1NRoHsi7Vli",
            SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/6Ue8iXcrM1F1NRoHsi7Vli#section")
        )
    }

    @Test
    fun extractPlaylistId_nonPlaylistSpotifyUrl() {
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/artist/abc123"))
    }

    @Test
    fun extractPlaylistId_lowerAndUpperCaseAccepted() {
        assertEquals(
            "AbCdEfGhIjKlMnOpQrStUv",
            SpotifyImporter.extractPlaylistId("AbCdEfGhIjKlMnOpQrStUv")
        )
    }

    // --- ImportResult ---

    @Test
    fun importResult_successDefaultsNotSkipped() {
        val result = SpotifyImporter.ImportResult(success = true, message = "ok")
        assertTrue(result.success)
        assertEquals("ok", result.message)
        assertFalse(result.skipped)
    }

    @Test
    fun importResult_skipped() {
        val result = SpotifyImporter.ImportResult(success = true, message = "already imported", skipped = true)
        assertTrue(result.success)
        assertTrue(result.skipped)
    }

    @Test
    fun importResult_failure() {
        val result = SpotifyImporter.ImportResult(success = false, message = "error: bad url")
        assertFalse(result.success)
        assertFalse(result.skipped)
    }

    // --- SpotifyTrack ---

    @Test
    fun spotifyTrack_holdsFields() {
        val track = SpotifyImporter.SpotifyTrack("Song", listOf("Artist A", "Artist B"), 240000)
        assertEquals("Song", track.name)
        assertEquals(listOf("Artist A", "Artist B"), track.artists)
        assertEquals(240000L, track.durationMs)
    }

    @Test
    fun spotifyTrack_equalityByContent() {
        val a = SpotifyImporter.SpotifyTrack("Song", listOf("Artist"), 100)
        val b = SpotifyImporter.SpotifyTrack("Song", listOf("Artist"), 100)
        assertEquals(a, b)
    }

    // --- SpotifyPlaylist ---

    @Test
    fun spotifyPlaylist_holdsFields() {
        val tracks = listOf(
            SpotifyImporter.SpotifyTrack("One", listOf("A"), 100),
            SpotifyImporter.SpotifyTrack("Two", listOf("B"), 200)
        )
        val playlist = SpotifyImporter.SpotifyPlaylist("id123", "My Playlist", "http://img.jpg", tracks)
        assertEquals("id123", playlist.id)
        assertEquals("My Playlist", playlist.name)
        assertEquals("http://img.jpg", playlist.imageUrl)
        assertEquals(2, playlist.tracks.size)
    }

    @Test
    fun spotifyPlaylist_nullImageUrl() {
        val playlist = SpotifyImporter.SpotifyPlaylist("id", "Name", null, emptyList())
        assertNull(playlist.imageUrl)
    }

    // --- LIKED_SONGS_ID constant ---

    @Test
    fun likedSongsId_isCorrectValue() {
        assertEquals("liked_songs", PlaylistLocalRepository.LIKED_SONGS_ID)
    }
}
