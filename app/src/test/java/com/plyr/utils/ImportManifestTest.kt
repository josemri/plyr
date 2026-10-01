package com.plyr.utils

import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests de la importación: validación del manifiesto, política de conflictos y
 * conversión a `TrackEntity`.
 *
 * La política es lo que evita que importar un ZIP destruya datos, así que cada
 * rama de [ImportManifest.plan] tiene su propio caso.
 */
class ImportManifestTest {

    // === PARSEO ===

    @Test
    fun parse_readsEveryPlaylistAndTrackField() {
        val playlists = ImportManifest.parse(MANIFEST).playlists

        assertEquals(3, playlists.size)

        val rock = playlists[0]
        assertEquals("youtube_PL1", rock.id)
        assertEquals("Rock", rock.name)
        assertEquals("YouTube Playlist by someone", rock.description)
        assertEquals("covers/youtube_PL1.jpg", rock.coverEntry)
        assertEquals(2, rock.trackCount)

        val first = rock.tracks[0]
        assertEquals(0, first.position)
        assertEquals("Song One", first.name)
        assertEquals("A, B", first.artists)
        assertEquals("remote-1", first.remoteTrackId)
        assertEquals("yt-1", first.youtubeVideoId)

        val second = rock.tracks[1]
        assertEquals(1, second.position)
        assertNull(second.youtubeVideoId)
    }

    @Test
    fun parse_keepsPlaylistOrder() {
        val ids = ImportManifest.parse(MANIFEST).playlists.map { it.id }
        assertEquals(listOf("youtube_PL1", "liked_songs", "youtube_PL2"), ids)
    }

    @Test
    fun parse_readsPlaylistWithoutDescriptionCoverOrTracks() {
        val playlists = ImportManifest.parse(MANIFEST).playlists
        val empty = playlists[2]

        assertNull(empty.description)
        assertNull(empty.coverEntry)
        assertTrue(empty.tracks.isEmpty())
    }

    @Test
    fun parse_trackWithoutRemoteTrackId_fallsBackToVideoIdThenSynthetic() {
        val json = manifestWithTracks(
            """{"position": 0, "name": "Con video", "artists": [], "remoteTrackId": "  ", "youtubeVideoId": "yt-9"}""",
            """{"position": 1, "name": "Sin nada", "artists": [], "remoteTrackId": "", "youtubeVideoId": ""}"""
        )
        val tracks = ImportManifest.parse(json).playlists.single().tracks

        assertEquals("yt-9", tracks[0].remoteTrackId)
        assertEquals("imported_1", tracks[1].remoteTrackId)
    }

    @Test
    fun parse_artistsWithoutValidEntries_becomesEmptyString() {
        val json = manifestWithTracks("""{"name": "S", "artists": ["A", "", "  ", "B"]}""")
        assertEquals("A, B", ImportManifest.parse(json).playlists.single().tracks.single().artists)
    }

    @Test
    fun parse_playlistWithoutTracksArray_givesEmptyList() {
        val json = """{"app":"_plyr","formatVersion":1,"playlists":[{"id":"x","name":"X"}]}"""
        assertTrue(ImportManifest.parse(json).playlists.single().tracks.isEmpty())
    }

    // === VALIDACIÓN DEL FORMATO ===

    @Test
    fun parse_rejectsJsonThatIsNotAnObject() {
        assertFormatException("no es un JSON válido", "[1, 2, 3]")
    }

    @Test
    fun parse_rejectsManifestFromAnotherApp() {
        assertFormatException("no fue generado por", manifest(app = "otra_app"))
    }

    @Test
    fun parse_rejectsUnknownFormatVersion() {
        assertFormatException("no soportada", manifest(formatVersion = 99))
        assertFormatException("no soportada", manifest(formatVersion = 0))
    }

    @Test
    fun parse_rejectsManifestWithoutPlaylists() {
        assertFormatException(
            "no tiene la lista",
            """{"app":"_plyr","formatVersion":1}"""
        )
    }

