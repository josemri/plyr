package com.plyr.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de los mapeos entre entidades locales y modelos de Spotify.
 */
class DatabaseMappingsTest {

    @Test
    fun playlistEntity_toSpotifyPlaylist_mapsFields() {
        val entity = PlaylistEntity(
            spotifyId = "youtube_abc",
            name = "My Playlist",
            description = "YouTube Playlist by Channel",
            trackCount = 5,
            imageUrl = "http://img"
        )
        val spotify = entity.toSpotifyPlaylist()
        assertEquals("youtube_abc", spotify.id)
        assertEquals("My Playlist", spotify.name)
        assertEquals("YouTube Playlist by Channel", spotify.description)
        assertNull(spotify.tracks)
    }

    @Test
    fun trackEntity_toSpotifyTrack_splitsArtists() {
        val entity = TrackEntity(
            id = "id1",
            playlistId = "youtube_abc",
            spotifyTrackId = "vid123",
            name = "Song",
            artists = "Artist A, Artist B",
            position = 0
        )
        val spotify = entity.toSpotifyTrack()
        assertEquals("vid123", spotify.id)
        assertEquals("Song", spotify.name)
        assertEquals(listOf("Artist A", "Artist B"), spotify.artists.map { it.name })
    }

    @Test
    fun trackEntity_toSpotifyTrack_singleArtist() {
        val entity = TrackEntity("id2", "youtube_abc", "vid456", "Song", "Solo Artist", position = 1)
        assertEquals(listOf("Solo Artist"), entity.toSpotifyTrack().artists.map { it.name })
    }
}
