package com.plyr.utils

import android.content.Context
import android.util.Log
import com.plyr.database.CreatedPlaylist
import com.plyr.database.PlaylistDatabase
import com.plyr.database.PlaylistLocalRepository
import com.plyr.database.PlaylistSource
import com.plyr.database.TrackEntity
import com.plyr.service.YouTubeSearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptador Android de la importación de listas de Spotify.
 *
 * La lógica reutilizable (parseo, descarga y plan de importación) vive en
 * `:core` ([SpotifyImport], M11); aquí solo queda lo que sí es de Android:
 * buscar cada pista con [YouTubeSearchManager], comprobar si la lista ya está
 * en Room y guardarla con `PlaylistLocalRepository`.
 */
object SpotifyImporter {

    private const val TAG = "SpotifyImporter"

    fun extractPlaylistId(input: String): String? = SpotifyImport.extractPlaylistId(input)

    suspend fun importPlaylistByUri(
        context: Context,
        playlistUri: String,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): Result<SpotifyImport.ImportResult> = withContext(Dispatchers.IO) {
        val playlistId = SpotifyImport.extractPlaylistId(playlistUri)
            ?: return@withContext Result.failure(Exception("Invalid Spotify playlist URL"))

        onProgress(0, 1, "Fetching playlist...")
        val playlist = SpotifyImport.fetchPlaylist(playlistId).getOrElse { e ->
            return@withContext Result.failure(e)
        }

        val dbPlaylistId = SpotifyImport.dbPlaylistId(playlistId)
        val database = PlaylistDatabase.getDatabase(context)

        if (isAlreadyImported(database, dbPlaylistId, playlist)) {
            return@withContext Result.success(
                SpotifyImport.ImportResult(
                    success = true,
                    message = SpotifyImport.alreadyImportedMessage(playlist.name),
                    skipped = true
                )
            )
        }

        val searchManager = YouTubeSearchManager(context)
        val plan = SpotifyImport.buildPlan(
            playlist = playlist,
            searchTrack = { query -> searchTrack(searchManager, query) },
            onProgress = onProgress,
        )

        onProgress(plan.totalCount, plan.totalCount, "Saving...")

        PlaylistLocalRepository(context).playlists.saveCreatedYouTubePlaylist(
            created = CreatedPlaylist(
                playlistId = playlistId,
                title = plan.name,
                description = plan.description,
                imageUrl = plan.imageUrl
            ),
            tracks = plan.tracks.map { it.toEntity(dbPlaylistId) },
            source = PlaylistSource.SPOTIFY
        )

        Log.d(TAG, plan.message)
        Result.success(SpotifyImport.ImportResult(success = true, message = plan.message))
    }

    /** ¿El dispositivo ya tiene esta playlist (por id o por mismo nombre y contenido)? */
    private suspend fun isAlreadyImported(
        database: PlaylistDatabase,
        dbPlaylistId: String,
        playlist: SpotifyImport.SpotifyPlaylist
    ): Boolean {
        if (database.playlistDao().getPlaylistById(dbPlaylistId) != null &&
            contentMatches(database, dbPlaylistId, playlist.tracks)
        ) {
            return true
        }
        val sameName = database.playlistDao().getAllPlaylistsSync().find {
            it.name.equals(playlist.name, ignoreCase = true) && it.remoteId != dbPlaylistId
        } ?: return false
        return contentMatches(database, sameName.remoteId, playlist.tracks)
    }

    private suspend fun contentMatches(
        database: PlaylistDatabase,
        playlistId: String,
        tracks: List<SpotifyImport.SpotifyTrack>
    ): Boolean {
        val existing = database.trackDao().getTracksByPlaylistSync(playlistId)
        if (existing.size != tracks.size) return false
        return existing.take(5).mapIndexed { i, t ->
            t.name.equals(tracks.getOrNull(i)?.name, ignoreCase = true)
        }.all { it }
    }

    private suspend fun searchTrack(searchManager: YouTubeSearchManager, query: String): String? =
        try {
            searchManager.searchSingleVideoId(query)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching YouTube for: $query", e)
            null
        }

    private fun SpotifyImport.MatchedTrack.toEntity(dbPlaylistId: String): TrackEntity = TrackEntity(
        id = "${dbPlaylistId}_$position",
        playlistId = dbPlaylistId,
        remoteTrackId = remoteTrackId,
        name = name,
        artists = artists,
        youtubeVideoId = youtubeVideoId,
        audioUrl = null,
        position = position
    )
}
