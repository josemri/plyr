package com.plyr.assistant

import android.content.Context
import android.util.Log
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.YouTubeManager
import com.plyr.database.TrackEntity

import java.util.Collections

// coroutines used for threading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// If ONNX Runtime dependency is present these imports will be used at compile/run time
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Lightweight on-device assistant manager.
 *
 * Behavior:
 * - If ONNX models are present in assets (assistant_intent.onnx, assistant_ner.onnx) it will attempt to use ONNX Runtime.
 * - If models are not present or loading fails, uses a small rule-based fallback for intents and simple NER.
 *
 * The assistant exposes analyze(text) -> IntentResult which the UI can use to act on the app.
 */
class AssistantManager(context: Context) {

    data class IntentResult(
        val intent: String,
        val score: Float = 1.0f,
        val entities: Map<String, String> = emptyMap()
    )

    private var onnxAvailable = false
    private var ortEnv: OrtEnvironment? = null
    private var sessionIntent: OrtSession? = null
    private var sessionNer: OrtSession? = null

    init {
        try {
            // Check if ONNX Runtime classes are available
            Class.forName("ai.onnxruntime.OnnxTensor")
            onnxAvailable = true
        } catch (_: Exception) {
            onnxAvailable = false
        }

        if (onnxAvailable) {
            try {
                ortEnv = OrtEnvironment.getEnvironment()
                // Try to load models from assets
                val am = context.assets
                fun loadSession(assetName: String): OrtSession? {
                    return try {
                        val bytes = am.open(assetName).use { it.readBytes() }
                        ortEnv?.createSession(bytes)
                    } catch (ex: Exception) {
                        Log.i("AssistantManager", "Model $assetName not found in assets or failed to load: ${ex.message}")
                        null
                    }
                }

                sessionIntent = loadSession("assistant_intent.onnx")
                sessionNer = loadSession("assistant_ner.onnx")

                if (sessionIntent == null && sessionNer == null) {
                    // No models loaded
                    onnxAvailable = false
                }
            } catch (ex: Throwable) {
                Log.i("AssistantManager", "ONNX init failed: ${ex.message}")
                onnxAvailable = false
            }
        }
    }

    /**
     * Analyze text and return a high-level intent + optional entities.
     * Tries ONNX models if available, otherwise falls back to simple rule-based parsing.
     */
    fun analyze(text: String): IntentResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return IntentResult("none", 1f)