    // === POLÍTICA ===

    @Test
    fun plan_createsPlaylistsThatDoNotExist() {
        val actions = ImportManifest.plan(listOf(playlist("youtube_PL1")), existingIds = emptySet())

        val action = actions.single()
        assertTrue(action is PlaylistAction.Create)
        assertEquals("youtube_PL1", (action as PlaylistAction.Create).playlist.id)
    }

    @Test
    fun plan_skipsPlaylistsThatAlreadyExist() {
        val actions = ImportManifest.plan(
            listOf(playlist("youtube_PL1")),
            existingIds = setOf("liked_songs", "youtube_PL1")
        )

        val action = actions.single()
        assertTrue(action is PlaylistAction.Skip)
        assertEquals(SkipReason.ALREADY_EXISTS, (action as PlaylistAction.Skip).reason)
    }

    @Test
    fun plan_alwaysMergesLikedSongsEvenThoughItAlwaysExists() {
        val actions = ImportManifest.plan(
            listOf(playlist(PlaylistLocalRepository.LIKED_SONGS_ID)),
            existingIds = setOf(PlaylistLocalRepository.LIKED_SONGS_ID)
        )

        assertEquals(PlaylistAction.MergeLikedSongs, actions.single())
    }

    @Test
    fun plan_skipsLegacyAlbumRows() {
        val actions = ImportManifest.plan(
            listOf(playlist("album_abc"), playlist("album_")),
            existingIds = emptySet()
        )

        actions.forEach { action ->
            assertTrue(action is PlaylistAction.Skip)
            assertEquals(SkipReason.LEGACY_ALBUM, (action as PlaylistAction.Skip).reason)
        }
    }

    @Test
    fun plan_skipsPlaylistsWithoutId() {
        val actions = ImportManifest.plan(listOf(playlist(""), playlist("   ")), existingIds = emptySet())

        actions.forEach { action ->
            assertTrue(action is PlaylistAction.Skip)
            assertEquals(SkipReason.INVALID_ID, (action as PlaylistAction.Skip).reason)
        }
    }

    @Test
    fun plan_coversEveryPlaylistExactlyOnceInOrder() {
        val playlists = listOf(
            playlist("youtube_PL1"),
            playlist("liked_songs"),
            playlist("album_x"),
            playlist("youtube_PL2")
        )
        val actions = ImportManifest.plan(playlists, existingIds = setOf("youtube_PL2"))

        assertEquals(4, actions.size)
        assertTrue(actions[0] is PlaylistAction.Create)
        assertEquals(PlaylistAction.MergeLikedSongs, actions[1])
        assertEquals(SkipReason.LEGACY_ALBUM, (actions[2] as PlaylistAction.Skip).reason)
        assertEquals(SkipReason.ALREADY_EXISTS, (actions[3] as PlaylistAction.Skip).reason)
    }

    @Test
    fun plan_freshInstallSyncBringsBackEveryArchivePlaylist() {
        // Regresión del sync "de cero a cero": tras instalar la app la única
        // lista del dispositivo es liked_songs vacía, así que la política de
        // fusión tiene que crear todas las del archivo (y fusionar los
        // favoritos), sin descartar ninguna. Es lo que evita que sincronizar
        // pise una copia buena con una vacía.
        val actions = ImportManifest.plan(
            playlists = listOf(
                playlist("youtube_PL1"),
                playlist(PlaylistLocalRepository.LIKED_SONGS_ID),
                playlist("youtube_PL2")
            ),
            existingIds = setOf(PlaylistLocalRepository.LIKED_SONGS_ID)
        )

        assertEquals(3, actions.size)
        assertTrue(actions[0] is PlaylistAction.Create)
        assertEquals((actions[0] as PlaylistAction.Create).playlist.id, "youtube_PL1")
        assertEquals(PlaylistAction.MergeLikedSongs, actions[1])
        assertTrue(actions[2] is PlaylistAction.Create)
        assertEquals((actions[2] as PlaylistAction.Create).playlist.id, "youtube_PL2")
    }

