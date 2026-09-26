package com.plyr.utils

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del formato de exportación (`ExportManifest`).
 *
 * La parte pura es la que se exporta a disco, así que aquí se comprueba que el
 * `playlists.json` generado sea JSON válido, con la forma esperada, y que los
 * nombres de entrada del ZIP no puedan escapar de su carpeta.
 */
class ExportManifestTest {

    // --- MANIFIESTO ---

    @Test
    fun build_producesValidJsonWithTopLevelMetadata() {
        val json = JSONObject(
            ExportManifest.build(
                appVersion = "1.1.0",
                exportedAt = 1_700_000_000_000L,
                playlists = listOf(samplePlaylist())
            )
        )

        assertEquals("_plyr", json.getString("app"))
        assertEquals(ExportManifest.FORMAT_VERSION, json.getInt("formatVersion"))
        assertEquals("1.1.0", json.getString("appVersion"))
        assertEquals(1_700_000_000_000L, json.getLong("exportedAt"))
        assertEquals(1, json.getInt("playlistCount"))
        assertEquals(2, json.getInt("trackCount"))
    }

    @Test
    fun build_withNoPlaylists_hasEmptyArrayNotNull() {
        val json = JSONObject(
            ExportManifest.build(appVersion = "1.0.0", exportedAt = 0L, playlists = emptyList())
        )

        assertEquals(0, json.getInt("playlistCount"))
        assertEquals(0, json.getInt("trackCount"))
        assertEquals(0, json.getJSONArray("playlists").length())
    }

    @Test
    fun build_mapsEveryPlaylistField() {
        val json = JSONObject(ExportManifest.build("1.1.0", 0L, listOf(samplePlaylist())))
        val playlist = json.getJSONArray("playlists").getJSONObject(0)

        assertEquals("liked_songs", playlist.getString("id"))
        assertEquals("liked", playlist.getString("name"))
        assertEquals("YouTube Playlist by someone", playlist.getString("description"))
        assertEquals("covers/liked_songs.jpg", playlist.getString("cover"))
        assertEquals(2, playlist.getInt("trackCount"))
    }

    @Test
    fun build_playlistWithoutDescriptionOrCover_writesJsonNull() {
        val json = JSONObject(
            ExportManifest.build(
                appVersion = "1.0.0",
                exportedAt = 0L,
                playlists = listOf(samplePlaylist().copy(description = null, coverEntry = null))
            )
        )
        val playlist = json.getJSONArray("playlists").getJSONObject(0)

        assertTrue(playlist.isNull("description"))
        assertTrue(playlist.isNull("cover"))
    }

    @Test
    fun build_keepsTrackOrderPositionAndArtists() {
        val json = JSONObject(ExportManifest.build("1.0.0", 0L, listOf(samplePlaylist())))
        val tracks = json.getJSONArray("playlists").getJSONObject(0).getJSONArray("tracks")

        assertEquals(2, tracks.length())

        val first = tracks.getJSONObject(0)
        assertEquals(0, first.getInt("position"))
        assertEquals("Song One", first.getString("name"))
        assertEquals(2, first.getJSONArray("artists").length())
        assertEquals("Artist A", first.getJSONArray("artists").getString(0))
        assertEquals("Artist B", first.getJSONArray("artists").getString(1))
        assertEquals("remote-1", first.getString("remoteTrackId"))
        assertEquals("yt-1", first.getString("youtubeVideoId"))

        assertEquals(1, tracks.getJSONObject(1).getInt("position"))
    }

    @Test
    fun build_trackWithoutYoutubeId_writesJsonNull() {
        val json = JSONObject(
            ExportManifest.build(
                "1.0.0", 0L,
                listOf(
                    samplePlaylist().copy(
                        tracks = listOf(
                            ExportTrack(0, "Song", emptyList(), "remote-1", null)
                        )
                    )
                )
            )
        )

        assertTrue(
            json.getJSONArray("playlists").getJSONObject(0)
                .getJSONArray("tracks").getJSONObject(0)
                .isNull("youtubeVideoId")
        )
    }

