package com.plyr.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Helper wrapper around Android TextToSpeech for speaking assistant responses.
 */
class AssistantTTSHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var listener: TTSListener? = null

    interface TTSListener {
        fun onStart() {}
        fun onDone() {}
        fun onError() {}
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                // Set default language
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        listener?.onStart()
                    }
                    override fun onDone(utteranceId: String?) {
                        listener?.onDone()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        listener?.onError()
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        listener?.onError()
                    }
                })
                // Speak pending text if any
                pendingText?.let { speak(it) }
                pendingText = null
            }
        }
    }

    fun setListener(l: TTSListener?) {
        listener = l
    }

    fun speak(text: String) {
        if (!isInitialized) {
            pendingText = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_response")
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

