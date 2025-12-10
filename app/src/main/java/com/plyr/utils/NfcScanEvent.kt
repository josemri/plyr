package com.plyr.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Evento global para comunicar resultados de escaneo NFC
 * Similar a SpotifyAuthEvent, permite que cualquier componente observe cuando se escanea un NFC
 */
object NfcScanEvent {
    private val _scanResult = MutableStateFlow<NfcScanResult?>(null)
    val scanResult: StateFlow<NfcScanResult?> = _scanResult.asStateFlow()

    fun onNfcScanned(result: NfcScanResult) {
        android.util.Log.d("NfcScanEvent", "🏷️ NFC Scanned - source: ${result.source}, type: ${result.type}, id: ${result.id}")
        _scanResult.value = result
    }

    fun consumeResult(): NfcScanResult? {
        val result = _scanResult.value
        _scanResult.value = null
        return result
    }

    fun clear() {
        _scanResult.value = null
    }
}