    @Test
    fun build_separatesMultiplePlaylists() {
        val json = JSONObject(
            ExportManifest.build(
                "1.0.0", 0L,
                listOf(
                    samplePlaylist(),
                    samplePlaylist().copy(id = "youtube_PL2", name = "Second", tracks = emptyList())
                )
            )
        )

        val playlists = json.getJSONArray("playlists")
        assertEquals(2, playlists.length())
        assertEquals("youtube_PL2", playlists.getJSONObject(1).getString("id"))
        assertEquals(0, playlists.getJSONObject(1).getInt("trackCount"))
    }

    @Test
    fun build_isStableForTheSameData() {
        val playlists = listOf(samplePlaylist())
        assertEquals(
            ExportManifest.build("1.0.0", 123L, playlists),
            ExportManifest.build("1.0.0", 123L, playlists)
        )
    }

    /**
     * Snapshot del formato. Si esto falla, el archivo exportado ha cambiado de
     * forma incompatible para quien lo consume: o se actualiza el snapshot a
     * mano, o se sube [ExportManifest.FORMAT_VERSION].
     */
    @Test
    fun build_matchesDocumentedFormat() {
        val actual = ExportManifest.build(
            appVersion = "1.1.0",
            exportedAt = 1_735_689_600_000L,
            playlists = listOf(
                samplePlaylist().copy(
                    id = "liked_songs",
                    name = "liked",
                    description = null,
                    coverEntry = null,
                    tracks = listOf(
                        ExportTrack(0, "Song \"One\"", listOf("A", "B"), "r1", "yt1"),
                        ExportTrack(1, "Song Two", emptyList(), "r2", null)
                    )
                ),
                ExportPlaylist(
                    id = "youtube_PL1",
                    name = "Rock",
                    description = "YouTube Playlist by uploader",
                    coverEntry = "covers/youtube_PL1.jpg",
                    tracks = emptyList()
                )
            )
        )

        val expected = """
            {
              "app": "_plyr",
              "formatVersion": 1,
              "appVersion": "1.1.0",
              "exportedAt": 1735689600000,
              "playlistCount": 2,
              "trackCount": 2,
              "playlists": [
                {
                  "id": "liked_songs",
                  "name": "liked",
                  "description": null,
                  "cover": null,
                  "trackCount": 2,
                  "tracks": [
                    {
                      "position": 0,
                      "name": "Song \"One\"",
                      "artists": ["A", "B"],
                      "remoteTrackId": "r1",
                      "youtubeVideoId": "yt1"
                    },
                    {
                      "position": 1,
                      "name": "Song Two",
                      "artists": [],
                      "remoteTrackId": "r2",
                      "youtubeVideoId": null
                    }
                  ]
                },
                {
                  "id": "youtube_PL1",
                  "name": "Rock",
                  "description": "YouTube Playlist by uploader",
                  "cover": "covers/youtube_PL1.jpg",
                  "trackCount": 0,
                  "tracks": []
                }
              ]
            }
        """.trimIndent()

        // El manifiesto cierra con salto de línea, como cualquier fichero de texto
        assertEquals(expected + "\n", actual)
    }

    // --- ESCAPADO ---

    @Test
    fun escape_handlesQuotesBackslashesAndWhitespace() {
        assertEquals("say \\\"hi\\\"", ExportManifest.escape("say \"hi\""))
        assertEquals("C:\\\\path", ExportManifest.escape("C:\\path"))
        assertEquals("a\\nb\\rc\\td", ExportManifest.escape("a\nb\rc\td"))
        assertEquals("\\b\\f", ExportManifest.escape("\b\u000C"))
    }

    @Test
    fun escape_handlesOtherControlCharsAsUnicode() {
        assertEquals("\\u0001", ExportManifest.escape("\u0001"))
    }

    @Test
    fun escape_keepsAccentsEmojisAndAscii() {
        assertEquals("a la carta 🎵 ~_", ExportManifest.escape("a la carta 🎵 ~_"))
    }

    @Test
    fun build_escapesUserSuppliedText() {
        val hostile = listOf(
            samplePlaylist().copy(name = "Rock \"n\" Roll\\n", description = "linea1\nlinea2")
        )
        val playlist = JSONObject(ExportManifest.build("1.0.0", 0L, hostile))
            .getJSONArray("playlists").getJSONObject(0)

        assertEquals("Rock \"n\" Roll\\n", playlist.getString("name"))
        assertEquals("linea1\nlinea2", playlist.getString("description"))
    }

