package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plyr.utils.SpotifyImporter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    private var importJob: Job? = null

    fun startImport(playlistUri: String) {
        if (_isImporting.value) return

        _isImporting.value = true
        _progress.value = 0f
        _message.value = ""
        _resultMessage.value = null

        importJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val result = SpotifyImporter.importPlaylistByUri(
                context = context,
                playlistUri = playlistUri,
                onProgress = { current, total, msg ->
                    _progress.value = if (total > 0) current.toFloat() / total else 0f
                    _message.value = msg
                }
            )
            _isImporting.value = false
            _resultMessage.value = result.fold(
                onSuccess = { it.message },
                onFailure = { "error: ${it.message}" }
            )
        }
    }

    fun dismissResult() {
        _resultMessage.value = null
    }
}
