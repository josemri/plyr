package com.plyr.desktop.feed

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.i18n.Translations
import com.plyr.model.Group
import com.plyr.model.Recommendation
import com.plyr.utils.MediaMetadata
import com.plyr.utils.MediaType
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
        extractMetadata: suspend (String) -> MediaMetadata =
            { url -> MediaMetadata(url, null, null, MediaType.UNKNOWN) },
    ) = FeedController(
        scope = scope,
        remote = FeedRemote(
            loadGroups = { groups },
            loadRecommendations = { groupId -> recommendations(groupId) },
            createRecommendation = { groupId, nickname, url, comment -> created(groupId, nickname, url, comment) },
            extractMetadata = extractMetadata,
        ),
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
            remote = FeedRemote(loadGroups = { error("sin red") }),
            persistNickname = {},
        )

        controller.refresh()

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertTrue(state.statusIsError)
        assertEquals(Translations.get("feed_error"), state.statusMessage)
    }

    // --- M23: metadatos del vídeo ---

    @Test
    fun refreshEnriqueceCadaRecomendacionConSusMetadatos() {
        val controller = controller(
            extractMetadata = { url ->
                MediaMetadata("Título de $url", "Canal", "https://img/$url", MediaType.YOUTUBE_VIDEO)
            },
        )

        controller.refresh()

        val metadata = controller.state.value.metadata["r1"]
        assertEquals("Título de https://youtu.be/r1", metadata?.title)
        assertEquals("Canal", metadata?.author)
        assertEquals(MediaType.YOUTUBE_VIDEO, metadata?.type)
    }

    @Test
    fun noReextraeLosMetadatosYaCacheados() {
        val calls = AtomicInteger(0)
        val controller = controller(
            extractMetadata = { url ->
                calls.incrementAndGet()
                MediaMetadata(url, null, null, MediaType.YOUTUBE_VIDEO)
            },
        )

        controller.refresh()
        controller.refresh()

        assertEquals(1, calls.get())
    }

    @Test
    fun unFalloDeMetadatosDejaLaRecomendacionSinEnriquecer() {
        val controller = controller(extractMetadata = { error("sin red") })

        controller.refresh()

        val state = controller.state.value
        assertEquals(1, state.recommendations.size)
        assertTrue(state.metadata.isEmpty())
    }

    @Test
    fun acotaLaConcurrenciaDeLasExtracciones() = runBlocking {
        val realScope = CoroutineScope(Dispatchers.Default)
        val recommendations = (1..10).map { recommendation("r$it", general.id) }
        val active = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val started = AtomicInteger(0)
        val firstBatchStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = FeedController(
            scope = realScope,
            remote = FeedRemote(
                loadGroups = { listOf(general) },
                loadRecommendations = { recommendations },
                extractMetadata = { url ->
                    val now = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    if (started.incrementAndGet() == METADATA_CONCURRENCY) firstBatchStarted.complete(Unit)
                    release.await()
                    active.decrementAndGet()
                    MediaMetadata(url, null, null, MediaType.UNKNOWN)
                },
            ),
            initialNickname = "",
            persistNickname = {},
        )

        controller.refresh()
        withTimeout(5_000) { firstBatchStarted.await() }
        // Mientras no se libera el primer lote no puede haber más de
        // METADATA_CONCURRENCY extracciones simultáneas.
        assertEquals(METADATA_CONCURRENCY, peak.get())

        release.complete(Unit)
        withTimeout(5_000) {
            while (controller.state.value.metadata.size < recommendations.size) delay(10)
        }
        assertTrue(peak.get() <= METADATA_CONCURRENCY)
        assertEquals(recommendations.size, controller.state.value.metadata.size)
    }
}