    // === TOMBS DE BORRADO ===

    @Test
    fun parse_archiveWithoutDeletedPlaylistIds_isValidAndEmpty() {
        val manifest = ImportManifest.parse(MANIFEST)
        assertTrue(manifest.deletedPlaylistIds.isEmpty())
    }

    @Test
    fun parse_deletedPlaylistIds_readsIdsAndFiltersNoise() {
        val json = """
            {
              "app": "_plyr",
              "formatVersion": 1,
              "deletedPlaylistIds": [" youtube_PL1 ", "", "  ", "liked_songs", "youtube_PL2"],
              "playlists": []
            }
        """.trimIndent()

        val parsed = ImportManifest.parse(json)
        assertEquals(setOf("youtube_PL1", "youtube_PL2"), parsed.deletedPlaylistIds)
    }

    @Test
    fun plan_ignoresPlaylistsMarkedAsDeleted() {
        val actions = ImportManifest.plan(
            listOf(playlist("youtube_PL1")),
            existingIds = emptySet(),
            deletedPlaylistIds = setOf("youtube_PL1")
        )

        val action = actions.single()
        assertTrue(action is PlaylistAction.Skip)
        assertEquals(SkipReason.IGNORED_DELETED, (action as PlaylistAction.Skip).reason)
    }

    @Test
    fun plan_deleteTakesPrecedenceEvenIfPlaylistExists() {
        // El tomb gana aunque la lista siga en el dispositivo: el archivo o el
        // propio usuario la marcó como borrada, y recrearla sería resucitarla.
        val actions = ImportManifest.plan(
            listOf(playlist("youtube_PL1")),
            existingIds = setOf("youtube_PL1"),
            deletedPlaylistIds = setOf("youtube_PL1")
        )

        assertEquals(
            SkipReason.IGNORED_DELETED,
            (actions.single() as PlaylistAction.Skip).reason
        )
    }

    @Test
    fun plan_likedSongsIsNeverTombstoned() {
        val actions = ImportManifest.plan(
            listOf(playlist(PlaylistLocalRepository.LIKED_SONGS_ID)),
            existingIds = setOf(PlaylistLocalRepository.LIKED_SONGS_ID),
            deletedPlaylistIds = setOf(PlaylistLocalRepository.LIKED_SONGS_ID)
        )

        assertEquals(PlaylistAction.MergeLikedSongs, actions.single())
    }

    // === CONVERSIÓN A ENTIDADES ===

    @Test
    fun buildTracks_setsPlaylistIdAndRenumbersPositionsByArrayOrder() {
        val tracks = ImportManifest.buildTracks(
            playlistId = "youtube_PL1",
            tracks = listOf(
                importedTrack(position = 7, name = "A"),
                importedTrack(position = 99, name = "B"),
                importedTrack(position = 0, name = "C")
            ),
            now = 1234L
        )

        assertEquals(listOf(0, 1, 2), tracks.map { it.position })
        assertEquals(listOf("A", "B", "C"), tracks.map { it.name })
        assertTrue(tracks.all { it.playlistId == "youtube_PL1" })
        assertTrue(tracks.all { it.lastSyncTime == 1234L })
        assertTrue(tracks.all { it.audioUrl == null })
    }

    @Test
    fun buildTracks_keepsArtistsVideoIdsAndRemoteIds() {
        val tracks = ImportManifest.buildTracks(
            playlistId = "liked_songs",
            tracks = listOf(importedTrack(remoteTrackId = "r-1", youtubeVideoId = "yt-1"))
        )
        val track = tracks.single()

        assertEquals("A, B", track.artists)
        assertEquals("r-1", track.remoteTrackId)
        assertEquals("yt-1", track.youtubeVideoId)
    }

    @Test
    fun buildTracks_preservesNullYoutubeId() {
        val tracks = ImportManifest.buildTracks(
            playlistId = "youtube_PL1",
            tracks = listOf(importedTrack(youtubeVideoId = null))
        )
        assertNull(tracks.single().youtubeVideoId)
    }

