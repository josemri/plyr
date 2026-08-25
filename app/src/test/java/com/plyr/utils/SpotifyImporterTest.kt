package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for SpotifyImporter: extractPlaylistId parsing and ImportResult construction.
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
}
