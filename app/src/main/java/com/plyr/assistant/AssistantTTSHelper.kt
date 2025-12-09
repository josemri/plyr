package com.plyr.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.plyr.utils.Config
import java.util.Locale

/**
 * Helper wrapper around Android TextToSpeech for speaking assistant responses.
 */
class AssistantTTSHelper(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null
    private var listener: TTSListener? = null

    interface TTSListener {
        fun onStart() {}
        fun onDone() {}
        fun onError() {}
    }

    /**
     * Convierte el idioma de la app al Locale para TTS
     */
    private fun getLocaleFromConfig(): Locale {
        return when (Config.getLanguage(context)) {
            "español" -> Locale("es", "ES")
            "english" -> Locale.US
            "català" -> Locale("ca", "ES")
            "日本語" -> Locale.JAPAN
            else -> Locale("es", "ES")
        }
    }

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                // Set language from app config
                val locale = getLocaleFromConfig()
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to default if language not available
                    tts?.language = Locale.getDefault()
                }
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
        // Update language before speaking in case it changed
        val locale = getLocaleFromConfig()
        tts?.setLanguage(locale)
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