    /**
     * `TrackEntity.id` es la clave primaria global y `PlayerViewModel` la usa para
     * resolver "qué pista suena": si dos pistas comparten id, la navegación y el
     * indicador de reproducción se rompen en silencio.
     */
    @Test
    fun buildTracks_generatesUniqueIdsPerPlaylist() {
        val tracks = ImportManifest.buildTracks(
            playlistId = "youtube_PL1",
            tracks = List(3) { importedTrack(remoteTrackId = "mismo", name = "N$it") }
        )

        assertEquals(3, tracks.map { it.id }.toSet().size)
    }

    @Test
    fun buildTracks_idsOfDifferentPlaylistsDoNotCollide() {
        val a = ImportManifest.buildTracks("youtube_PL1", listOf(importedTrack()))
        val b = ImportManifest.buildTracks("youtube_PL2", listOf(importedTrack()))

        assertTrue(a.intersect(b.toSet()).isEmpty())
    }

    @Test
    fun buildTracks_withNoTracks_givesEmptyList() {
        assertTrue(ImportManifest.buildTracks("youtube_PL1", emptyList()).isEmpty())
    }

    @Test
    fun fallbackDedupeKey_distinguishesTracksWithoutVideoId() {
        val base = importedTrack(name = "Song", artists = "A")
        val same = importedTrack(name = "Song", artists = "A")
        val other = importedTrack(name = "Song", artists = "B")

        assertEquals(
            ImportManifest.fallbackDedupeKey(base.toEntity("liked_songs", 0)),
            ImportManifest.fallbackDedupeKey(same.toEntity("liked_songs", 0))
        )
        assertFalse(
            ImportManifest.fallbackDedupeKey(base.toEntity("liked_songs", 0)) ==
                ImportManifest.fallbackDedupeKey(other.toEntity("liked_songs", 0))
        )
    }

    // === ROUND TRIP ===

    /**
     * Lo que exporta `ExportManifest` tiene que releerse aquí sin pérdida. Es el
     * contrato entre las dos mitades de la funcionalidad.
     */
    @Test
    fun exportedManifest_canBeParsedBack() {
        val original = listOf(
            ExportPlaylist(
                id = "youtube_PL1",
                name = "Rock \"n\" Roll",
                description = "línea1\nlínea2",
                coverEntry = "covers/youtube_PL1.jpg",
                tracks = listOf(
                    ExportTrack(0, "Song \"One\"", listOf("A", "B"), "r1", "yt1"),
                    ExportTrack(1, "Cañón", emptyList(), "r2", null)
                )
            ),
            ExportPlaylist("liked_songs", "liked", null, null, emptyList())
        )
        val json = ExportManifest.build(
            appVersion = "1.1.0",
            exportedAt = 0L,
            playlists = original,
            deletedPlaylistIds = listOf("youtube_PL2", "youtube_PL1")
        )

        val parsed = ImportManifest.parse(json)

        assertEquals(setOf("youtube_PL1", "youtube_PL2"), parsed.deletedPlaylistIds)
        assertEquals(2, parsed.playlists.size)
        assertEquals("youtube_PL1", parsed.playlists[0].id)
        assertEquals("Rock \"n\" Roll", parsed.playlists[0].name)
        assertEquals("línea1\nlínea2", parsed.playlists[0].description)
        assertEquals("covers/youtube_PL1.jpg", parsed.playlists[0].coverEntry)
        assertEquals(2, parsed.playlists[0].tracks.size)
        assertEquals("Song \"One\"", parsed.playlists[0].tracks[0].name)
        assertEquals("A, B", parsed.playlists[0].tracks[0].artists)
        assertEquals("Cañón", parsed.playlists[0].tracks[1].name)
        assertEquals("", parsed.playlists[0].tracks[1].artists)
        assertNull(parsed.playlists[0].tracks[1].youtubeVideoId)
        assertEquals("liked_songs", parsed.playlists[1].id)
        assertNull(parsed.playlists[1].description)
    }

