package com.plyr.assistant

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.plyr.viewmodel.PlayerViewModel
import com.plyr.network.YouTubeManager
import com.plyr.network.SpotifyRepository
import com.plyr.network.SpotifyTrack
import com.plyr.database.TrackEntity
import com.plyr.utils.SpotifyTokenManager
import com.plyr.utils.Translations

import java.util.Collections
import java.util.Timer
import java.util.TimerTask
import java.util.Calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Lightweight on-device assistant manager with enhanced NLU capabilities.
 */
class AssistantManager(private val context: Context) {

    companion object {
        private const val TAG = "AssistantManager"

        // Umbral de similitud para fuzzy matching (0.0 - 1.0)
        private const val FUZZY_THRESHOLD = 0.7f
    }

    data class IntentResult(
        val intent: String,
        val score: Float = 1.0f,
        val entities: Map<String, String> = emptyMap()
    )

    // Estado del asistente
    enum class AssistantState {
        IDLE, LISTENING, PROCESSING, SPEAKING
    }

    private var onnxAvailable = false
    private var ortEnv: OrtEnvironment? = null
    private var sessionIntent: OrtSession? = null
    private var sessionNer: OrtSession? = null

    // Sleep timer
    private var sleepTimer: Timer? = null
    private var sleepTimerEndTime: Long = 0

    // Estado actual
    var currentState: AssistantState = AssistantState.IDLE
        private set

    // Callback para cambios de estado
    var onStateChange: ((AssistantState) -> Unit)? = null

    // Último comando entendido (para feedback visual)
    var lastRecognizedCommand: String = ""
        private set

    private fun t(key: String) = Translations.get(context, key)

    private fun getTriggers(key: String): List<String> {
        return t(key).split("|").map { it.trim().lowercase() }
    }

    fun setState(state: AssistantState) {
        currentState = state
        onStateChange?.invoke(state)
    }

    // ==================== FUZZY MATCHING ====================

    /**
     * Calcula la distancia de Levenshtein entre dos strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i-1] == s2[j-1]) {
                    dp[i-1][j-1]
                } else {
                    minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1]) + 1
                }
            }
        }
        return dp[m][n]
    }

    /**
     * Calcula similitud entre dos strings (0.0 - 1.0)
     */
    private fun similarity(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        return 1.0f - (levenshteinDistance(s1, s2).toFloat() / maxLen)
    }

    /**
     * Busca si alguna palabra del texto es similar a algún trigger (fuzzy match)
     */
    private fun fuzzyContains(text: String, triggers: List<String>): Pair<Boolean, Float> {
        val words = text.lowercase().split(" ", ",", ".", "?", "!")
        var bestScore = 0f

        for (trigger in triggers) {
            // Match exacto
            if (text.lowercase().contains(trigger)) {
                return Pair(true, 1.0f)
            }

            // Fuzzy match por palabras
            val triggerWords = trigger.split(" ")
            for (word in words) {
                if (word.length < 3) continue // Ignorar palabras muy cortas

                for (tw in triggerWords) {
                    if (tw.length < 3) continue
                    val sim = similarity(word, tw)
                    if (sim > bestScore) bestScore = sim
                }
            }

            // También comparar frases completas si el trigger tiene múltiples palabras
            if (triggerWords.size > 1) {
                val sim = similarity(text.lowercase(), trigger)
                if (sim > bestScore) bestScore = sim
            }
        }

        return Pair(bestScore >= FUZZY_THRESHOLD, bestScore)
    }

    // ==================== SEMANTIC PATTERNS ====================

    /**
     * Patrones semánticos para detectar intenciones de forma más natural
     */
    private data class SemanticPattern(
        val intent: String,
        val patterns: List<Regex>,
        val entityExtractor: ((MatchResult) -> Map<String, String>)? = null
    )

