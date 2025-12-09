package com.plyr.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.plyr.utils.Config

/**
 * Helper wrapper around Android SpeechRecognizer to simplify usage.
 * Provides callbacks for partial and final results and errors.
 */
class AssistantVoiceHelper(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: VoiceListener? = null
    @Volatile
    private var isCancelled = false

    companion object {
        private const val TAG = "AssistantVoice"
    }

    interface VoiceListener {
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(errorCode: Int)
        fun onReady() {}
    }

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                        if (!isCancelled) listener?.onReady()
                    }
                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech detected")
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech detected")
                    }
                    override fun onError(error: Int) {
                        Log.e(TAG, "Recognition error: $error")
                        if (!isCancelled) listener?.onError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        if (isCancelled) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        Log.d(TAG, "Final result: \"$text\"")
                        listener?.onResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (isCancelled) return
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = partials?.firstOrNull() ?: ""
                        Log.d(TAG, "Partial result: \"$text\"")
                        listener?.onPartial(text)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } catch (e: Exception) {
                Log.i("AssistantVoiceHelper", "SpeechRecognizer init failed: ${e.message}")
                speechRecognizer = null
            }
        }
    }

    /**
     * Convierte el idioma de la app al código de idioma para el reconocimiento de voz
     */
    private fun getLanguageCode(): String {
        return when (Config.getLanguage(context)) {
            "español" -> "es-ES"
            "english" -> "en-US"
            "català" -> "ca-ES"
            "日本語" -> "ja-JP"
            else -> "es-ES"
        }
    }

    fun setListener(l: VoiceListener?) {
        listener = l
    }

    fun startListening(language: String? = null) {
        isCancelled = false
        val sr = speechRecognizer ?: return
        val langCode = language ?: getLanguageCode()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            // Apagar micrófono tras 1.5 segundos de silencio
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }
        try {
            sr.startListening(intent)
        } catch (e: Exception) {
            listener?.onError(-1)
        }
    }

    fun stopListening() {
        isCancelled = true
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
    }

    fun cancel() {
        isCancelled = true
        try { speechRecognizer?.cancel() } catch (_: Exception) {}
    }

    fun destroy() {
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }
}
