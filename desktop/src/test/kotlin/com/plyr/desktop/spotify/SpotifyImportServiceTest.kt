package com.plyr.desktop.spotify

import com.plyr.database.PlaylistSource
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.database.DesktopDatabase
import com.plyr.desktop.database.InMemoryTombstoneStore
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.utils.SpotifyImport
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Importación de Spotify de escritorio (M11): busca las pistas con un doble,
 * traduce el plan a los modelos de SQLDelight y evita duplicados. Sin red ni
 * `Context`, igual que el servicio.
 */
class SpotifyImportServiceTest {

    private lateinit var database: DesktopDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        database = DesktopDatabase.inMemory()
        repository = PlaylistRepository(database, InMemoryTombstoneStore())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val playlist = SpotifyImport.SpotifyPlaylist(
        id = "37i9dQZF1DXcBWIGoYBM5M",
        name = "Mi Lista",
        imageUrl = "https://img/cover.jpg",
        tracks = listOf(
            SpotifyImport.SpotifyTrack("Uno", listOf("A", "B"), 1000),
            SpotifyImport.SpotifyTrack("Dos", listOf("C"), 2000),
        ),
    )

    private fun service(
        source: SpotifyImport.SpotifyPlaylist = playlist,
        search: suspend (String) -> String? = { "vid" },
    ) = SpotifyImportService(
        repository = repository,
        fetchPlaylist = { Result.success(source) },
        searchTrack = search,
    )

    @Test
    fun importarCreaLaListaConSusPistas() = runBlocking {
        val result = service(search = { "vid-${it.first()}" })
            .import("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().skipped)

        val record = repository.playlistById("youtube_37i9dQZF1DXcBWIGoYBM5M")
        assertNotNull(record)
        assertEquals("Mi Lista", record!!.name)
        assertEquals("https://img/cover.jpg", record.imageUrl)
        assertEquals(PlaylistSource.SPOTIFY, record.source)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", record.sourceId)

        val tracks = repository.tracksByPlaylist("youtube_37i9dQZF1DXcBWIGoYBM5M")
        assertEquals(2, tracks.size)
        assertEquals("Uno", tracks[0].name)
        assertEquals("A, B", tracks[0].artists)
        assertEquals("vid-U", tracks[0].youtubeVideoId)
        assertEquals(0, tracks[0].position)
    }

    @Test
    fun unaPistaSinCoincidenciaSeGuardaSinVideo() = runBlocking {
        val result = service(search = { null })
            .import("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        assertTrue(result.isSuccess)
        val tracks = repository.tracksByPlaylist("youtube_37i9dQZF1DXcBWIGoYBM5M")
        assertEquals(2, tracks.size)
        assertEquals(null, tracks[0].youtubeVideoId)
    }

    @Test
    fun importarDosVecesSeSaltaLaSegunda() = runBlocking {
        service().import("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M")

        val second = service().import("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")

        assertTrue(second.isSuccess)
        assertTrue(second.getOrThrow().skipped)
        assertEquals(1, repository.allPlaylists().count { it.remoteId == "youtube_37i9dQZF1DXcBWIGoYBM5M" })
    }

    @Test
    fun urlInvalidaFallaSinTocarLaBase() = runBlocking {
        val result = service().import("no es una url")

        assertTrue(result.isFailure)
        assertTrue(repository.allPlaylists().isEmpty())
    }

    @Test
    fun errorDeDescargaSePropaga() = runBlocking {
        val failing = SpotifyImportService(
            repository = repository,
            fetchPlaylist = { Result.failure(RuntimeException("sin red")) },
            searchTrack = { null },
        )

        val result = failing.import("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")

        assertTrue(result.isFailure)
        assertTrue(repository.allPlaylists().isEmpty())
    }
}
