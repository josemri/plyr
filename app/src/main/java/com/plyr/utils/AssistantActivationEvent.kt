package com.plyr.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Evento global para activar el asistente de voz desde cualquier parte de la app.
 * Usado principalmente para la activación por shake.
 */
object AssistantActivationEvent {
    private val _activationRequested = MutableStateFlow(false)
    val activationRequested: StateFlow<Boolean> = _activationRequested

    /**
     * Solicita la activación del asistente de voz.
     */
    fun requestActivation() {
        _activationRequested.value = true
    }

    /**
     * Consume el evento de activación (debe llamarse después de procesarlo).
     */
    fun consumeActivation() {
        _activationRequested.value = false
    }
}