    @Test
    fun jsonString_wrapsInQuotesAndMapsNull() {
        assertEquals("\"hola\"", ExportManifest.jsonString("hola"))
        assertEquals("null", ExportManifest.jsonString(null))
    }

    // --- NOMBRES DE ENTRADA DEL ZIP ---

    @Test
    fun coverEntry_usesPlaylistIdUnderCoversDir() {
        assertEquals("covers/liked_songs.jpg", ExportManifest.coverEntry("liked_songs"))
        assertEquals("covers/youtube_PLabc-123.jpg", ExportManifest.coverEntry("youtube_PLabc-123"))
    }

    @Test
    fun sanitizeEntryName_stripsPathSeparators() {
        assertEquals(".._.._etc_passwd", ExportManifest.sanitizeEntryName("../../etc/passwd"))
        assertEquals("a_b", ExportManifest.sanitizeEntryName("a/b"))
        assertFalse(ExportManifest.sanitizeEntryName("a\\b").contains("\\"))
    }

    @Test
    fun sanitizeEntryName_fallsBackWhenNothingSafeRemains() {
        assertEquals("playlist", ExportManifest.sanitizeEntryName(""))
        assertEquals("playlist", ExportManifest.sanitizeEntryName(".."))
        assertEquals("playlist", ExportManifest.sanitizeEntryName("."))
    }

    @Test
    fun uniqueCoverEntry_avoidsDuplicateZipEntries() {
        val taken = mutableSetOf<String>()

        // "a/b" y "a_b" se sanean igual, pero deben acabar en entradas distintas
        assertEquals("covers/a_b.jpg", ExportManifest.uniqueCoverEntry("a/b", taken))
        assertEquals("covers/a_b-2.jpg", ExportManifest.uniqueCoverEntry("a_b", taken))
        assertEquals("covers/a_b-3.jpg", ExportManifest.uniqueCoverEntry("a/b", taken))
        assertEquals("covers/other.jpg", ExportManifest.uniqueCoverEntry("other", taken))

        assertEquals(4, taken.size)
    }

    @Test
    fun uniqueCoverEntry_withEmptySet_behavesLikeCoverEntry() {
        assertEquals(
            ExportManifest.coverEntry("liked_songs"),
            ExportManifest.uniqueCoverEntry("liked_songs", mutableSetOf())
        )
    }

    @Test
    fun sanitizeEntryName_keepsSafeCharactersAndTruncates() {
        assertEquals("Rock-90s_mix.1", ExportManifest.sanitizeEntryName("Rock-90s_mix.1"))
        assertEquals(64, ExportManifest.sanitizeEntryName("a".repeat(200)).length)
    }

    @Test
    fun suggestedFileName_hasDateAndZipExtension() {
        val name = ExportManifest.suggestedFileName(1_700_000_000_000L)

        assertTrue(name, name.startsWith("plyr-export-"))
        assertTrue(name, name.endsWith(".zip"))
        assertTrue(name, Regex("\\Aplyr-export-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.zip\\z").matches(name))
    }

    // --- ARTISTAS ---

    @Test
    fun parseArtists_splitsCommaJoinedString() {
        assertEquals(listOf("A", "B", "C"), ExportManifest.parseArtists("A, B, C"))
        assertEquals(listOf("Solo"), ExportManifest.parseArtists("Solo"))
        assertEquals(listOf("A", "B"), ExportManifest.parseArtists("A , B"))
        assertEquals(emptyList<String>(), ExportManifest.parseArtists(""))
        assertEquals(emptyList<String>(), ExportManifest.parseArtists(" , "))
    }

    // --- HELPERS ---

    private fun samplePlaylist() = ExportPlaylist(
        id = "liked_songs",
        name = "liked",
        description = "YouTube Playlist by someone",
        coverEntry = "covers/liked_songs.jpg",
        tracks = listOf(
            ExportTrack(
                position = 0,
                name = "Song One",
                artists = listOf("Artist A", "Artist B"),
                remoteTrackId = "remote-1",
                youtubeVideoId = "yt-1"
            ),
            ExportTrack(
                position = 1,
                name = "Song Two",
                artists = emptyList(),
                remoteTrackId = "remote-2",
                youtubeVideoId = null
            )
        )
    )
}
