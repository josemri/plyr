package com.plyr.desktop.feed

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.i18n.Translations
import com.plyr.model.Group
import com.plyr.model.Recommendation
import com.plyr.network.SupabaseClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del feed de recomendaciones de escritorio (M12).
 *
 * Reúne lo que la pantalla necesita pintar: los grupos disponibles, el grupo
 * seleccionado (por defecto el general, como Android), sus recomendaciones y los
 * campos del formulario para publicar una nueva.
 */
data class FeedState(
    val isLoading: Boolean = false,
    val groups: List<Group> = emptyList(),
    val selectedGroupId: String? = null,
    val recommendations: List<Recommendation> = emptyList(),
    val nickname: String = "",
    val url: String = "",
    val comment: String = "",
    val isPosting: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val nicknameMessage: String? = null,
) {
    /** Grupo seleccionado, o `null` si todavía no hay grupos. */
    val selectedGroup: Group? get() = groups.firstOrNull { it.id == selectedGroupId }

    /** `true` si se puede publicar con los datos actuales. */
    val canPost: Boolean
        get() = !isPosting && selectedGroupId != null && nickname.isNotBlank() && url.isNotBlank()
}

/**
 * Lógica del feed de recomendaciones del escritorio (M12).
 *
 * Port de `FeedScreen`/`FeedConfig` de Android sobre el `SupabaseClient` de
 * `:core`, pero con las dependencias de red inyectadas como funciones para poder
 * testearlo sin red. El apodo se persiste con el mismo contrato que Android
 * ([DesktopConfig.userNickname] bajo la clave `user_nickname`).
 */
class FeedController(
    private val scope: CoroutineScope,
    private val loadGroups: suspend () -> List<Group> = { SupabaseClient.getGroups() },
    private val loadRecommendations: suspend (groupId: String?) -> List<Recommendation> =
        { groupId -> SupabaseClient.getRecommendations(groupId) },
    private val createRecommendation:
        suspend (groupId: String, nickname: String, url: String, comment: String?) -> Recommendation? =
        { groupId, nickname, url, comment -> SupabaseClient.createRecommendation(groupId, nickname, url, comment) },
    initialNickname: String = DesktopConfig.userNickname,
    private val persistNickname: (String) -> Unit = { DesktopConfig.userNickname = it },
) {

    private val _state = MutableStateFlow(FeedState(nickname = initialNickname))
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var postJob: Job? = null

    /**
     * Carga los grupos y las recomendaciones del grupo seleccionado. La primera
     * vez (o si el grupo guardado ya no existe) usa el general, igual que Android.
     */
    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, statusMessage = null, statusIsError = false) }
        loadJob = scope.launch {
            try {
                val groups = loadGroups()
                val selected = resolveGroup(groups, _state.value.selectedGroupId)
                val recommendations = if (selected != null) loadRecommendations(selected) else emptyList()
                _state.update {
                    it.copy(
                        isLoading = false,
                        groups = groups,
                        selectedGroupId = selected,
                        recommendations = recommendations,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, statusMessage = Translations.get("feed_error"), statusIsError = true)
                }
            }
        }
    }

    /** Cambia de grupo y recarga sus recomendaciones. */
    fun selectGroup(groupId: String) {
        if (_state.value.selectedGroupId == groupId) return
        loadJob?.cancel()
        _state.update {
            it.copy(selectedGroupId = groupId, recommendations = emptyList(), isLoading = true, statusMessage = null)
        }
        loadJob = scope.launch {
            try {
                val recommendations = loadRecommendations(groupId)
                _state.update { it.copy(isLoading = false, recommendations = recommendations) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, statusMessage = Translations.get("feed_error"), statusIsError = true)
                }
            }
        }
    }

    fun onNicknameChange(value: String) {
        _state.update { it.copy(nickname = value, nicknameMessage = null) }
    }

    /** Persiste el apodo recortado para futuras recomendaciones. */
    fun saveNickname() {
        val nickname = _state.value.nickname.trim()
        persistNickname(nickname)
        _state.update { it.copy(nickname = nickname, nicknameMessage = Translations.get("nickname_saved")) }
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, statusMessage = null, statusIsError = false) }
    }

    fun onCommentChange(value: String) {
        _state.update { it.copy(comment = value, statusMessage = null, statusIsError = false) }
    }

    /** Publica la recomendación actual en el grupo seleccionado. */
    fun addRecommendation() {
        val current = _state.value
        if (!current.canPost) return
        val groupId = current.selectedGroupId ?: return
        val nickname = current.nickname.trim()
        val url = current.url.trim()
        val comment = current.comment.trim().takeIf { it.isNotEmpty() }

        postJob?.cancel()
        _state.update {
            it.copy(isPosting = true, statusMessage = Translations.get("sending"), statusIsError = false)
        }
        postJob = scope.launch {
            try {
                val created = createRecommendation(groupId, nickname, url, comment)
                if (created != null) {
                    _state.update {
                        it.copy(
                            isPosting = false,
                            url = "",
                            comment = "",
                            recommendations = listOf(created) + it.recommendations,
                            statusMessage = Translations.get("recommendation_sent"),
                            statusIsError = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isPosting = false,
                            statusMessage = Translations.get("recommendation_error"),
                            statusIsError = true,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isPosting = false,
                        statusMessage = Translations.get("recommendation_error"),
                        statusIsError = true,
                    )
                }
            }
        }
    }

    private fun resolveGroup(groups: List<Group>, current: String?): String? =
        current?.takeIf { id -> groups.any { it.id == id } }
            ?: groups.firstOrNull { it.groupType == "general" }?.id
            ?: groups.firstOrNull()?.id
}