    // === HELPERS ===

    private fun playlist(id: String) = ImportedPlaylist(
        id = id,
        name = "name-$id",
        description = null,
        coverEntry = null,
        tracks = listOf(importedTrack())
    )

    private fun importedTrack(
        position: Int = 0,
        name: String = "Song",
        artists: String = "A, B",
        remoteTrackId: String = "r-1",
        youtubeVideoId: String? = "yt-1"
    ) = ImportedTrack(
        position = position,
        name = name,
        artists = artists,
        remoteTrackId = remoteTrackId,
        youtubeVideoId = youtubeVideoId
    )

    private fun ImportedTrack.toEntity(playlistId: String, position: Int) = TrackEntity(
        id = ImportManifest.synthesizeTrackId(playlistId, position),
        playlistId = playlistId,
        remoteTrackId = remoteTrackId,
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = null,
        position = position
    )

    /** Construye un manifiesto con una sola lista "p" y las pistas dadas. */
    private fun manifestWithTracks(vararg tracks: String): String = manifestOf("p", tracks.toList())

    /** Igual que [manifestWithTracks] pero con el id de lista que se le pase. */
    private fun manifestOf(id: String, tracks: List<String>): String {
        val tracksJson = tracks.joinToString(",\n") { "      $it" }
        return """
            {
              "app": "_plyr",
              "formatVersion": 1,
              "playlists": [
                {
                  "id": "$id",
                  "name": "P",
                  "tracks": [
            $tracksJson
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    /** Manifiesto mínimo y válido, para probar solo el rechazo de formato. */
    private fun manifest(
        app: String = "_plyr",
        formatVersion: Int = 1
    ): String = """
        {
          "app": "$app",
          "formatVersion": $formatVersion,
          "playlists": [
            {"id": "p", "name": "P", "tracks": [{"name": "S", "artists": ["A"], "remoteTrackId": "r"}]}
          ]
        }
    """.trimIndent()

    private fun assertFormatException(expectedInMessage: String, json: String) {
        try {
            ImportManifest.parse(json)
            fail("Se esperaba ManifestFormatException para: $json")
        } catch (e: ManifestFormatException) {
            assertTrue(
                "El mensaje '${e.message}' no contiene '$expectedInMessage'",
                e.message.orEmpty().contains(expectedInMessage)
            )
        }
    }

    private companion object {
        /** Un manifiesto con dos listas con pistas, liked_songs y una vacía. */
        val MANIFEST = """
            {
              "app": "_plyr",
              "formatVersion": 1,
              "appVersion": "1.1.0",
              "exportedAt": 1735689600000,
              "playlistCount": 3,
              "trackCount": 2,
              "playlists": [
                {
                  "id": "youtube_PL1",
                  "name": "Rock",
                  "description": "YouTube Playlist by someone",
                  "cover": "covers/youtube_PL1.jpg",
                  "trackCount": 2,
                  "tracks": [
                    {"position": 0, "name": "Song One", "artists": ["A", "B"],
                     "remoteTrackId": "remote-1", "youtubeVideoId": "yt-1"},
                    {"position": 1, "name": "Song Two", "artists": [],
                     "remoteTrackId": "remote-2", "youtubeVideoId": null}
                  ]
                },
                {
                  "id": "liked_songs",
                  "name": "liked",
                  "description": null,
                  "cover": null,
                  "trackCount": 0,
                  "tracks": []
                },
                {
                  "id": "youtube_PL2",
                  "name": "Empty",
                  "description": null,
                  "cover": null,
                  "trackCount": 0,
                  "tracks": []
                }
              ]
            }
        """.trimIndent()
    }

    // === TOMBS DE FAVORITOS (B51) ===

    @Test
    fun parse_readsRemovedLikedTrackKeys() {
        val json = """
            {
              "app": "_plyr",
              "formatVersion": 1,
              "appVersion": "1.1.0",
              "exportedAt": 0,
              "playlistCount": 0,
              "trackCount": 0,
              "deletedPlaylistIds": [],
              "removedLikedTrackKeys": ["yt-1", "Song Two|"],
              "playlists": []
            }
        """.trimIndent()

        assertEquals(setOf("yt-1", "Song Two|"), ImportManifest.parse(json).removedLikedTrackKeys)
    }

    /**
     * Un archivo exportado antes de este campo tiene que seguir siendo válido: no
     * sincronizar borrados es más conservador que borrar de más.
     */
    @Test
    fun parse_withoutRemovedLikedKeys_givesEmptySet() {
        assertEquals(emptySet<String>(), ImportManifest.parse(MANIFEST).removedLikedTrackKeys)
    }

    @Test
    fun parse_ignoresBlankRemovedLikedKeys() {
        val json = """
            {
              "app": "_plyr",
              "formatVersion": 1,
              "playlistCount": 0,
              "trackCount": 0,
              "removedLikedTrackKeys": ["yt-1", "", "   "],
              "playlists": []
            }
        """.trimIndent()

        assertEquals(setOf("yt-1"), ImportManifest.parse(json).removedLikedTrackKeys)
    }

    /**
     * La clave del tomb tiene que ser **la misma** con la que la fusión descarta
     * duplicados. Si divergieran, el borrado se guardaría con una clave que la
     * fusión nunca consulta y la pista seguiría resucitando.
     */
    @Test
    fun likedTrackKey_usaElVideoIdYSinoElNombreYLosArtistas() {
        assertEquals("yt-1", ImportManifest.likedTrackKey(track(youtubeVideoId = "yt-1")))

        // Sin video id: nombre y artistas, igual que la deduplicación.
        assertEquals(
            ImportManifest.fallbackDedupeKey(track(youtubeVideoId = null)),
            ImportManifest.likedTrackKey(track(youtubeVideoId = null)),
        )

        // Un video id vacío cuenta como ausente, no como clave "".
        assertEquals(
            ImportManifest.fallbackDedupeKey(track(youtubeVideoId = null)),
            ImportManifest.likedTrackKey(track(youtubeVideoId = "")),
        )
    }

    /**
     * Ida y vuelta: lo que escribe [ExportManifest] tiene que ser exactamente lo
     * que lee [ImportManifest]. Sin este test, renombrar el campo en un solo lado
     * no rompe nada visible: el tomb se escribiría y simplemente nunca se leería,
     * que es el bug de B51 volvió a aparecer sin avisar.
     */
    @Test
    fun roundTrip_losTombsDeFavoritosSobrevivenAExportarEImportar() {
        val removed = setOf("yt-9", "Song Sin Video|")

        val json = ExportManifest.build(
            appVersion = "1.1.0",
            exportedAt = 0L,
            playlists = listOf(
                ExportPlaylist(
                    id = "liked_songs",
                    name = "liked",
                    description = null,
                    coverEntry = null,
                    tracks = listOf(ExportTrack(0, "Song One", listOf("A", "B"), "r1", "yt-1")),
                )
            ),
            deletedPlaylistIds = setOf("youtube_PL7"),
            removedLikedTrackKeys = removed,
        )

        val parsed = ImportManifest.parse(json)
        assertEquals(removed, parsed.removedLikedTrackKeys)
        assertEquals(setOf("youtube_PL7"), parsed.deletedPlaylistIds)
        // Y lo demás del archivo sigue leyendo igual que antes.
        assertEquals(listOf("liked_songs"), parsed.playlists.map { it.id })
        assertEquals("yt-1", parsed.playlists.first().tracks.first().youtubeVideoId)
    }

    private fun track(
        name: String = "Song One",
        artists: String = "A, B",
        youtubeVideoId: String? = "yt-1",
    ) = TrackEntity(
        id = "liked_songs_r1_0",
        playlistId = PlaylistLocalRepository.LIKED_SONGS_ID,
        remoteTrackId = "remote-1",
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = null,
        position = 0,
    )
}