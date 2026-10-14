package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de `DownloadPlan.pending`: qué pistas de una lista hay que descargar.
 * Es la lógica donde se cuelan los "ya la tenía" y los duplicados por vídeo.
 *
 * Portado de `:app` al extraer el plan a `:core` (M16): ahora la función es
 * genérica, así que el tipo de pista es cualquiera con un id de vídeo.
 */
class DownloadPlanTest {

    private data class Track(val id: String, val videoId: String?)

    private val videoIdOf: (Track) -> String? = { it.videoId }

    @Test
    fun tracksWithoutVideoIdAreExcluded() {
        val result = DownloadPlan.pending(
            listOf(Track("a", null), Track("b", "vid_b")),
            videoIdOf = videoIdOf,
            isDownloaded = { false },
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun alreadyDownloadedAreSkipped() {
        val downloaded = setOf("vid_a")
        val result = DownloadPlan.pending(
            listOf(Track("a", "vid_a"), Track("b", "vid_b")),
            videoIdOf = videoIdOf,
            isDownloaded = { it in downloaded },
        )
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun duplicateVideosKeepOnlyTheFirst() {
        val result = DownloadPlan.pending(
            listOf(Track("a", "vid_x"), Track("b", "vid_y"), Track("c", "vid_x")),
            videoIdOf = videoIdOf,
            isDownloaded = { false },
        )
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun blankVideoIdIsExcluded() {
        val result = DownloadPlan.pending(
            listOf(Track("a", ""), Track("b", " "), Track("c", "vid_c")),
            videoIdOf = videoIdOf,
            isDownloaded = { false },
        )
        assertEquals(listOf("c"), result.map { it.id })
    }

    @Test
    fun orderIsPreserved() {
        val result = DownloadPlan.pending(
            listOf(Track("a", "1"), Track("b", "2"), Track("c", "3")),
            videoIdOf = videoIdOf,
            isDownloaded = { false },
        )
        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }

    @Test
    fun emptyListYieldsEmpty() {
        assertEquals(
            emptyList<Track>(),
            DownloadPlan.pending(emptyList(), videoIdOf = videoIdOf, isDownloaded = { false }),
        )
    }

    @Test
    fun everythingAlreadyDownloadedYieldsEmpty() {
        val result = DownloadPlan.pending(
            listOf(Track("a", "1"), Track("b", "2")),
            videoIdOf = videoIdOf,
            isDownloaded = { true },
        )
        assertEquals(emptyList<Track>(), result)
    }
}
