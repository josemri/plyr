package com.plyr.service

import com.plyr.database.TrackEntity
import com.plyr.network.AppArtist
import com.plyr.network.AppTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de YouTubePlaylistCreator: la playlist creada debe ser la MISMA que la fuente
 * (mismo título, misma descripción, mismos tracks en orden), con cada track resuelto
 * a su vídeo de YouTube usando la integración existente (inyectada aquí como fake).
 */
class YouTubePlaylistCreatorTest {

    private fun track(
        name: String,
        artists: String = "Artist",
        remoteTrackId: String = "s_$name",
        youtubeVideoId: String? = null
    ): TrackEntity = TrackEntity(
        id = "src_$name",
        playlistId = "src_playlist",
        remoteTrackId = remoteTrackId,
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = null,
        position = 0,
        lastSyncTime = 0L
    )

    private fun creator(resolve: (String) -> String? = { "video_$it" }): YouTubePlaylistCreator =
        YouTubePlaylistCreator(resolveVideoId = resolve)

    @Test
    fun build_keepsTitleDescriptionAndTrackOrder() {
        val result = creator().build(
            title = "Rock Hits",
            description = "Los mejores temas",
            sourceTracks = listOf(track("One"), track("Two")),
            targetPlaylistId = "youtube_123"
        )
        assertEquals("Rock Hits", result.title)
        assertEquals("Los mejores temas", result.description)
        assertEquals(listOf("One", "Two"), result.tracks.map { it.name })
    }

    @Test
    fun build_nullDescriptionStaysNull() {
        val result = creator().build("T", null, listOf(track("One")), "youtube_123")
        assertNull(result.description)
    }

    @Test
    fun build_resolvesEachTrackWithNameAndArtistsQuery() {
        val queries = mutableListOf<String>()
        val creator = creator { query ->
            queries.add(query)
            "video_$query"
        }
        val source = listOf(track("One", "Artist A, Artist B"), track("Two", "Solo"))
        creator.build("T", null, source, "youtube_123")
        assertEquals(listOf("One Artist A, Artist B", "Two Solo"), queries)
    }

    @Test
    fun build_setsVideoIdPlaylistIdAndRebuiltId() {
        val result = creator().build("T", null, listOf(track("One")), "youtube_123")
        val out = result.tracks.single()
        assertEquals("video_One Artist", out.youtubeVideoId)
        assertEquals("youtube_123", out.playlistId)
        assertEquals("youtube_123_video_One Artist_0", out.id)
    }

    @Test
    fun build_keepsAlreadyResolvedVideoIdWithoutSearching() {
        var searches = 0
        val creator = creator { searches++; "ignored" }
        val source = listOf(track("One", youtubeVideoId = "already_resolved"))
        val result = creator.build("T", null, source, "youtube_123")
        assertEquals(0, searches)
        assertEquals("already_resolved", result.tracks.single().youtubeVideoId)
    }

    @Test
    fun build_usesResolvedVideoIdsWithoutSearching() {
        var searches = 0
        val creator = creator { searches++; "ignored" }
        val source = listOf(track("One", remoteTrackId = "abcdefghijk"))
        val result = creator.build(
            "T", null, source, "youtube_123",
            resolvedVideoIds = mapOf("abcdefghijk" to "from_map")
        )
        assertEquals(0, searches)
        assertEquals("from_map", result.tracks.single().youtubeVideoId)
    }

    @Test
    fun build_resolvedVideoIdsOverridesSearchOnlyForKnownIds() {
        var searches = 0
        val creator = creator { searches++; "searched_$it" }
        val source = listOf(track("One", remoteTrackId = "knownid1"), track("Two", remoteTrackId = "spotify_id_2"))
        val result = creator.build(
            "T", null, source, "youtube_123",
            resolvedVideoIds = mapOf("knownid1" to "from_map")
        )
        assertEquals(listOf("from_map", "searched_Two Artist"), result.tracks.map { it.youtubeVideoId })
        assertEquals(1, searches)
    }

