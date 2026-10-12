package com.plyr.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests del filtro/orden de listas visibles, ahora en `:core` para que Android y
 * escritorio compartan exactamente el mismo criterio (antes vivía en
 * `PlaylistLocalRepository` y solo lo probaba `:app`).
 */
class PlaylistFiltersTest {

    private fun playlist(id: String, name: String = id, trackCount: Int = 0) = PlaylistRecord(
        remoteId = id,
        name = name,
        description = null,
        trackCount = trackCount,
        imageUrl = null,
        lastSyncTime = 0,
        source = PlaylistSource.UNKNOWN,
        sourceId = null,
    )

    @Test
    fun lasFilasAlbumNoSonListasDelUsuario() {
        val visible = PlaylistFilters.visiblePlaylists(
            listOf(playlist("album_123"), playlist("mine", name = "Mine")),
        )

        assertEquals(listOf("mine"), visible.map { it.remoteId })
    }

    @Test
    fun unaLikedVaciaNoSeMuestra() {
        val visible = PlaylistFilters.visiblePlaylists(
            listOf(playlist(LikedSongs.ID, name = "liked", trackCount = 0)),
        )

        assertEquals(emptyList<String>(), visible.map { it.remoteId })
    }

    @Test
    fun unaLikedConPistasSeMuestraLaPrimera() {
        val visible = PlaylistFilters.visiblePlaylists(
            listOf(
                playlist("rock", name = "Rock"),
                playlist(LikedSongs.ID, name = "liked", trackCount = 3),
            ),
        )

        assertEquals(listOf(LikedSongs.ID, "rock"), visible.map { it.remoteId })
    }

    @Test
    fun elRestoSeOrdenaPorNombreYLaLikedVaPrimero() {
        val visible = PlaylistFilters.visiblePlaylists(
            listOf(
                playlist("z", name = "Zeta"),
                playlist(LikedSongs.ID, name = "liked", trackCount = 1),
                playlist("a", name = "Alfa"),
            ),
        )

        assertEquals(listOf(LikedSongs.ID, "a", "z"), visible.map { it.remoteId })
    }

    @Test
    fun unaListaVaciaNoDaNada() {
        assertEquals(emptyList<PlaylistRow>(), PlaylistFilters.visiblePlaylists(emptyList()))
    }
}
