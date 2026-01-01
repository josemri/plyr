package com.plyr.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.plyr.utils.Config
import java.util.Locale

/**
 * Helper wrapper around Android TextToSpeech for speaking assistant responses.
 */
class AssistantTTSHelper private constructor(private val context: Context) {
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
      * Convierte el idioma del asistente al Locale para TTS
     * Usa el idioma específico del asistente o el de la app según configuración
     */
    private fun getLocaleFromConfig(): Locale {
        val language = if (Config.isAssistantSameLanguage(context)) {
            Config.getLanguage(context)
        } else {
            Config.getAssistantLanguage(context)
        }
        return when (language) {
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

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: AssistantTTSHelper? = null

        /** Initialize the singleton if not already created. Safe to call multiple times. */
        fun initializeIfNeeded(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        // Store applicationContext to avoid leaking activities
                        INSTANCE = AssistantTTSHelper(context.applicationContext)
                    }
                }
            }
        }

        /** Shutdown and clear the singleton if present. */
        fun shutdownIfNeeded() {
            INSTANCE?.let {
                it.destroy()
                INSTANCE = null
            }
        }

        /** Convenience to speak text if the TTS is initialized and enabled. */
        fun speakIfReady(context: Context, text: String) {
            // Verificar si el asistente y TTS están habilitados
            if (!Config.isAssistantEnabled(context) || !Config.isAssistantTtsEnabled(context)) {
                return
            }
            initializeIfNeeded(context)
            INSTANCE?.speak(text)
        }

        /** Stop the current TTS playback if instance exists. */
        fun stopIfNeeded() {
            INSTANCE?.stop()
        }

        /** Check whether the TTS is currently speaking. */
        fun isSpeaking(): Boolean {
            return INSTANCE?.isSpeaking() == true
        }
    }
}
