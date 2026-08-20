package com.plyr.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DatabaseMappingsTest {

    @Test
    fun playlistEntity_toAppPlaylist_mapsFields() {
        val entity = PlaylistEntity(
            remoteId = "youtube_abc",
            name = "My Playlist",
            description = "YouTube Playlist by Channel",
            trackCount = 5,
            imageUrl = "http://img"
        )
        val playlist = entity.toAppPlaylist()
        assertEquals("youtube_abc", playlist.id)
        assertEquals("My Playlist", playlist.name)
        assertEquals("YouTube Playlist by Channel", playlist.description)
        assertNull(playlist.tracks)
    }

    @Test
    fun trackEntity_toAppTrack_splitsArtists() {
        val entity = TrackEntity(
            id = "id1",
            playlistId = "youtube_abc",
            remoteTrackId = "vid123",
            name = "Song",
            artists = "Artist A, Artist B",
            position = 0
        )
        val track = entity.toAppTrack()
        assertEquals("vid123", track.id)
        assertEquals("Song", track.name)
        assertEquals(listOf("Artist A", "Artist B"), track.artists.map { it.name })
    }

    @Test
    fun trackEntity_toAppTrack_singleArtist() {
        val entity = TrackEntity("id2", "youtube_abc", "vid456", "Song", "Solo Artist", position = 1)
        assertEquals(listOf("Solo Artist"), entity.toAppTrack().artists.map { it.name })
    }
}
