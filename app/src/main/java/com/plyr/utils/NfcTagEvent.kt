package com.plyr.utils

import android.nfc.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton para comunicar eventos de tags NFC detectados
 * entre la MainActivity y los composables que necesitan escribir NFC
 */
object NfcTagEvent {
    private val _detectedTag = MutableStateFlow<Tag?>(null)
    val detectedTag: StateFlow<Tag?> = _detectedTag.asStateFlow()

    // Estado que indica si estamos en modo escritura (bloquea la lectura automática)
    private val _isWriteMode = MutableStateFlow(false)
    val isWriteMode: StateFlow<Boolean> = _isWriteMode.asStateFlow()

    fun onTagDetected(tag: Tag) {
        android.util.Log.d("NfcTagEvent", "🏷️ Tag detected and stored: $tag")
        _detectedTag.value = tag
    }

    fun consumeTag(): Tag? {
        val tag = _detectedTag.value
        _detectedTag.value = null
        return tag
    }

    fun clear() {
        _detectedTag.value = null
    }

    fun setWriteMode(enabled: Boolean) {
        android.util.Log.d("NfcTagEvent", "✏️ NFC Write mode: $enabled")
        _isWriteMode.value = enabled
    }

    fun isInWriteMode(): Boolean = _isWriteMode.value
}