    @Test
    fun build_mixesResolvedAndUnresolvedSources() {
        val creator = creator { "video_$it" }
        val source = listOf(
            track("One", youtubeVideoId = "resolved"),
            track("Two")
        )
        val result = creator.build("T", null, source, "youtube_123")
        assertEquals(listOf("resolved", "video_Two Artist"), result.tracks.map { it.youtubeVideoId })
    }

    @Test
    fun build_dropsTracksWithoutMatchAndReindexes() {
        val creator = creator { query -> if (query.startsWith("Missing")) null else "video_$query" }
        val source = listOf(track("One"), track("Missing"), track("Two"))
        val result = creator.build("T", null, source, "youtube_123")
        assertEquals(listOf("One", "Two"), result.tracks.map { it.name })
        assertEquals(listOf(0, 1), result.tracks.map { it.position })
    }

    @Test
    fun build_emptySourceProducesEmptyTracks() {
        val result = creator().build("T", "d", emptyList(), "youtube_123")
        assertEquals(0, result.tracks.size)
    }

    @Test
    fun build_preservesNameArtistsAndAppTrackId() {
        val source = track("One", "Artist A, Artist B", remoteTrackId = "spotify_1")
        val result = creator().build("T", null, listOf(source), "youtube_123")
        val out = result.tracks.single()
        assertEquals("One", out.name)
        assertEquals("Artist A, Artist B", out.artists)
        assertEquals("spotify_1", out.remoteTrackId)
    }

    @Test
    fun buildSourceTracks_mapsNameArtistsAndSpotifyId() {
        val selected = listOf(
            AppTrack("sp1", "One", listOf(AppArtist("Artist A"), AppArtist("Artist B"))),
            AppTrack("sp2", "Two", listOf(AppArtist("Solo")))
        )
        val source = YouTubePlaylistCreator().buildSourceTracks(selected)
        assertEquals(2, source.size)
        assertEquals("One", source[0].name)
        assertEquals("Artist A, Artist B", source[0].artists)
        assertEquals("sp1", source[0].remoteTrackId)
        assertEquals("Two", source[1].name)
        assertEquals("Solo", source[1].artists)
        assertEquals("sp2", source[1].remoteTrackId)
    }

    @Test
    fun buildSourceTracks_reindexesPositions() {
        val selected = listOf(AppTrack("sp1", "One", emptyList()), AppTrack("sp2", "Two", emptyList()))
        val source = YouTubePlaylistCreator().buildSourceTracks(selected)
        assertEquals(listOf(0, 1), source.map { it.position })
        assertEquals(listOf("source_sp1_0", "source_sp2_1"), source.map { it.id })
    }

    @Test
    fun buildSourceTracks_emptyListProducesEmptyTracks() {
        assertEquals(0, YouTubePlaylistCreator().buildSourceTracks(emptyList()).size)
    }

    @Test
    fun buildFromAppTracks_resolvesSamePlaylistEndToEnd() {
        val creator = creator { "video_$it" }
        val selected = listOf(
            AppTrack("sp1", "One", listOf(AppArtist("A"))),
            AppTrack("sp2", "Two", listOf(AppArtist("B")))
        )
        val created = creator.build(
            title = "Mi Playlist",
            description = "La descripción",
            sourceTracks = creator.buildSourceTracks(selected),
            targetPlaylistId = "youtube_yt_1"
        )
        assertEquals("Mi Playlist", created.title)
        assertEquals("La descripción", created.description)
        assertEquals(2, created.tracks.size)
        assertEquals(listOf("sp1", "sp2"), created.tracks.map { it.remoteTrackId })
        assertEquals(listOf("One", "Two"), created.tracks.map { it.name })
        assertEquals(listOf("youtube_yt_1", "youtube_yt_1"), created.tracks.map { it.playlistId })
    }
}
