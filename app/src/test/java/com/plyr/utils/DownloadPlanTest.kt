package com.plyr.utils

import com.plyr.database.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de `DownloadPlan.pending`: qué pistas de una lista hay que descargar.
 * Es la lógica donde se cuelan los "ya la tenía" y los duplicados por video.
 */
class DownloadPlanTest {

    private fun track(id: String, videoId: String?) = TrackEntity(
        id = id,
        playlistId = "pl",
        remoteTrackId = "remote_$id",
        name = "track $id",
        artists = "artist",
        youtubeVideoId = videoId,
        position = 0
    )

    @Test
    fun tracksWithoutVideoIdAreExcluded() {
        val result = DownloadPlan.pending(
            listOf(track("a", null), track("b", "vid_b")),
            isDownloaded = { false }
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun alreadyDownloadedAreSkipped() {
        val downloaded = setOf("vid_a")
        val result = DownloadPlan.pending(
            listOf(track("a", "vid_a"), track("b", "vid_b")),
            isDownloaded = { it in downloaded }
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun duplicateVideosKeepOnlyTheFirst() {
        val result = DownloadPlan.pending(
            listOf(track("a", "vid_x"), track("b", "vid_y"), track("c", "vid_x")),
            isDownloaded = { false }
        )
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun blankVideoIdIsExcluded() {
        val result = DownloadPlan.pending(
            listOf(track("a", ""), track("b", " "), track("c", "vid_c")),
            isDownloaded = { false }
        )
        assertEquals(listOf("c"), result.map { it.id })
    }

    @Test
    fun orderIsPreserved() {
        val result = DownloadPlan.pending(
            listOf(track("a", "1"), track("b", "2"), track("c", "3")),
            isDownloaded = { false }
        )
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }

    @Test
    fun emptyListYieldsEmpty() {
        assertEquals(emptyList<TrackEntity>(), DownloadPlan.pending(emptyList(), isDownloaded = { false }))
    }

    @Test
    fun everythingAlreadyDownloadedYieldsEmpty() {
        val result = DownloadPlan.pending(
            listOf(track("a", "1"), track("b", "2")),
            isDownloaded = { true }
        )
        assertEquals(emptyList<TrackEntity>(), result)
    }
}