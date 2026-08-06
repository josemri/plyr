package com.plyr.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de los modelos de Spotify (cálculos puros de las data classes).
 */
class SpotifyModelsTest {

    @Test
    fun track_getArtistNames_joinsWithComma() {
        val track = SpotifyTrack(
            id = "t1",
            name = "Song",
            artists = listOf(SpotifyArtist("Artist A"), SpotifyArtist("Artist B"))
        )
        assertEquals("Artist A, Artist B", track.getArtistNames())
    }

    @Test
    fun track_getArtistNames_singleArtist() {
        val track = SpotifyTrack("t2", "Solo", listOf(SpotifyArtist("One")))
        assertEquals("One", track.getArtistNames())
    }

    @Test
    fun track_getArtistNames_emptyArtists() {
        val track = SpotifyTrack("t3", "Solo", emptyList())
        assertEquals("", track.getArtistNames())
    }

    @Test
    fun track_getDisplayName() {
        val track = SpotifyTrack("t4", "Name", listOf(SpotifyArtist("Artist")))
        assertEquals("Name - Artist", track.getDisplayName())
    }

    @Test
    fun playlist_getImageUrl_returnsFirstImage() {
        val playlist = SpotifyPlaylist(
            id = "p1",
            name = "Pl",
            description = null,
            tracks = null,
            images = listOf(SpotifyImage("http://img1", 300, 300), SpotifyImage("http://img2", 100, 100))
        )
        assertEquals("http://img1", playlist.getImageUrl())
    }

    @Test
    fun playlist_getImageUrl_emptyWhenNoImages() {
        val playlist = SpotifyPlaylist("p2", "Pl", null, null, images = null)
        assertEquals("", playlist.getImageUrl())
    }

    @Test
    fun album_getArtistNames_and_getImageUrl() {
        val album = SpotifyAlbum(
            id = "a1",
            name = "Album",
            artists = listOf(SpotifyArtist("X"), SpotifyArtist("Y")),
            images = listOf(SpotifyImage("http://a", null, null))
        )
        assertEquals("X, Y", album.getArtistNames())
        assertEquals("http://a", album.getImageUrl())
    }

    @Test
    fun album_getImageUrl_emptyWhenNoImages() {
        val album = SpotifyAlbum("a2", "Album", listOf(SpotifyArtist("X")), images = null)
        assertEquals("", album.getImageUrl())
    }

    @Test
    fun artistFull_getImageUrl_returnsFirstImage() {
        val artist = SpotifyArtistFull(
            id = "id",
            name = "name",
            images = listOf(SpotifyImage("http://img", 100, 100)),
            followers = null,
            genres = null
        )
        assertEquals("http://img", artist.getImageUrl())
    }

    @Test
    fun artistFull_getImageUrl_emptyWhenNoImages() {
        val artist = SpotifyArtistFull(id = "id", name = "name", images = null, followers = null, genres = null)
        assertEquals("", artist.getImageUrl())
    }

    @Test
    fun extension_firstImageUrl_returnsFirstOrEmpty() {
        assertEquals("http://img1", listOf(SpotifyImage("http://img1", 1, 1), SpotifyImage("http://img2", 1, 1)).firstImageUrl())
        assertEquals("", (null as List<SpotifyImage>?).firstImageUrl())
        assertEquals("", emptyList<SpotifyImage>().firstImageUrl())
    }

    @Test
    fun extension_artistNames_joinsWithComma() {
        assertEquals("A, B", listOf(SpotifyArtist("A"), SpotifyArtist("B")).artistNames())
        assertEquals("", emptyList<SpotifyArtist>().artistNames())
    }
}
