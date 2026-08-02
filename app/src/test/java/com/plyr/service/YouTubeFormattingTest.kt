package com.plyr.service

import com.plyr.service.YouTubeSearchManager.YouTubePlaylistInfo
import com.plyr.service.YouTubeSearchManager.YouTubeVideoInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests del formateo de duración y recuento de vídeos de YouTube.
 */
class YouTubeFormattingTest {

    @Test
    fun duration_formatsMinutes() {
        val video = YouTubeVideoInfo("v1", "Title", "Channel", 185, 1000, null)
        assertEquals("3:05", video.getFormattedDuration())
    }

    @Test
    fun duration_formatsSecondsWithLeadingZero() {
        val video = YouTubeVideoInfo("v1", "Title", "Channel", 65, 1000, null)
        assertEquals("1:05", video.getFormattedDuration())
    }

    @Test
    fun duration_formatsHours() {
        val video = YouTubeVideoInfo("v1", "Title", "Channel", 3661, 1000, null)
        assertEquals("1:01:01", video.getFormattedDuration())
    }

    @Test
    fun duration_liveWhenZeroOrNegative() {
        assertEquals("En vivo", YouTubeVideoInfo("v1", "T", "C", 0, 1000, null).getFormattedDuration())
        assertEquals("En vivo", YouTubeVideoInfo("v1", "T", "C", -5, 1000, null).getFormattedDuration())
    }

    @Test
    fun videoCount_single() {
        val playlist = YouTubePlaylistInfo("p1", "T", "U", 1, null, null)
        assertEquals("1 video", playlist.getFormattedVideoCount())
    }

    @Test
    fun videoCount_hundreds() {
        val playlist = YouTubePlaylistInfo("p1", "T", "U", 250, null, null)
        assertEquals("250 videos", playlist.getFormattedVideoCount())
    }

    @Test
    fun videoCount_thousandsWithDecimal() {
        val playlist = YouTubePlaylistInfo("p1", "T", "U", 1500, null, null)
        assertEquals("1.5K videos", playlist.getFormattedVideoCount())
    }

    @Test
    fun videoCount_thousandsWhole() {
        val playlist = YouTubePlaylistInfo("p1", "T", "U", 2000, null, null)
        assertEquals("2K videos", playlist.getFormattedVideoCount())
    }
}
