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

    @Test
    fun trackEntity_toAppTrack_carriesYoutubeVideoIdAndPosition() {
        // B28: la ida y vuelta AppTrack -> TrackEntity no debe perder el id de YouTube
        val entity = TrackEntity(
            id = "id3",
            playlistId = "youtube_abc",
            remoteTrackId = "vid789",
            name = "Song",
            artists = "Artist",
            youtubeVideoId = "youtube12345",
            position = 4
        )
        val app = entity.toAppTrack()
        assertEquals("youtube12345", app.youtubeVideoId)
        assertEquals(4, app.position)
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

    // --- B1: identidad de la fila de liked_songs ---

    private fun likedRow(
        id: String,
        name: String,
        artists: String,
        youtubeVideoId: String? = null,
        position: Int = 0
    ) = TrackEntity(
        id = id,
        playlistId = PlaylistLocalRepository.LIKED_SONGS_ID,
        remoteTrackId = "r_$id",
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = null,
        position = position
    )

    @Test
    fun likedTrackOf_findsRowByYoutubeVideoId() {
        val tracks = listOf(
            likedRow("a", "Song A", "Artist A", youtubeVideoId = "vidA"),
            likedRow("b", "Song B", "Artist B", youtubeVideoId = "vidB", position = 1)
        )

        val found = PlaylistLocalRepository.likedTrackOf(
            tracks, "Song B", "Artist B", "remoteB", "vidB"
        )

        assertNotNull(found)
        assertEquals("b", found!!.id)
    }

    @Test
    fun likedTrackOf_blankId_fallsBackToNameAndArtists() {
        // Fila guardada por el bug anterior: liked sin youtubeVideoId.
        val tracks = listOf(likedRow("legacy", "Legacy Song", "Legacy Artist"))

        val found = PlaylistLocalRepository.likedTrackOf(
            tracks, "Legacy Song", "Legacy Artist", "remote", ""
        )

        assertNotNull("Sin id debe encontrar la fila por nombre+artista", found)
        assertEquals("legacy", found!!.id)
    }

    @Test
    fun likedTrackOf_nullId_fallsBackToNameAndArtists() {
        val tracks = listOf(likedRow("legacy", "Legacy Song", "Legacy Artist"))

        val found = PlaylistLocalRepository.likedTrackOf(
            tracks, "Legacy Song", "Legacy Artist", "remote", null
        )

        assertEquals("legacy", found?.id)
    }

    @Test
    fun likedTrackOf_emptyId_doesNotMatchAnotherSong() {
        val tracks = listOf(likedRow("a", "Song A", "Artist A", youtubeVideoId = "vidA"))

        val found = PlaylistLocalRepository.likedTrackOf(
            tracks, "Otra canción", "Otro artista", "remote", ""
        )

        assertNull("Sin coincidencia no debe tocar ninguna fila", found)
    }

    @Test
    fun likedTrackOf_videoIdTakesPrecedenceOverSameName() {
        val tracks = listOf(
            likedRow("byName", "Shared Name", "Shared Artist", youtubeVideoId = null),
            likedRow("byId", "Shared Name", "Shared Artist", youtubeVideoId = "vidX", position = 1)
        )

        val found = PlaylistLocalRepository.likedTrackOf(
            tracks, "Shared Name", "Shared Artist", "remote", "vidX"
        )

        assertEquals("Con id, manda el id", "byId", found?.id)
    }

    @Test
    fun likedTrackOf_unknownId_fallsBackToNameAndArtists() {
        val tracks = listOf(likedRow("a", "Song A", "Artist A"))

        val found = PlaylistLocalRepository.likedTrackOf(
            tracks, "Song A", "Artist A", "remote", "vidResueltoDespues"
        )

        assertEquals("El id guardado puede venir de otra resolución", "a", found?.id)
    }

    @Test
    fun likedTrackOf_emptyList_returnsNull() {
        val found = PlaylistLocalRepository.likedTrackOf(
            emptyList(), "Song", "Artist", "remote", "vid"
        )

        assertNull(found)
    }
}
