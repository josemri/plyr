package com.plyr.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de los modelos de la app (cálculos puros de las data classes).
 */
class AppModelsTest {

    @Test
    fun track_getArtistNames_joinsWithComma() {
        val track = AppTrack(
            id = "t1",
            name = "Song",
            artists = listOf(AppArtist("Artist A"), AppArtist("Artist B"))
        )
        assertEquals("Artist A, Artist B", track.getArtistNames())
    }

    @Test
    fun track_getArtistNames_singleArtist() {
        val track = AppTrack("t2", "Solo", listOf(AppArtist("One")))
        assertEquals("One", track.getArtistNames())
    }

    @Test
    fun track_getArtistNames_emptyArtists() {
        val track = AppTrack("t3", "Solo", emptyList())
        assertEquals("", track.getArtistNames())
    }

    @Test
    fun track_getDisplayName() {
        val track = AppTrack("t4", "Name", listOf(AppArtist("Artist")))
        assertEquals("Name - Artist", track.getDisplayName())
    }

    @Test
    fun playlist_getImageUrl_returnsFirstImage() {
        val playlist = AppPlaylist(
            id = "p1",
            name = "Pl",
            description = null,
            tracks = null,
            images = listOf(AppImage("http://img1", 300, 300), AppImage("http://img2", 100, 100))
        )
        assertEquals("http://img1", playlist.getImageUrl())
    }

    @Test
    fun playlist_getImageUrl_emptyWhenNoImages() {
        val playlist = AppPlaylist("p2", "Pl", null, null, images = null)
        assertEquals("", playlist.getImageUrl())
    }

    @Test
    fun extension_firstImageUrl_returnsFirstOrEmpty() {
        assertEquals("http://img1", listOf(AppImage("http://img1", 1, 1), AppImage("http://img2", 1, 1)).firstImageUrl())
        assertEquals("", (null as List<AppImage>?).firstImageUrl())
        assertEquals("", emptyList<AppImage>().firstImageUrl())
    }

    @Test
    fun extension_artistNames_joinsWithComma() {
        assertEquals("A, B", listOf(AppArtist("A"), AppArtist("B")).artistNames())
        assertEquals("", emptyList<AppArtist>().artistNames())
    }
}
