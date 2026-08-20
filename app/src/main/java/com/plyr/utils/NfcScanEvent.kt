package com.plyr.utils

import com.plyr.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Evento global para comunicar resultados de escaneo NFC
 * Permite que cualquier componente observe cuando se escanea un NFC
 */
object NfcScanEvent {
    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    fun onNfcScanned(result: ScanResult) {
        android.util.Log.d("NfcScanEvent", "🏷️ NFC Scanned - source: ${result.source}, type: ${result.type}, id: ${result.id}")
        _scanResult.value = result
    }

    fun consumeResult(): ScanResult? {
        val result = _scanResult.value
        _scanResult.value = null
        return result
    }

    fun clear() {
        _scanResult.value = null
    }
}