    private val semanticPatterns: List<SemanticPattern> by lazy {
        listOf(
            // Reproducir música - patrones muy flexibles
            SemanticPattern(
                "play_search",
                listOf(
                    // "quiero escuchar X", "I want to hear X", "me apetece X"
                    Regex("(?:quiero|want|wanna|me apetece|me gustaría|i'd like to).*(?:escuchar|oír|oir|hear|listen)\\s+(.+)", RegexOption.IGNORE_CASE),
                    // "ponme X", "play X", "pon X"
                    Regex("(?:ponme|pon|play|put on|reproduce|toca)\\s+(.+)", RegexOption.IGNORE_CASE),
                    // "algo de X", "something from X", "música de X"
                    Regex("(?:algo|something|anything|música|music)\\s+(?:de|from|by|del)\\s+(.+)", RegexOption.IGNORE_CASE),
                    // "X por favor", "X please"
                    Regex("(.+?)\\s+(?:por favor|please|porfavor|porfa)$", RegexOption.IGNORE_CASE),
                    // "escucha X", "reproduce X"
                    Regex("(?:escucha|reproduce|listen to)\\s+(.+)", RegexOption.IGNORE_CASE)
                )
            ) { match ->
                val query = cleanQueryText(match.groupValues[1])
                if (query.isNotBlank()) mapOf("query" to query) else emptyMap()
            },

            // Control de reproducción
            SemanticPattern(
                "pause",
                listOf(
                    Regex("(?:para|stop|detén|deten|calla|silencio|quiet|pause|pausa|wait|espera)", RegexOption.IGNORE_CASE)
                )
            ),

            SemanticPattern(
                "play",
                listOf(
                    Regex("^(?:play|resume|continua|continue|sigue|dale|go|start|empieza|comienza)$", RegexOption.IGNORE_CASE),
                    Regex("(?:sigue|continúa|resume)\\s*(?:la música|playing|reproduciendo)?", RegexOption.IGNORE_CASE)
                )
            ),

            SemanticPattern(
                "next",
                listOf(
                    Regex("(?:siguiente|next|skip|salta|pasa|otra|another|cambia|change)", RegexOption.IGNORE_CASE)
                )
            ),

            SemanticPattern(
                "previous",
                listOf(
                    Regex("(?:anterior|previous|back|atrás|atras|vuelve|go back|última|ultima|last)", RegexOption.IGNORE_CASE)
                )
            ),

            // Volumen - IMPORTANTE: volume_set debe ir ANTES que volume_up/down
            SemanticPattern(
                "volume_set",
                listOf(
                    // "volumen al 50", "volume to 50", "set volume to 50"
                    Regex("(?:volumen|volume|sonido|sound)\\s*(?:al?|to|at)\\s*(\\d+)", RegexOption.IGNORE_CASE),
                    // "pon el volumen al 50", "set volume at 50"
                    Regex("(?:pon|set|put).*(?:volumen|volume).*(?:al?|to|at)\\s*(\\d+)", RegexOption.IGNORE_CASE),
                    // "50 percent volume", "50% volume"
                    Regex("(\\d+)\\s*(?:%|percent|por ?ciento)?\\s*(?:de )?(?:volumen|volume)", RegexOption.IGNORE_CASE),
                    // "volumen 50", "volume 50" (número después de volumen)
                    Regex("(?:volumen|volume)\\s+(\\d+)(?:\\s*%)?", RegexOption.IGNORE_CASE)
                )
            ) { match ->
                val level = match.groupValues.getOrNull(1)?.toIntOrNull()
                if (level != null) mapOf("level" to level.toString()) else emptyMap()
            },

            SemanticPattern(
                "volume_up",
                listOf(
                    Regex("(?:sube|subir|más alto|louder|turn up|volume up|aumenta|increase).*(?:volumen|volume|sonido|sound)?", RegexOption.IGNORE_CASE),
                    Regex("(?:volumen|volume|sonido).*(?:arriba|up|más|more|alto)", RegexOption.IGNORE_CASE)
                )
            ),

            SemanticPattern(
                "volume_down",
                listOf(
                    Regex("(?:baja|bajar|más bajo|quieter|softer|turn down|volume down|reduce|disminuye).*(?:volumen|volume|sonido|sound)?", RegexOption.IGNORE_CASE),
                    Regex("(?:volumen|volume|sonido).*(?:abajo|down|menos|less|bajo)", RegexOption.IGNORE_CASE)
                )
            ),

            // Información
            SemanticPattern(
                "whats_playing",
                listOf(
                    Regex("(?:qué|que|what|which).*(?:suena|canción|song|playing|sonando|escuchando|track)", RegexOption.IGNORE_CASE),
                    Regex("(?:cómo|como|how).*(?:llama|call|nombre|name).*(?:canción|song|esta|this)", RegexOption.IGNORE_CASE),
                    Regex("(?:dime|tell me).*(?:canción|song|qué|what)", RegexOption.IGNORE_CASE)
                )
            ),

            SemanticPattern(
                "who_sings",
                listOf(
                    Regex("(?:quién|quien|who).*(?:canta|sings|artista|artist|interpreta)", RegexOption.IGNORE_CASE),
                    Regex("(?:de quién|de quien|whose|by whom).*(?:es|is).*(?:canción|song|esta|this)?", RegexOption.IGNORE_CASE)
                )
            ),

            // Shuffle
            SemanticPattern(
                "shuffle",
                listOf(
                    Regex("(?:mezcla|shuffle|random|aleatorio|mix|revuelve|desordena)", RegexOption.IGNORE_CASE)
                )
            ),

            // Ayuda
            SemanticPattern(
                "help",
                listOf(
                    Regex("(?:ayuda|help|comandos|commands|qué puedo|what can|opciones|options)", RegexOption.IGNORE_CASE)
                )
            )
        )
    }

