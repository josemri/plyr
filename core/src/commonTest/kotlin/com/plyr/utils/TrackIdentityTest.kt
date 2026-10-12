package com.plyr.utils

import com.plyr.database.TrackRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests de la identidad de pistas compartida con el escritorio.
 *
 * Es la que usan marcar/desmarcar favoritos y la fusión, así que fija el criterio
 * que evita resucitar y duplicar (B1/B13/B51).
 */
class TrackIdentityTest {

    private fun track(
        name: String,
        artists: String,
        videoId: String? = null,
        id: String = name,
    ) = TrackRecord(
        id = id,
        playlistId = "liked_songs",
        remoteTrackId = "r_$id",
        name = name,
        artists = artists,
        youtubeVideoId = videoId,
        audioUrl = null,
        position = 0,
        lastSyncTime = 0,
    )

    @Test
    fun fallbackDedupeKey_uneNombreYArtistas() {
        assertEquals("Song|Artist", TrackIdentity.fallbackDedupeKey(track("Song", "Artist")))
        assertEquals("Song|Artist", TrackIdentity.fallbackDedupeKey("Song", "Artist"))
    }

    @Test
    fun likedTrackKey_prefiereElVideoId() {
        assertEquals("vidA", TrackIdentity.likedTrackKey("vidA", "Song", "Artist"))
        assertEquals("vidA", TrackIdentity.likedTrackKey(track("Song", "Artist", videoId = "vidA")))
    }

    @Test
    fun likedTrackKey_sinVideoId_caeANombreYArtistas() {
        assertEquals("Song|Artist", TrackIdentity.likedTrackKey(null, "Song", "Artist"))
        assertEquals("Song|Artist", TrackIdentity.likedTrackKey("", "Song", "Artist"))
        assertEquals("Song|Artist", TrackIdentity.likedTrackKey("   ", "Song", "Artist"))
    }

    @Test
    fun likedTrackOf_encuentraPorVideoId() {
        val a = track("Song A", "Artist A", videoId = "vidA")
        val b = track("Song B", "Artist B", videoId = "vidB", id = "b")

        val found = TrackIdentity.likedTrackOf(listOf(a, b), "Song B", "Artist B", "remoteB", "vidB")

        assertSame(b, found)
    }

    @Test
    fun likedTrackOf_sinId_caeANombreYArtistas() {
        val legacy = track("Legacy", "Artist", videoId = null, id = "legacy")

        val found = TrackIdentity.likedTrackOf(listOf(legacy), "Legacy", "Artist", "remote", "")

        assertSame(legacy, found)
    }

    @Test
    fun likedTrackOf_idVacio_noCasaConOtraCancion() {
        val other = track("Other", "Another", videoId = null)

        val found = TrackIdentity.likedTrackOf(listOf(other), "Song", "Artist", "remote", "")

        assertNull(found)
    }

    @Test
    fun likedTrackOf_elVideoIdMandaSobreElMismoNombre() {
        val byVideo = track("Same", "Artist", videoId = "vidA", id = "byVideo")
        val byName = track("Same", "Artist", videoId = "vidOther", id = "byName")

        val found = TrackIdentity.likedTrackOf(listOf(byName, byVideo), "Same", "Artist", "r", "vidA")

        assertSame(byVideo, found)
    }

    @Test
    fun likedTrackOf_listaVaciaNull() {
        assertNull(TrackIdentity.likedTrackOf(emptyList(), "Song", "Artist", "r", "vidA"))
    }
}
