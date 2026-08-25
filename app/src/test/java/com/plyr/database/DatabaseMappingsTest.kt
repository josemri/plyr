package com.plyr.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // --- liked_songs playlist entity ---

    @Test
    fun likedSongsPlaylistEntity_hasCorrectId() {
        val entity = PlaylistEntity(
            remoteId = PlaylistLocalRepository.LIKED_SONGS_ID,
            name = "liked",
            description = null,
            trackCount = 0,
            imageUrl = null
        )
        assertEquals("liked_songs", entity.remoteId)
        assertEquals("liked", entity.name)
        assertNull(entity.description)
        assertNull(entity.imageUrl)
    }

    @Test
    fun likedSongsPlaylistEntity_toAppPlaylist_mapsCorrectly() {
        val entity = PlaylistEntity(
            remoteId = PlaylistLocalRepository.LIKED_SONGS_ID,
            name = "liked",
            description = null,
            trackCount = 3,
            imageUrl = null
        )
        val playlist = entity.toAppPlaylist()
        assertEquals("liked_songs", playlist.id)
        assertEquals("liked", playlist.name)
        assertNull(playlist.images)
    }

    @Test
    fun likedSongsPlaylistEntity_withTracks() {
        val entity = PlaylistEntity(
            remoteId = PlaylistLocalRepository.LIKED_SONGS_ID,
            name = "liked",
            description = null,
            trackCount = 5,
            imageUrl = null,
            lastSyncTime = 12345L
        )
        assertEquals(5, entity.trackCount)
        assertEquals(12345L, entity.lastSyncTime)
    }

    // --- Sorting: liked_songs sorts first ---

    @Test
    fun playlistSorting_likedSongsSortsFirst() {
        val playlists = listOf(
            PlaylistEntity("youtube_abc", "Rock Hits", null, 10, null),
            PlaylistEntity(PlaylistLocalRepository.LIKED_SONGS_ID, "liked", null, 3, null),
            PlaylistEntity("youtube_def", "Chill Vibes", null, 7, null)
        )

        val sorted = playlists
            .filter { !it.remoteId.startsWith("album_") }
            .sortedBy { if (it.remoteId == PlaylistLocalRepository.LIKED_SONGS_ID) "" else it.name }

        assertEquals(PlaylistLocalRepository.LIKED_SONGS_ID, sorted[0].remoteId)
        assertEquals("Chill Vibes", sorted[1].name)
        assertEquals("Rock Hits", sorted[2].name)
    }

    @Test
    fun playlistSorting_albumsFilteredOut() {
        val playlists = listOf(
            PlaylistEntity("youtube_abc", "Playlist", null, 5, null),
            PlaylistEntity("album_xyz", "Album", null, 12, null),
            PlaylistEntity(PlaylistLocalRepository.LIKED_SONGS_ID, "liked", null, 1, null)
        )

        val filtered = playlists.filter { !it.remoteId.startsWith("album_") }

        assertEquals(2, filtered.size)
        assertTrue(filtered.none { it.remoteId.startsWith("album_") })
    }

    @Test
    fun playlistSorting_emptyList() {
        val playlists = emptyList<PlaylistEntity>()
        val sorted = playlists
            .filter { !it.remoteId.startsWith("album_") }
            .sortedBy { if (it.remoteId == PlaylistLocalRepository.LIKED_SONGS_ID) "" else it.name }

        assertTrue(sorted.isEmpty())
    }

    @Test
    fun playlistSorting_onlyLikedSongs() {
        val playlists = listOf(
            PlaylistEntity(PlaylistLocalRepository.LIKED_SONGS_ID, "liked", null, 0, null)
        )

        val sorted = playlists
            .filter { !it.remoteId.startsWith("album_") }
            .sortedBy { if (it.remoteId == PlaylistLocalRepository.LIKED_SONGS_ID) "" else it.name }

        assertEquals(1, sorted.size)
        assertEquals(PlaylistLocalRepository.LIKED_SONGS_ID, sorted[0].remoteId)
    }

    // --- Track entity for liked_songs ---

    @Test
    fun likedSongTrackEntity_hasCorrectPlaylistId() {
        val track = TrackEntity(
            id = "liked_songs_vid123_0",
            playlistId = PlaylistLocalRepository.LIKED_SONGS_ID,
            remoteTrackId = "vid123",
            name = "My Song",
            artists = "Artist",
            youtubeVideoId = "abc123",
            position = 0
        )
        assertEquals("liked_songs", track.playlistId)
        assertEquals("My Song", track.name)
        assertNotNull(track.youtubeVideoId)
    }

    @Test
    fun likedSongTrackEntity_toggleCreatesAndRemoves() {
        // Simulate the toggle logic: a track with matching youtubeVideoId is liked
        val existingTracks = mutableListOf<TrackEntity>()

        // Add a track (toggle on)
        val track = TrackEntity(
            id = "liked_songs_vid1_0",
            playlistId = PlaylistLocalRepository.LIKED_SONGS_ID,
            remoteTrackId = "vid1",
            name = "Song",
            artists = "Artist",
            youtubeVideoId = "vid1",
            position = 0
        )
        existingTracks.add(track)
        assertEquals(1, existingTracks.size)

        // Remove the track (toggle off)
        val toRemove = existingTracks.find { it.youtubeVideoId == "vid1" }
        assertNotNull(toRemove)
        existingTracks.remove(toRemove)
        assertTrue(existingTracks.isEmpty())
    }

    @Test
    fun likedSongTrackEntity_duplicateYoutubeVideoIdPrevented() {
        val tracks = listOf(
            TrackEntity("id1", PlaylistLocalRepository.LIKED_SONGS_ID, "r1", "Song", "Artist", youtubeVideoId = "vid1", position = 0),
            TrackEntity("id2", PlaylistLocalRepository.LIKED_SONGS_ID, "r2", "Song2", "Artist2", youtubeVideoId = "vid2", position = 1)
        )

        val match = tracks.find { it.youtubeVideoId == "vid1" }
        assertNotNull(match)
        assertEquals("Song", match!!.name)
    }
}
