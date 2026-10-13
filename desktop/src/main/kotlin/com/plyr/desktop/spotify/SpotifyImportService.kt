package com.plyr.desktop.spotify

import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackRecord
import com.plyr.desktop.database.PlaylistRepository
import com.plyr.network.YouTubeManager
import com.plyr.utils.SpotifyImport

/**
 * Importa una lista de Spotify al escritorio (M11).
 *
 * A diferencia del `SpotifyImporter` de Android, no conoce `Context` ni un
 * repositorio concreto: recibe la descarga de la lista, la búsqueda en YouTube y
 * el guardado como dependencias. Los valores por defecto usan las piezas reales
 * ([SpotifyImport], [YouTubeManager], [PlaylistRepository]) y los tests pasan
 * dobles en memoria.
 *
 * El parseo y el plan de importación viven en `:core`; aquí solo se traduce ese
 * plan a los modelos de SQLDelight y se guarda.
 */
class SpotifyImportService(
    private val repository: PlaylistRepository,
    private val fetchPlaylist: suspend (String) -> Result<SpotifyImport.SpotifyPlaylist> =
        { SpotifyImport.fetchPlaylist(it) },
    private val searchTrack: suspend (String) -> String? = { YouTubeManager.searchVideoId(it) },
) {

    suspend fun import(
        playlistUri: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    ): Result<SpotifyImport.ImportResult> {
        val playlistId = SpotifyImport.extractPlaylistId(playlistUri)
            ?: return Result.failure(IllegalArgumentException("Invalid Spotify playlist URL"))

        val playlist = fetchPlaylist(playlistId).getOrElse { return Result.failure(it) }
        val dbPlaylistId = SpotifyImport.dbPlaylistId(playlistId)

        if (isAlreadyImported(dbPlaylistId, playlist)) {
            return Result.success(
                SpotifyImport.ImportResult(
                    success = true,
                    message = SpotifyImport.alreadyImportedMessage(playlist.name),
                    skipped = true,
                )
            )
        }

        val plan = SpotifyImport.buildPlan(playlist, searchTrack, onProgress)
        repository.savePlaylist(plan.toPlaylistRecord(), plan.tracks.map { it.toTrackRecord(dbPlaylistId) })

        return Result.success(SpotifyImport.ImportResult(success = true, message = plan.message))
    }

    private fun isAlreadyImported(
        dbPlaylistId: String,
        playlist: SpotifyImport.SpotifyPlaylist,
    ): Boolean {
        if (repository.playlistById(dbPlaylistId) != null && contentMatches(dbPlaylistId, playlist.tracks)) {
            return true
        }
        val sameName = repository.allPlaylists().find {
            it.name.equals(playlist.name, ignoreCase = true) && it.remoteId != dbPlaylistId
        } ?: return false
        return contentMatches(sameName.remoteId, playlist.tracks)
    }

    private fun contentMatches(
        playlistId: String,
        tracks: List<SpotifyImport.SpotifyTrack>,
    ): Boolean {
        val existing = repository.tracksByPlaylist(playlistId)
        if (existing.size != tracks.size) return false
        return existing.take(5).mapIndexed { i, t ->
            t.name.equals(tracks.getOrNull(i)?.name, ignoreCase = true)
        }.all { it }
    }

    private fun SpotifyImport.ImportPlan.toPlaylistRecord(): PlaylistRecord = PlaylistRecord(
        remoteId = dbPlaylistId,
        name = name,
        description = description,
        trackCount = tracks.size,
        imageUrl = imageUrl,
        lastSyncTime = System.currentTimeMillis(),
        source = PlaylistSource.SPOTIFY,
        sourceId = spotifyId,
    )

    private fun SpotifyImport.MatchedTrack.toTrackRecord(dbPlaylistId: String): TrackRecord = TrackRecord(
        id = "${dbPlaylistId}_$position",
        playlistId = dbPlaylistId,
        remoteTrackId = remoteTrackId,
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = null,
        position = position,
        lastSyncTime = System.currentTimeMillis(),
    )
}