    /**
     * Limpia el texto de query eliminando palabras innecesarias
     */
    private fun cleanQueryText(query: String): String {
        val stopWords = listOf(
            // English
            "something", "anything", "some", "a", "the", "from", "by", "of",
            "please", "pls", "plz", "thanks", "thank you",
            "i want", "i wanna", "i'd like", "can you", "could you",
            "play me", "put on", "give me",
            // Spanish
            "algo", "alguna", "algún", "algun", "una", "un", "el", "la", "los", "las",
            "de", "del", "por favor", "porfa", "porfavor", "gracias",
            "quiero", "quisiera", "me gustaría", "puedes", "podrías",
            "ponme", "dame", "pon",
            // Catalan
            "alguna cosa", "una", "un", "el", "la", "els", "les",
            "de", "del", "si us plau", "sisplau", "gràcies"
        )

        var cleaned = query.lowercase().trim()

        // Eliminar stop words del inicio
        for (sw in stopWords.sortedByDescending { it.length }) {
            if (cleaned.startsWith("$sw ")) {
                cleaned = cleaned.removePrefix("$sw ").trim()
            }
        }

        // Eliminar stop words del final
        for (sw in stopWords.sortedByDescending { it.length }) {
            if (cleaned.endsWith(" $sw")) {
                cleaned = cleaned.removeSuffix(" $sw").trim()
            }
        }

        return cleaned.trim()
    }

    /**
     * Intenta hacer match con patrones semánticos
     */
    private fun matchSemanticPatterns(text: String): IntentResult? {
        for (pattern in semanticPatterns) {
            for (regex in pattern.patterns) {
                val match = regex.find(text)
                if (match != null) {
                    val entities = pattern.entityExtractor?.invoke(match) ?: emptyMap()
                    Log.d(TAG, "Semantic match: ${pattern.intent} with entities: $entities")
                    return IntentResult(pattern.intent, 0.85f, entities)
                }
            }
        }
        return null
    }

