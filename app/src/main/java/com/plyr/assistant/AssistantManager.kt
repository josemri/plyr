package com.plyr.assistant

import android.content.Context
import android.util.Log
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.YouTubeManager
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyTrack
import com.plyr.database.TrackEntity
import com.plyr.utils.SpotifyTokenManager
import com.plyr.utils.Translations

import java.util.Collections

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Lightweight on-device assistant manager.
 */
class AssistantManager(private val context: Context) {

    data class IntentResult(
        val intent: String,
        val score: Float = 1.0f,
        val entities: Map<String, String> = emptyMap()
    )

    private var onnxAvailable = false
    private var ortEnv: OrtEnvironment? = null
    private var sessionIntent: OrtSession? = null
    private var sessionNer: OrtSession? = null

    private fun t(key: String) = Translations.get(context, key)

    private fun getTriggers(key: String): List<String> {
        return t(key).split("|").map { it.trim().lowercase() }
    }

    init {
        try {
            Class.forName("ai.onnxruntime.OnnxTensor")
            onnxAvailable = true
        } catch (_: Exception) {
            onnxAvailable = false
        }

        if (onnxAvailable) {
            try {
                ortEnv = OrtEnvironment.getEnvironment()
                val am = context.assets
                fun loadSession(assetName: String): OrtSession? {
                    return try {
                        val bytes = am.open(assetName).use { it.readBytes() }
                        ortEnv?.createSession(bytes)
                    } catch (ex: Exception) {
                        Log.i("AssistantManager", "Model $assetName not found: ${ex.message}")
                        null
                    }
                }
                sessionIntent = loadSession("assistant_intent.onnx")
                sessionNer = loadSession("assistant_ner.onnx")
                if (sessionIntent == null && sessionNer == null) {
                    onnxAvailable = false
                }
            } catch (ex: Throwable) {
                Log.i("AssistantManager", "ONNX init failed: ${ex.message}")
                onnxAvailable = false
            }
        }
    }

    fun analyze(text: String): IntentResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return IntentResult("none", 1f)

