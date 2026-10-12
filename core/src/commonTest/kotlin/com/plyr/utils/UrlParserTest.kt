package com.plyr.utils

import com.plyr.model.ScanResult
import com.plyr.utils.UrlParser.UrlType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del parseo centralizado de URLs (UrlParser), que sustituye a los
 * extractores privados de FeedScreen, YouTubeSearchManager, QrScannerDialog,
 * NfcReader y YouTubeManager.
 */
class UrlParserTest {

    // --- extractYoutubeVideoId ---

    @Test
    fun videoId_watchParam() {
        assertEquals("abc123", UrlParser.extractYoutubeVideoId("https://youtube.com/watch?v=abc123&t=30"))
    }

    @Test
    fun videoId_watchParamIgnoresPlaylist() {
        assertEquals("abc123", UrlParser.extractYoutubeVideoId("https://youtube.com/watch?v=abc123&list=PLX"))
    }

    @Test
    fun videoId_youtuBeShort() {
        assertEquals("xyz987", UrlParser.extractYoutubeVideoId("https://youtu.be/xyz987?si=foo"))
    }

    @Test
    fun videoId_watchPath() {
        assertEquals("def456", UrlParser.extractYoutubeVideoId("https://youtube.com/watch/def456"))
    }

    @Test
    fun videoId_shorts() {
        assertEquals("ghi789", UrlParser.extractYoutubeVideoId("https://youtube.com/shorts/ghi789?feature=share"))
    }

    @Test
    fun videoId_fallbackLastSegmentOfLengthEleven() {
        assertEquals("aaaaaaaaaaa", UrlParser.extractYoutubeVideoId("https://youtube.com/v/aaaaaaaaaaa"))
    }

    @Test
    fun videoId_noMatch() {
        assertNull(UrlParser.extractYoutubeVideoId("https://example.com/video"))
    }

    @Test
    fun videoId_empty() {
        assertNull(UrlParser.extractYoutubeVideoId(""))
    }

    // --- extractYoutubePlaylistId ---

    @Test
    fun playlistId_listParam() {
        assertEquals("PLABC", UrlParser.extractYoutubePlaylistId("https://youtube.com/playlist?list=PLABC&x=1"))
    }

    @Test
    fun playlistId_listOnWatchUrl() {
        assertEquals("PLXYZ", UrlParser.extractYoutubePlaylistId("https://youtube.com/watch?v=abc&list=PLXYZ"))
    }

    @Test
    fun playlistId_playlistPath() {
        assertEquals("PL123", UrlParser.extractYoutubePlaylistId("https://youtube.com/playlist/PL123"))
    }

    @Test
    fun playlistId_noMatch() {
        assertNull(UrlParser.extractYoutubePlaylistId("https://example.com/list/x"))
    }

    // --- extractSpotifyId ---

    @Test
    fun spotifyId_track() {
        assertEquals("abc123", UrlParser.extractSpotifyId("https://open.spotify.com/track/abc123"))
    }

    @Test
    fun spotifyId_withQuery() {
        assertEquals("xyz", UrlParser.extractSpotifyId("https://open.spotify.com/album/xyz?si=foo"))
    }

    @Test
    fun spotifyId_noMatch() {
        assertNull(UrlParser.extractSpotifyId("https://open.spotify.com/"))
    }

    // --- parseScanText ---

    @Test
    fun parseScanText_spotifyUrl() {
        assertEquals(ScanResult("spotify", "track", "abc123"), UrlParser.parseScanText("https://open.spotify.com/track/abc123?si=foo"))
    }

    @Test
    fun parseScanText_spotifyArtist() {
        assertEquals(ScanResult("spotify", "artist", "id456"), UrlParser.parseScanText("https://open.spotify.com/artist/id456"))
    }

    @Test
    fun parseScanText_youtubeUrl() {
        assertEquals(ScanResult("youtube", "track", "abc123"), UrlParser.parseScanText("https://www.youtube.com/watch?v=abc123"))
    }

    @Test
    fun parseScanText_youtuBeUrl() {
        assertEquals(ScanResult("youtube", "track", "xyz987"), UrlParser.parseScanText("https://youtu.be/xyz987"))
    }

    @Test
    fun parseScanText_legacySpotify() {
        assertEquals(ScanResult("spotify", "track", "1234567890"), UrlParser.parseScanText("plyr_spotify:track:1234567890"))
    }

    @Test
    fun parseScanText_legacyYoutube() {
        assertEquals(ScanResult("youtube", "track", "abcdefgh"), UrlParser.parseScanText("plyr_youtube:track:abcdefgh"))
    }

    @Test
    fun parseScanText_unknown() {
        assertNull(UrlParser.parseScanText("not a url"))
    }

    // --- getUrlType ---

    @Test
    fun getUrlType_youtube() {
        assertEquals(UrlType.YOUTUBE, UrlParser.getUrlType("https://youtu.be/xyz"))
    }

    @Test
    fun getUrlType_spotify() {
        assertEquals(UrlType.SPOTIFY, UrlParser.getUrlType("https://open.spotify.com/track/x"))
    }

    @Test
    fun getUrlType_unknown() {
        assertEquals(UrlType.UNKNOWN, UrlParser.getUrlType("https://example.com/x"))
    }

    // --- isPlayableUrl ---

    @Test
    fun isPlayableUrl_acceptsYoutubeAndSpotifyAndHttp() {
        assertTrue(UrlParser.isPlayableUrl("https://youtu.be/x"))
        assertTrue(UrlParser.isPlayableUrl("https://open.spotify.com/track/x"))
        assertTrue(UrlParser.isPlayableUrl("http://example.com/file"))
    }

    @Test
    fun isPlayableUrl_rejectsPlainText() {
        assertFalse(UrlParser.isPlayableUrl("hello world"))
    }

    // --- youtubeThumbnailUrl ---

    @Test
    fun thumbnailUrl_usesMqdefault() {
        assertEquals("https://img.youtube.com/vi/abc123/mqdefault.jpg", UrlParser.youtubeThumbnailUrl("abc123"))
    }

    @Test
    fun thumbnailUrl_nullId() {
        assertNull(UrlParser.youtubeThumbnailUrl(null))
    }

    // --- normalizeYoutubeThumb ---

    @Test
    fun normalizeThumb_convertsHqdefaultToMqdefault() {
        assertEquals(
            "https://img.youtube.com/vi/abc123/mqdefault.jpg",
            UrlParser.normalizeYoutubeThumb("https://img.youtube.com/vi/abc123/hqdefault.jpg")
        )
    }

    @Test
    fun normalizeThumb_convertsIYtimg() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/mqdefault.jpg",
            UrlParser.normalizeYoutubeThumb("https://i.ytimg.com/vi/abc123/default.jpg")
        )
    }

    @Test
    fun normalizeThumb_null() {
        assertNull(UrlParser.normalizeYoutubeThumb(null))
    }

    @Test
    fun normalizeThumb_nonYoutubeUrlUnchanged() {
        assertEquals("https://example.com/cover.jpg", UrlParser.normalizeYoutubeThumb("https://example.com/cover.jpg"))
    }
}
