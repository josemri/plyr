package com.plyr.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Helper wrapper around Android SpeechRecognizer to simplify usage.
 * Provides callbacks for partial and final results and errors.
 */
class AssistantVoiceHelper(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: VoiceListener? = null
    @Volatile
    private var isCancelled = false

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
                        if (!isCancelled) listener?.onReady()
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        if (!isCancelled) listener?.onError(error)
                    }

                    override fun onResults(results: Bundle?) {
                        if (isCancelled) return
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        listener?.onResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (isCancelled) return
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = partials?.firstOrNull() ?: ""
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

    fun setListener(l: VoiceListener?) {
        listener = l
    }

    fun startListening(language: String? = null) {
        isCancelled = false
        val sr = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
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