        if (onnxAvailable && sessionIntent != null && ortEnv != null) {
            try {
                val inputName = sessionIntent!!.inputNames.iterator().next()
                val tensor = OnnxTensor.createTensor(ortEnv!!, arrayOf(trimmed))
                val res = sessionIntent!!.run(Collections.singletonMap(inputName, tensor))
                try {
                    if (res.size() > 0) {
                        val first = res.get(0)
                        val value = first.value
                        if (value is Array<*>) {
                            val maybe = value.firstOrNull() as? String
                            if (!maybe.isNullOrBlank()) return IntentResult(maybe, 0.95f)
                        } else if (value is String) {
                            if (value.isNotBlank()) return IntentResult(value, 0.95f)
                        }
                    }
                } finally {
                    try { res.close() } catch (_: Exception) {}
                    try { tensor.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.i("AssistantManager", "ONNX intent inference failed: ${e.message}")
            }
        }
        return ruleBasedAnalysis(trimmed)
    }

    private fun ruleBasedAnalysis(text: String): IntentResult {
        val lower = text.lowercase()

        val quoteRegex = "\"(.*?)\"".toRegex()
        val quoted = quoteRegex.find(text)?.groups?.get(1)?.value

        fun containsAny(triggerKey: String): Boolean {
            return getTriggers(triggerKey).any { lower.contains(it) }
        }

        fun extractAfter(triggerKey: String): String? {
            val triggers = getTriggers(triggerKey).map { "$it " }
            for (k in triggers) {
                val idx = lower.indexOf(k)
                if (idx >= 0) {
                    return text.substring(idx + k.length).trim().trim('"', '\'')
                }
            }
            return null
        }

        return when {
            containsAny("assistant_triggers_help") -> IntentResult("help")
            containsAny("assistant_triggers_whats_playing") -> IntentResult("whats_playing")
            containsAny("assistant_triggers_next") -> IntentResult("next")
            containsAny("assistant_triggers_previous") -> IntentResult("previous")
            containsAny("assistant_triggers_pause") -> IntentResult("pause")
            containsAny("assistant_triggers_repeat") -> IntentResult("repeat")
            containsAny("assistant_triggers_add_queue") -> {
                val after = extractAfter("assistant_triggers_add_queue") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("add_queue", 0.9f, mapOf("query" to after))
                else IntentResult("add_queue")
            }
            containsAny("assistant_triggers_play") -> {
                val after = extractAfter("assistant_triggers_play") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("play_search", 0.9f, mapOf("query" to after))
                else IntentResult("play", 0.9f)
            }
            containsAny("assistant_triggers_resume") -> IntentResult("play")
            containsAny("assistant_triggers_search") -> {
                val after = extractAfter("assistant_triggers_search") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("search", 0.95f, mapOf("query" to after))
                else IntentResult("search", 0.6f)
            }
            containsAny("assistant_triggers_settings") -> IntentResult("settings")
            else -> {
                if (!quoted.isNullOrBlank()) IntentResult("play_search", 0.9f, mapOf("query" to quoted))
                else IntentResult("unknown")
            }
        }
    }

    private suspend fun searchSpotifyTrack(query: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        val token = SpotifyTokenManager.getValidAccessToken(context) ?: return@withContext null
        suspendCoroutine { cont ->
            SpotifyRepository.searchAll(token, query) { response, error ->
                if (error != null || response == null) cont.resume(null)
                else cont.resume(response.tracks?.items?.firstOrNull())
            }
        }
    }

    private suspend fun createTrackFromSpotify(query: String, playlistId: String): TrackEntity? {
        val spotifyTrack = searchSpotifyTrack(query)
        val searchQuery = if (spotifyTrack != null) {
            "${spotifyTrack.name} ${spotifyTrack.artists.joinToString(" ") { it.name }}"
        } else query

        val videoId = withContext(Dispatchers.IO) {
            try { YouTubeManager.searchVideoId(searchQuery) } catch (_: Exception) { null }
        } ?: return null

        return TrackEntity(
            id = "assistant_${videoId}_${System.currentTimeMillis()}",
            playlistId = playlistId,
            spotifyTrackId = spotifyTrack?.id ?: "",
            name = spotifyTrack?.name ?: query,
            artists = spotifyTrack?.artists?.joinToString(", ") { it.name } ?: "",
            youtubeVideoId = videoId,
            audioUrl = null,
            position = 0,
            lastSyncTime = System.currentTimeMillis()
        )
    }

    private fun getAvailableCommands(): List<Pair<String, String>> {
        return listOf(
            t("assistant_cmd_play") to t("assistant_resume_playback"),
            t("assistant_cmd_pause") to t("assistant_pause_playback"),
            t("assistant_cmd_next") to t("assistant_next_song"),
            t("assistant_cmd_previous") to t("assistant_previous_song"),
            t("assistant_cmd_play_song") to t("assistant_search_play"),
            t("assistant_cmd_search") to t("assistant_search_song"),
            t("assistant_cmd_add_queue") to t("assistant_add_queue"),
            t("assistant_cmd_repeat") to t("assistant_repeat_mode"),
            t("assistant_cmd_whats_playing") to t("assistant_current_song"),
            t("assistant_cmd_help") to t("assistant_see_commands")
        )
    }

    suspend fun perform(result: IntentResult, playerViewModel: PlayerViewModel): String {
        return try {
            when (result.intent) {
                "help" -> {
                    val commands = getAvailableCommands()
                    val list = commands.joinToString("\n") { "• ${it.first}: ${it.second}" }
                    "${t("assistant_commands_available")}\n$list"
                }
                "whats_playing" -> {
                    val track = playerViewModel.currentTrack.value
                    if (track != null) {
                        val artists = track.artists.ifBlank { t("assistant_unknown_artist") }
                        String.format(t("assistant_now_playing"), track.name, artists)
                    } else t("assistant_nothing_playing")
                }
                "play" -> {
                    withContext(Dispatchers.Main) { playerViewModel.playPlayer() }
                    t("assistant_playing")
                }
                "pause" -> {
                    withContext(Dispatchers.Main) { playerViewModel.pausePlayer() }
                    t("assistant_paused")
                }
                "next" -> {
                    withContext(Dispatchers.Main) { playerViewModel.navigateToNext() }
                    t("assistant_next")
                }
                "previous" -> {
                    withContext(Dispatchers.Main) { playerViewModel.navigateToPrevious() }
                    t("assistant_previous")
                }
                "repeat" -> {
                    withContext(Dispatchers.Main) { playerViewModel.updateRepeatMode() }
                    t("assistant_repeat_changed")
                }
                "settings" -> t("assistant_open_settings")
                "play_search" -> {
                    val q = result.entities["query"] ?: ""
                    if (q.isBlank()) return t("assistant_what_play")
                    val track = createTrackFromSpotify(q, "assistant_${System.currentTimeMillis()}")
                        ?: return String.format(t("assistant_no_results"), q)
                    val ok = withContext(Dispatchers.Main) {
                        playerViewModel.initializePlayer()
                        playerViewModel.setCurrentPlaylist(listOf(track), 0)
                        try { playerViewModel.loadAudioFromTrack(track) } catch (_: Exception) { false }
                    }
                    if (ok) {
                        val artists = track.artists.ifBlank { "" }
                        if (artists.isNotBlank()) String.format(t("assistant_playing_song"), track.name, artists)
                        else String.format(t("assistant_playing_song_no_artist"), track.name)
                    } else String.format(t("assistant_error_play"), q)
                }
                "search" -> {
                    val q = result.entities["query"] ?: ""
                    if (q.isBlank()) return t("assistant_what_search")
                    val spotifyTrack = searchSpotifyTrack(q)
                        ?: return String.format(t("assistant_no_results"), q)
                    val artists = spotifyTrack.artists.joinToString(", ") { it.name }
                    String.format(t("assistant_found"), spotifyTrack.name, artists)
                }
                "add_queue" -> {
                    val q = result.entities["query"] ?: ""
                    if (q.isBlank()) return t("assistant_what_add")
                    val track = createTrackFromSpotify(q, "assistant_queue")
                        ?: return String.format(t("assistant_no_results"), q)
                    withContext(Dispatchers.Main) { playerViewModel.addToQueue(track) }
                    val artists = track.artists.ifBlank { "" }
                    if (artists.isNotBlank()) String.format(t("assistant_added_queue"), track.name, artists)
                    else String.format(t("assistant_added_queue_no_artist"), track.name)
                }
                else -> t("assistant_not_understand")
            }
        } catch (e: Exception) {
            Log.e("AssistantManager", "perform action error", e)
            t("assistant_error")
        }
    }

    fun close() {
        try { sessionIntent?.close() } catch (_: Exception) {}
        try { sessionNer?.close() } catch (_: Exception) {}
        sessionIntent = null
        sessionNer = null
        try { ortEnv = null } catch (_: Exception) {}
        onnxAvailable = false
    }
}