    // ==================== INTENT INFERENCE ====================

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
                        // Models are optional - silently skip if not found
                        Log.v(TAG, "Optional model $assetName not available")
                        null
                    }
                }
                sessionIntent = loadSession("assistant_intent.onnx")
                sessionNer = loadSession("assistant_ner.onnx")
                if (sessionIntent == null && sessionNer == null) {
                    onnxAvailable = false
                    Log.d(TAG, "ONNX models not found, using rule-based NLU")
                }
            } catch (ex: Throwable) {
                Log.d(TAG, "ONNX runtime not available, using rule-based NLU")
                onnxAvailable = false
            }
        }
    }

    fun analyze(text: String): IntentResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return IntentResult("none", 1f)

        Log.d(TAG, "Analyzing: \"$trimmed\"")

        // 1. Intentar con ONNX si está disponible
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
                            if (!maybe.isNullOrBlank()) {
                                Log.d(TAG, "ONNX result: $maybe")
                                return IntentResult(maybe, 0.95f)
                            }
                        } else if (value is String) {
                            if (value.isNotBlank()) {
                                Log.d(TAG, "ONNX result: $value")
                                return IntentResult(value, 0.95f)
                            }
                        }
                    }
                } finally {
                    try { res.close() } catch (_: Exception) {}
                    try { tensor.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.i(TAG, "ONNX intent inference failed: ${e.message}")
            }
        }

        // 2. Intentar con patrones semánticos (más flexibles)
        val semanticResult = matchSemanticPatterns(trimmed)
        if (semanticResult != null) {
            return semanticResult
        }

        // 3. Fallback a análisis basado en reglas con fuzzy matching
        return ruleBasedAnalysis(trimmed)
    }

    private fun ruleBasedAnalysis(text: String): IntentResult {
        val lower = text.lowercase()

        val quoteRegex = "\"(.*?)\"".toRegex()
        val quoted = quoteRegex.find(text)?.groups?.get(1)?.value

        fun containsAny(triggerKey: String): Boolean {
            val triggers = getTriggers(triggerKey)
            // Primero intentar match exacto
            if (triggers.any { lower.contains(it) }) return true
            // Luego fuzzy match
            val (found, score) = fuzzyContains(lower, triggers)
            if (found) {
                Log.d(TAG, "Fuzzy match for $triggerKey with score $score")
            }
            return found
        }

        fun containsAnyExact(triggerKey: String): Boolean {
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

        // Extractor de entidades mejorado
        fun extractMusicEntity(text: String): String? {
            // Patrones para extraer artista/canción
            val patterns = listOf(
                // "de X", "by X", "from X"
                Regex("(?:de|by|from|del)\\s+(.+?)(?:\\s+(?:por favor|please))?$", RegexOption.IGNORE_CASE),
                // Contenido entre comillas
                Regex("\"(.+?)\""),
                Regex("'(.+?)'"),
                // Después de verbos de reproducción
                Regex("(?:play|pon|reproduce|escucha|toca)\\s+(.+?)(?:\\s+(?:por favor|please))?$", RegexOption.IGNORE_CASE)
            )

            for (pattern in patterns) {
                val match = pattern.find(text)
                if (match != null && match.groupValues.size > 1) {
                    val extracted = cleanQueryText(match.groupValues[1])
                    if (extracted.isNotBlank() && extracted.length > 1) {
                        return extracted
                    }
                }
            }
            return null
        }

        // Limpiar frases comunes que no son parte del artista/canción
        fun cleanQuery(query: String): String {
            return cleanQueryText(query)
        }

        // Extraer número del texto
        fun extractNumber(): Int? {
            val numberRegex = "(\\d+)".toRegex()
            return numberRegex.find(lower)?.groups?.get(1)?.value?.toIntOrNull()
        }

        // Extraer tiempo para sleep timer
        fun extractTime(): Pair<Int, String>? {
            val minutesRegex = "(\\d+)\\s*(minuto|minute|min)".toRegex()
            val hoursRegex = "(\\d+)\\s*(hora|hour|h)".toRegex()
            val atTimeRegex = "(\\d{1,2})[:\\.]?(\\d{2})?".toRegex()

            minutesRegex.find(lower)?.let {
                return Pair(it.groups[1]?.value?.toIntOrNull() ?: 0, "minutes")
            }
            hoursRegex.find(lower)?.let {
                return Pair(it.groups[1]?.value?.toIntOrNull() ?: 0, "hours")
            }
            if (lower.contains("a las") || lower.contains("at ")) {
                atTimeRegex.find(lower)?.let {
                    val hour = it.groups[1]?.value?.toIntOrNull() ?: return null
                    val minute = it.groups[2]?.value?.toIntOrNull() ?: 0
                    return Pair(hour * 60 + minute, "absolute")
                }
            }
            return null
        }

        // Detectar comandos compuestos
        val hasNext = containsAnyExact("assistant_triggers_next")
        val hasVolumeUp = containsAnyExact("assistant_triggers_volume_up")
        val hasVolumeDown = containsAnyExact("assistant_triggers_volume_down")

        if (hasNext && (hasVolumeUp || hasVolumeDown)) {
            return IntentResult("compound", 0.9f, mapOf(
                "actions" to if (hasVolumeUp) "next,volume_up" else "next,volume_down"
            ))
        }

        return when {
            // Comandos de ayuda
            containsAny("assistant_triggers_help") -> IntentResult("help")

            // Información contextual
            containsAny("assistant_triggers_who_sings") -> IntentResult("who_sings")
            containsAny("assistant_triggers_what_album") -> IntentResult("what_album")
            containsAny("assistant_triggers_how_long") -> IntentResult("how_long")

            // Control de volumen
            containsAny("assistant_triggers_mute") -> IntentResult("mute")
            containsAny("assistant_triggers_volume_up") -> {
                val amount = extractNumber() ?: 10
                IntentResult("volume_up", 0.9f, mapOf("amount" to amount.toString()))
            }
            containsAny("assistant_triggers_volume_down") -> {
                val amount = extractNumber() ?: 10
                IntentResult("volume_down", 0.9f, mapOf("amount" to amount.toString()))
            }
            containsAny("assistant_triggers_volume_set") -> {
                val level = extractNumber()
                if (level != null) IntentResult("volume_set", 0.9f, mapOf("level" to level.toString()))
                else IntentResult("volume_set", 0.6f)
            }

            // Sleep timer
            containsAny("assistant_triggers_sleep_timer") -> {
                val time = extractTime()
                if (time != null) {
                    IntentResult("sleep_timer", 0.9f, mapOf(
                        "value" to time.first.toString(),
                        "unit" to time.second
                    ))
                } else {
                    IntentResult("sleep_timer", 0.6f)
                }
            }
            containsAny("assistant_triggers_cancel_timer") -> IntentResult("cancel_timer")

            // Comandos de playlist
            containsAny("assistant_triggers_create_playlist") -> {
                val after = extractAfter("assistant_triggers_create_playlist") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("create_playlist", 0.9f, mapOf("name" to after))
                else IntentResult("create_playlist", 0.6f)
            }
            containsAny("assistant_triggers_add_favorites") -> IntentResult("add_favorites")
            containsAny("assistant_triggers_shuffle") -> IntentResult("shuffle")
            containsAny("assistant_triggers_save_song") -> IntentResult("save_song")

            // Comandos naturales con género/artista
            containsAny("assistant_triggers_play_genre") -> {
                val after = extractAfter("assistant_triggers_play_genre") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("play_search", 0.9f, mapOf("query" to after))
                else IntentResult("play_search", 0.6f)
            }
            containsAny("assistant_triggers_play_mood") -> {
                val after = extractAfter("assistant_triggers_play_mood") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("play_search", 0.9f, mapOf("query" to "$after music"))
                else IntentResult("play_search", 0.6f)
            }

            // Comandos básicos existentes
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
                val after = extractAfter("assistant_triggers_play") ?: quoted ?: extractMusicEntity(text)
                if (!after.isNullOrBlank()) {
                    val cleanedQuery = cleanQuery(after)
                    if (cleanedQuery.isNotBlank()) {
                        IntentResult("play_search", 0.9f, mapOf("query" to cleanedQuery))
                    } else {
                        IntentResult("play", 0.9f)
                    }
                }
                else IntentResult("play", 0.9f)
            }
            containsAny("assistant_triggers_resume") -> IntentResult("play")
            containsAny("assistant_triggers_search") -> {
                val after = extractAfter("assistant_triggers_search") ?: quoted
                if (!after.isNullOrBlank()) IntentResult("search", 0.95f, mapOf("query" to after))
                else IntentResult("search", 0.6f)
            }
            containsAny("assistant_triggers_settings") -> IntentResult("settings")

            // Fallback: si hay comillas, asumir que quiere reproducir
            !quoted.isNullOrBlank() -> IntentResult("play_search", 0.8f, mapOf("query" to quoted))

            // Último intento: extraer entidad musical del texto completo
            else -> {
                val entity = extractMusicEntity(text)
                if (!entity.isNullOrBlank()) {
                    Log.d(TAG, "Fallback entity extraction: $entity")
                    IntentResult("play_search", 0.7f, mapOf("query" to entity))
                } else {
                    IntentResult("unknown")
                }
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
            t("assistant_cmd_help") to t("assistant_see_commands"),
            // Nuevos comandos
            t("assistant_cmd_volume") to t("assistant_volume_desc"),
            t("assistant_cmd_shuffle") to t("assistant_shuffle_desc"),
            t("assistant_cmd_favorites") to t("assistant_favorites_desc"),
            t("assistant_cmd_who_sings") to t("assistant_who_sings_desc"),
            t("assistant_cmd_sleep_timer") to t("assistant_sleep_timer_desc")
        )
    }

    private fun getAudioManager(): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun setVolume(level: Int) {
        val audioManager = getAudioManager()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (level * maxVolume / 100).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }

    private fun adjustVolume(delta: Int) {
        val audioManager = getAudioManager()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val change = (delta * maxVolume / 100).coerceAtLeast(1)
        val newVolume = (currentVolume + change).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }

    private fun getCurrentVolumePercent(): Int {
        val audioManager = getAudioManager()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (currentVolume * 100 / maxVolume)
    }

    private fun startSleepTimer(minutes: Int, playerViewModel: PlayerViewModel) {
        cancelSleepTimer()
        sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        sleepTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    playerViewModel.pausePlayer()
                    sleepTimer = null
                    sleepTimerEndTime = 0
                }
            }, minutes * 60 * 1000L)
        }
    }

    private fun cancelSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerEndTime = 0
    }

    fun getSleepTimerRemainingMinutes(): Int {
        if (sleepTimerEndTime == 0L) return 0
        val remaining = sleepTimerEndTime - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 60000).toInt() else 0
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    suspend fun perform(result: IntentResult, playerViewModel: PlayerViewModel): String {
        setState(AssistantState.PROCESSING)
        lastRecognizedCommand = result.intent

        return try {
            when (result.intent) {
                "help" -> {
                    val commands = getAvailableCommands()
                    val list = commands.map { it.first }.joinToString(" / ")
                    list
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

                // Información contextual
                "who_sings" -> {
                    val track = playerViewModel.currentTrack.value
                    if (track != null) {
                        val artists = track.artists.ifBlank { t("assistant_unknown_artist") }
                        String.format(t("assistant_artist_info"), artists)
                    } else t("assistant_nothing_playing")
                }
                "what_album" -> {
                    val track = playerViewModel.currentTrack.value
                    if (track != null) {
                        // Intentar obtener info del álbum desde Spotify
                        val spotifyTrack = if (track.spotifyTrackId.isNotBlank()) {
                            searchSpotifyTrack(track.name)
                        } else null

                        val albumName = spotifyTrack?.album?.name ?: t("assistant_unknown_album")
                        String.format(t("assistant_album_info"), albumName)
                    } else t("assistant_nothing_playing")
                }
                "how_long" -> {
                    val player = playerViewModel.exoPlayer
                    if (player != null && player.duration > 0) {
                        val duration = formatDuration(player.duration)
                        val position = formatDuration(player.currentPosition)
                        String.format(t("assistant_duration_info"), position, duration)
                    } else t("assistant_nothing_playing")
                }

                // Control de volumen
                "mute" -> {
                    setVolume(0)
                    t("assistant_muted")
                }
                "volume_up" -> {
                    val amount = result.entities["amount"]?.toIntOrNull() ?: 10
                    adjustVolume(amount)
                    String.format(t("assistant_volume_set_to"), getCurrentVolumePercent())
                }
                "volume_down" -> {
                    val amount = result.entities["amount"]?.toIntOrNull() ?: 10
                    adjustVolume(-amount)
                    String.format(t("assistant_volume_set_to"), getCurrentVolumePercent())
                }
                "volume_set" -> {
                    val level = result.entities["level"]?.toIntOrNull()
                    if (level != null) {
                        setVolume(level.coerceIn(0, 100))
                        String.format(t("assistant_volume_set_to"), level)
                    } else t("assistant_what_volume")
                }

                // Sleep timer
                "sleep_timer" -> {
                    val value = result.entities["value"]?.toIntOrNull()
                    val unit = result.entities["unit"] ?: "minutes"

                    if (value != null) {
                        val minutes = when (unit) {
                            "hours" -> value * 60
                            "absolute" -> {
                                // Calcular minutos hasta la hora especificada
                                val now = Calendar.getInstance()
                                val target = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, value / 60)
                                    set(Calendar.MINUTE, value % 60)
                                    set(Calendar.SECOND, 0)
                                    if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
                                }
                                ((target.timeInMillis - now.timeInMillis) / 60000).toInt()
                            }
                            else -> value
                        }
                        startSleepTimer(minutes, playerViewModel)
                        String.format(t("assistant_sleep_timer_set"), minutes)
                    } else t("assistant_what_time")
                }
                "cancel_timer" -> {
                    cancelSleepTimer()
                    t("assistant_timer_cancelled")
                }

                // Comandos de playlist
                "shuffle" -> {
                    val playlist = playerViewModel.currentPlaylist.value
                    if (playlist != null && playlist.isNotEmpty()) {
                        val shuffled = playlist.shuffled()
                        withContext(Dispatchers.Main) {
                            playerViewModel.setCurrentPlaylist(shuffled, 0)
                        }
                        t("assistant_shuffled")
                    } else t("assistant_nothing_playing")
                }
                "add_favorites" -> {
                    val track = playerViewModel.currentTrack.value
                    if (track != null) {
                        // Guardar en favoritos usando Spotify API
                        val token = SpotifyTokenManager.getValidAccessToken(context)
                        if (token != null && track.spotifyTrackId.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                SpotifyRepository.saveTrack(token, track.spotifyTrackId) { success, _ ->
                                    // No hacemos nada con el resultado aquí
                                }
                            }
                            String.format(t("assistant_added_favorites"), track.name)
                        } else t("assistant_cannot_save")
                    } else t("assistant_nothing_playing")
                }
                "save_song" -> {
                    val track = playerViewModel.currentTrack.value
                    if (track != null) {
                        val token = SpotifyTokenManager.getValidAccessToken(context)
                        if (token != null && track.spotifyTrackId.isNotBlank()) {
                            withContext(Dispatchers.IO) {
                                SpotifyRepository.saveTrack(token, track.spotifyTrackId) { _, _ -> }
                            }
                            String.format(t("assistant_song_saved"), track.name)
                        } else t("assistant_cannot_save")
                    } else t("assistant_nothing_playing")
                }
                "create_playlist" -> {
                    val name = result.entities["name"]
                    if (!name.isNullOrBlank()) {
                        // TODO: Implementar creación de playlist
                        String.format(t("assistant_playlist_created"), name)
                    } else t("assistant_what_playlist_name")
                }

                // Comandos compuestos
                "compound" -> {
                    val actions = result.entities["actions"]?.split(",") ?: emptyList()
                    val results = mutableListOf<String>()
                    for (action in actions) {
                        when (action.trim()) {
                            "next" -> {
                                withContext(Dispatchers.Main) { playerViewModel.navigateToNext() }
                                results.add(t("assistant_next"))
                            }
                            "volume_up" -> {
                                adjustVolume(10)
                                results.add(String.format(t("assistant_volume_set_to"), getCurrentVolumePercent()))
                            }
                            "volume_down" -> {
                                adjustVolume(-10)
                                results.add(String.format(t("assistant_volume_set_to"), getCurrentVolumePercent()))
                            }
                        }
                    }
                    results.joinToString(". ")
                }

                "settings" -> t("assistant_open_settings")
                "play_search" -> {
                    val q = result.entities["query"] ?: ""
                    Log.d("AssistantManager", "play_search query: \"$q\"")
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
        cancelSleepTimer()
        try { sessionIntent?.close() } catch (_: Exception) {}
        try { sessionNer?.close() } catch (_: Exception) {}
        sessionIntent = null
        sessionNer = null
        try { ortEnv = null } catch (_: Exception) {}
        onnxAvailable = false
    }
}
