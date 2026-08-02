package com.plyr.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de los extractores de IDs de URLs del feed (YouTube/Spotify) y del formato
 * de timestamps relativos. Se accede vía reflexión por ser funciones privadas.
 */
class UrlExtractorTest {

    @Test
    fun youTubeVideoId_watchParam() {
        assertEquals("abc123", extractVideoId("https://youtube.com/watch?v=abc123&t=30"))
    }

    @Test
    fun youTubeVideoId_youtuBeShort() {
        assertEquals("xyz987", extractVideoId("https://youtu.be/xyz987?si=foo"))
    }

    @Test
    fun youTubeVideoId_watchPath() {
        assertEquals("def456", extractVideoId("https://youtube.com/watch/def456"))
    }

    @Test
    fun youTubeVideoId_shorts() {
        assertEquals("ghi789", extractVideoId("https://youtube.com/shorts/ghi789?feature=share"))
    }

    @Test
    fun youTubeVideoId_noMatch() {
        assertNull(extractVideoId("https://example.com/video"))
    }

    @Test
    fun youTubePlaylistId_listParam() {
        assertEquals("PLABC", extractPlaylistId("https://youtube.com/playlist?list=PLABC&x=1"))
    }

    @Test
    fun youTubePlaylistId_listOnWatchUrl() {
        assertEquals("PLXYZ", extractPlaylistId("https://youtube.com/watch?v=abc&list=PLXYZ"))
    }

    @Test
    fun youTubePlaylistId_playlistPath() {
        assertEquals("PL123", extractPlaylistId("https://youtube.com/playlist/PL123"))
    }

    @Test
    fun youTubePlaylistId_noMatch() {
        assertNull(extractPlaylistId("https://example.com/list/x"))
    }

    @Test
    fun spotifyId_track() {
        assertEquals("abc123", extractSpotifyId("https://open.spotify.com/track/abc123"))
    }

    @Test
    fun spotifyId_withQuery() {
        assertEquals("xyz", extractSpotifyId("https://open.spotify.com/album/xyz?si=foo"))
    }

    @Test
    fun formatTimestamp_nowUnderAMinute() {
        assertEquals("now", formatTimestamp(System.currentTimeMillis() - 30_000))
    }

    @Test
    fun formatTimestamp_minutes() {
        assertEquals("5m", formatTimestamp(System.currentTimeMillis() - 5 * 60_000))
    }

    @Test
    fun formatTimestamp_hours() {
        assertEquals("3h", formatTimestamp(System.currentTimeMillis() - 3 * 60 * 60_000))
    }

    @Test
    fun formatTimestamp_days() {
        assertEquals("2d", formatTimestamp(System.currentTimeMillis() - 2 * 24 * 60 * 60_000))
    }

    private fun extractVideoId(url: String): String? =
        invokeStatic("extractYoutubeVideoId", arrayOf(String::class.java), arrayOf(url)) as String?

    private fun extractPlaylistId(url: String): String? =
        invokeStatic("extractYoutubePlaylistId", arrayOf(String::class.java), arrayOf(url)) as String?

    private fun extractSpotifyId(url: String): String? =
        invokeStatic("extractSpotifyId", arrayOf(String::class.java), arrayOf(url)) as String?

    private fun formatTimestamp(timestamp: Long): String =
        invokeStatic("formatTimestamp", arrayOf(Long::class.javaPrimitiveType!!), arrayOf(timestamp)) as String

    private fun invokeStatic(name: String, paramTypes: Array<Class<*>>, args: Array<Any?>): Any? {
        val method = Class.forName("com.plyr.ui.FeedScreenKt")
            .getDeclaredMethod(name, *paramTypes)
        method.isAccessible = true
        return method.invoke(null, *args)
    }
}