        // If ONNX models are present, attempt to run the intent model (simple string tensor -> string label expected)
        if (onnxAvailable && sessionIntent != null && ortEnv != null) {
            try {
                val inputName = sessionIntent!!.inputNames.iterator().next()
                val tensor = OnnxTensor.createTensor(ortEnv!!, arrayOf(trimmed))
                val res = sessionIntent!!.run(Collections.singletonMap(inputName, tensor))
                try {
                    if (res.size() > 0) {
                        val first = res.get(0)
                        val value = first.value
                        // If the model outputs a string label or array of strings
                        if (value is Array<*>) {
                            val maybe = value.firstOrNull() as? String
                            if (!maybe.isNullOrBlank()) return IntentResult(maybe, 0.95f)
                        } else if (value is String) {
                            if (value.isNotBlank()) return IntentResult(value, 0.95f)
                        }
                    }
                } finally {
                    // Close OrtValues returned by run()
                    try { res.close() } catch (_: Exception) {}
                    try { tensor.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.i("AssistantManager", "ONNX intent inference failed: ${e.message}")
                // Fall through to rule-based
            }
        }

        // Fallback
        return ruleBasedAnalysis(trimmed)
    }

    private fun ruleBasedAnalysis(text: String): IntentResult {
        val lower = text.lowercase()

        // NER: try to capture quoted text or phrase after known verbs
        val quoteRegex = "\"(.*?)\"".toRegex()
        val quoted = quoteRegex.find(text)?.groups?.get(1)?.value

        fun extractAfter(vararg keys: String): String? {
            for (k in keys) {
                val idx = lower.indexOf(k)
                if (idx >= 0) {
                    return text.substring(idx + k.length).trim().trim('"', '\'')
                }
            }
            return null
        }

        // Intent patterns (Spanish + English heuristics)
        return when {
            lower.contains("play next") || lower.contains("siguiente") || lower.contains("sigue") -> IntentResult("next")
            lower.contains("previous") || lower.contains("anterior") || lower.contains("atrás") || lower.contains("prev") -> IntentResult("previous")
            lower.contains("pause") || lower.contains("stop") || lower.contains("pausa") -> IntentResult("pause")
            lower.contains("play") || lower.contains("resume") || lower.contains("reproducir") || lower.contains("tocar") -> {
                // If user said play <song>
                val after = extractAfter("play ", "reproducir ", "tocar ") ?: quoted
                if (!after.isNullOrBlank()) {
                    IntentResult("play_search", 0.9f, mapOf("query" to after))
                } else {
                    IntentResult("play", 0.9f)
                }
            }
            lower.contains("search for") || lower.contains("buscar") || lower.contains("find") -> {
                val after = extractAfter("search for ", "buscar ", "find ") ?: quoted
                if (!after.isNullOrBlank()) {
                    IntentResult("search", 0.95f, mapOf("query" to after))
                } else {
                    IntentResult("search", 0.6f)
                }
            }
            lower.contains("settings") || lower.contains("ajustes") || lower.contains("configur") -> IntentResult("settings")
            lower.contains("shuffle") || lower.contains("aleatorio") -> IntentResult("shuffle")
            lower.contains("repeat") || lower.contains("repetir") -> IntentResult("repeat")
            lower.startsWith("add") && lower.contains("queue") -> {
                val after = extractAfter("add ", "agregar ") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("add_queue", 0.9f, mapOf("query" to after)) else IntentResult("add_queue")
            }
            else -> {
                // if it contains a quoted phrase, treat as search
                if (!quoted.isNullOrBlank()) IntentResult("search", 0.9f, mapOf("query" to quoted))
                else IntentResult("unknown")
            }
        }
    }

    /**
     * Perform the detected intent using PlayerViewModel and return a short user-facing message.
     */
    suspend fun perform(result: IntentResult, playerViewModel: PlayerViewModel): String {
        return try {
            when (result.intent) {
                "play" -> {
                    // Ensure player control happens on the main thread
                    withContext(Dispatchers.Main) {
                        playerViewModel.playPlayer()
                    }
                    "Reproduciendo"
                }
                "pause" -> {
                    withContext(Dispatchers.Main) {
                        playerViewModel.pausePlayer()
                    }
                    "Pausado"
                }
                "next" -> {
                    withContext(Dispatchers.Main) {
                        playerViewModel.navigateToNext()
                    }
                    "Siguiente"
                }
                "previous" -> {
                    withContext(Dispatchers.Main) {
                        playerViewModel.navigateToPrevious()
                    }
                    "Anterior"
                }
                "settings" -> { "Abrir ajustes" }
                "shuffle" -> { "Activar/desactivar aleatorio" }
                "repeat" -> {
                    withContext(Dispatchers.Main) {
                        playerViewModel.updateRepeatMode()
                    }
                    "Cambiar modo repetición"
                }

                // Try to search and play the first result for a user query
                "play_search" -> {
                    val q = result.entities["query"] ?: ""
                    if (q.isBlank()) return "Dime qué quieres reproducir"

                    val videoId = withContext(Dispatchers.IO) { try { YouTubeManager.searchVideoId(q) } catch (_: Exception) { null } }
                    if (videoId == null) return "No encontré resultados para: $q"

                    // Build a TrackEntity and instruct the player to load + play on main thread
                    val track = TrackEntity(
                        id = "assistant_$videoId",
                        playlistId = "assistant_${System.currentTimeMillis()}",
                        spotifyTrackId = "",
                        name = q,
                        artists = "",
                        youtubeVideoId = videoId,
                        audioUrl = null,
                        position = 0,
                        lastSyncTime = System.currentTimeMillis()
                    )

                    // Player initialization and playlist updates must run on the main thread
                    val ok = withContext(Dispatchers.Main) {
                        playerViewModel.initializePlayer()
                        playerViewModel.setCurrentPlaylist(listOf(track), 0)
                        try {
                            // loadAudioFromTrack is suspend and internally posts to Main as well, but call it from Main
                            playerViewModel.loadAudioFromTrack(track)
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (ok) "Reproduciendo: $q" else "Error al reproducir: $q"
                }

                // Search only (no autoplay)
                "search" -> {
                    val q = result.entities["query"] ?: ""
                    if (q.isBlank()) return "Dime qué quieres buscar"
                    val videoId = withContext(Dispatchers.IO) { try { YouTubeManager.searchVideoId(q) } catch (_: Exception) { null } }
                    if (videoId == null) return "No encontré resultados para: $q"
                    "Encontré un resultado para: $q"
                }

                // Add to queue if possible
                "add_queue" -> {
                    val q = result.entities["query"] ?: ""
                    if (q.isBlank()) return "Dime qué quieres añadir a la cola"
                    val videoId = withContext(Dispatchers.IO) { try { YouTubeManager.searchVideoId(q) } catch (_: Exception) { null } }
                    if (videoId == null) return "No encontré resultados para: $q"

                    val track = TrackEntity(
                        id = "assistant_queue_$videoId",
                        playlistId = "assistant_queue",
                        spotifyTrackId = "",
                        name = q,
                        artists = "",
                        youtubeVideoId = videoId,
                        audioUrl = null,
                        position = 0,
                        lastSyncTime = System.currentTimeMillis()
                    )
                    // addToQueue can be called from any thread, but ensure UI updates are on main
                    withContext(Dispatchers.Main) {
                        playerViewModel.addToQueue(track)
                    }
                    "Añadido a la cola: $q"
                }

                else -> "No entiendo. Prueba con 'play', 'pause', 'next', 'search for <canción>'"
            }
        } catch (e: Exception) {
            Log.e("AssistantManager", "perform action error", e)
            "Error al ejecutar la acción"
        }
    }

    // Clean up sessions if the manager is destroyed (not strictly necessary in this demo)
    fun close() {
        try { sessionIntent?.close() } catch (_: Exception) {}
        try { sessionNer?.close() } catch (_: Exception) {}
        sessionIntent = null
        sessionNer = null
        try { ortEnv = null } catch (_: Exception) {}
        onnxAvailable = false
    }
}
