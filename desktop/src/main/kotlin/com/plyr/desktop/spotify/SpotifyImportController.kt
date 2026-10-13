package com.plyr.desktop.spotify

import com.plyr.desktop.i18n.Translations
import com.plyr.utils.SpotifyImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la sección de importación de Spotify de los ajustes. */
data class SpotifyImportState(
    val url: String = "",
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
)

/**
 * Lógica de la sección "importar de spotify" del escritorio (M11).
 *
 * Mantiene el texto introducido, valida la URL con la misma política que Android
 * ([SpotifyImport.extractPlaylistId]) y delega el trabajo en
 * [SpotifyImportService]. El estado es un `StateFlow` para que la UI lo observe.
 */
class SpotifyImportController(
    private val scope: CoroutineScope,
    private val service: SpotifyImportService,
) {

    private val _state = MutableStateFlow(SpotifyImportState())
    val state: StateFlow<SpotifyImportState> = _state.asStateFlow()

    private var job: Job? = null

    fun onUrlChange(url: String) {
        _state.update { it.copy(url = url, statusMessage = null, statusIsError = false) }
    }

    fun import() {
        if (job?.isActive == true) return
        val url = _state.value.url
        if (SpotifyImport.extractPlaylistId(url) == null) {
            _state.update {
                it.copy(statusMessage = Translations.get("spotify_import_invalid"), statusIsError = true)
            }
            return
        }

        job = scope.launch {
            _state.update {
                it.copy(
                    isImporting = true,
                    progress = 0f,
                    statusMessage = Translations.get("spotify_importing"),
                    statusIsError = false,
                )
            }
            val result = service.import(url) { current, total, _ ->
                val progress = if (total > 0) current.toFloat() / total else 0f
                _state.update { it.copy(progress = progress) }
            }
            result.fold(
                onSuccess = { outcome ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            progress = 1f,
                            statusMessage = outcome.message,
                            statusIsError = false,
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            statusMessage = Translations.get("spotify_import_error"),
                            statusIsError = true,
                        )
                    }
                },
            )
        }
    }
}
