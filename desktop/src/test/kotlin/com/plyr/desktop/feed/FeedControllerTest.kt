package com.plyr.desktop.feed

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.i18n.Translations
import com.plyr.model.Group
import com.plyr.model.Recommendation
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [FeedController] con Supabase inyectado: sin red y con el estado
 * resuelto de forma síncrona (`Dispatchers.Unconfined`).
 */
class FeedControllerTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        scope = CoroutineScope(Dispatchers.Unconfined)
    }

    private val general = Group(id = "g-general", name = "General", groupType = "general")
    private val privateGroup = Group(id = "g-private", name = "Amigos", groupType = "private")

    private fun recommendation(id: String, groupId: String) = Recommendation(
        id = id,
        groupId = groupId,
        nickname = "ana",
        url = "https://youtu.be/$id",
        comment = null,
        createdAt = System.currentTimeMillis(),
    )

    private fun controller(
        groups: List<Group> = listOf(general, privateGroup),
        recommendations: (String?) -> List<Recommendation> = { groupId ->
            listOf(recommendation("r1", groupId.orEmpty()))
        },
        created: (String, String, String, String?) -> Recommendation? =
            { groupId, nickname, url, comment ->
                Recommendation(
                    id = "new",
                    groupId = groupId,
                    nickname = nickname,
                    url = url,
                    comment = comment,
                )
            },
        initialNickname: String = "",
        persist: (String) -> Unit = {},
    ) = FeedController(
        scope = scope,
        loadGroups = { groups },
        loadRecommendations = { groupId -> recommendations(groupId) },
        createRecommendation = { groupId, nickname, url, comment -> created(groupId, nickname, url, comment) },
        initialNickname = initialNickname,
        persistNickname = persist,
    )

    @Test
    fun refreshSeleccionaElGrupoGeneralYSuLista() {
        val controller = controller()

        controller.refresh()

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(general, privateGroup), state.groups)
        assertEquals(general.id, state.selectedGroupId)
        assertEquals(listOf("r1"), state.recommendations.map { it.id })
    }

    @Test
    fun refreshSinGruposDejaElEstadoVacio() {
        val controller = controller(groups = emptyList())

        controller.refresh()

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertTrue(state.groups.isEmpty())
        assertNull(state.selectedGroupId)
        assertTrue(state.recommendations.isEmpty())
    }

    @Test
    fun selectGroupRecargaSusRecomendaciones() {
        val controller = controller(recommendations = { groupId ->
            listOf(recommendation("rec-$groupId", groupId.orEmpty()))
        })
        controller.refresh()

        controller.selectGroup(privateGroup.id)

        val state = controller.state.value
        assertEquals(privateGroup.id, state.selectedGroupId)
        assertEquals(listOf("rec-${privateGroup.id}"), state.recommendations.map { it.id })
    }

    @Test
    fun addRecommendationPublicaRecortaYAntepone() {
        var received: List<String?>? = null
        val controller = controller(created = { groupId, nickname, url, comment ->
            received = listOf(groupId, nickname, url, comment)
            Recommendation(
                id = "new",
                groupId = groupId,
                nickname = nickname,
                url = url,
                comment = comment,
            )
        })
        controller.refresh()
        controller.onNicknameChange("  ana  ")
        controller.onUrlChange("  https://youtu.be/xyz  ")
        controller.onCommentChange("  buena  ")

        controller.addRecommendation()

        val state = controller.state.value
        assertFalse(state.isPosting)
        assertEquals(listOf(general.id, "ana", "https://youtu.be/xyz", "buena"), received)
        assertEquals("new", state.recommendations.first().id)
        assertEquals("", state.url)
        assertEquals("", state.comment)
        assertEquals(Translations.get("recommendation_sent"), state.statusMessage)
        assertFalse(state.statusIsError)
    }

    @Test
    fun addRecommendationSinUrlNoPublica() {
        var called = false
        val controller = controller(created = { _, _, _, _ ->
            called = true
            null
        })
        controller.refresh()
        controller.onNicknameChange("ana")

        controller.addRecommendation()

        assertFalse(called)
        assertFalse(controller.state.value.isPosting)
    }

    @Test
    fun addRecommendationFallidaMuestraError() {
        val controller = controller(created = { _, _, _, _ -> null })
        controller.refresh()
        controller.onNicknameChange("ana")
        controller.onUrlChange("https://youtu.be/xyz")

        controller.addRecommendation()

        assertTrue(controller.state.value.statusIsError)
        assertFalse(controller.state.value.isPosting)
        assertEquals(Translations.get("recommendation_error"), controller.state.value.statusMessage)
    }

    @Test
    fun saveNicknamePersisteYRecorta() {
        var persisted: String? = null
        val controller = controller(persist = { persisted = it })
        controller.onNicknameChange("  ana  ")

        controller.saveNickname()

        assertEquals("ana", persisted)
        assertEquals("ana", controller.state.value.nickname)
        assertEquals(Translations.get("nickname_saved"), controller.state.value.nicknameMessage)
    }

    @Test
    fun refreshFallidoMuestraError() {
        val controller = FeedController(
            scope = scope,
            loadGroups = { error("sin red") },
            persistNickname = {},
        )

        controller.refresh()

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertTrue(state.statusIsError)
        assertEquals(Translations.get("feed_error"), state.statusMessage)
    }
}
