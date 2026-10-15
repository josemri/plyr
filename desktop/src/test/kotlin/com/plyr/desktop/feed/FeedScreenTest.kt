package com.plyr.desktop.feed

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.theme.PlyrTheme
import com.plyr.model.Group
import com.plyr.model.Recommendation
import com.plyr.utils.MediaMetadata
import com.plyr.utils.MediaType
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de UI del feed (M12). El [FeedController] se construye con Supabase
 * inyectado, así que no hay red; se comprueba el listado, el estado vacío, el
 * alta de una recomendación y el botón de volver.
 */
@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {

    private var savedLanguage = Language.ENGLISH
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        savedLanguage = DesktopConfig.language
        DesktopConfig.language = Language.ENGLISH
        scope = CoroutineScope(Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        DesktopConfig.language = savedLanguage
    }

    private val general = Group(id = "g-general", name = "General", groupType = "general")

    private fun controller(
        groups: List<Group> = listOf(general),
        recommendations: List<Recommendation> = emptyList(),
        created: (String, String, String, String?) -> Recommendation? = { groupId, nickname, url, comment ->
            Recommendation(id = "new", groupId = groupId, nickname = nickname, url = url, comment = comment)
        },
        initialNickname: String = "ana",
        extractMetadata: suspend (String) -> MediaMetadata =
            { url -> MediaMetadata(url, null, null, MediaType.UNKNOWN) },
    ) = FeedController(
        scope = scope,
        remote = FeedRemote(
            loadGroups = { groups },
            loadRecommendations = { recommendations },
            createRecommendation = { groupId, nickname, url, comment -> created(groupId, nickname, url, comment) },
            extractMetadata = extractMetadata,
        ),
        initialNickname = initialNickname,
        persistNickname = {},
    )

    @Test
    fun muestraLasRecomendaciones() = runComposeUiTest {
        val controller = controller(
            recommendations = listOf(
                Recommendation(id = "r1", groupId = general.id, nickname = "ana", url = "https://youtu.be/r1"),
            ),
        )
        setContent { PlyrTheme { FeedScreen(controller = controller, onBack = {}) } }

        onNodeWithTag("recommendation_r1").assertIsDisplayed()
    }

    @Test
    fun estadoVacioMuestraElMensaje() = runComposeUiTest {
        setContent { PlyrTheme { FeedScreen(controller = controller(), onBack = {}) } }

        onNodeWithText(Translations.get("no_recommendations")).assertIsDisplayed()
    }

    @Test
    fun publicaUnaRecomendacion() = runComposeUiTest {
        var published = false
        val controller = controller(created = { groupId, nickname, url, comment ->
            published = true
            Recommendation(id = "new", groupId = groupId, nickname = nickname, url = url, comment = comment)
        })
        setContent { PlyrTheme { FeedScreen(controller = controller, onBack = {}) } }

        onNodeWithTag("feed_url").performTextInput("https://youtu.be/xyz")
        onNodeWithTag("feed_post").performClick()

        assertTrue(published)
        onNodeWithTag("recommendation_new").assertIsDisplayed()
    }

    @Test
    fun elBotonVolverInvocaElCallback() = runComposeUiTest {
        var back = false
        setContent { PlyrTheme { FeedScreen(controller = controller(), onBack = { back = true }) } }

        onNodeWithTag("back_from_FEED").performClick()

        assertEquals(true, back)
    }

    // --- M23: metadatos del vídeo y click por tipo ---

    @Test
    fun muestraElTituloYElAutorExtraidos() = runComposeUiTest {
        val controller = controller(
            recommendations = listOf(recommendation()),
            extractMetadata = { url ->
                MediaMetadata("Mi vídeo", "Canal de $url", "https://img/$url", MediaType.YOUTUBE_VIDEO)
            },
        )
        setContent { PlyrTheme { FeedScreen(controller = controller, onBack = {}) } }

        onNodeWithText("Mi vídeo").assertIsDisplayed()
        onNodeWithText("Canal de https://youtu.be/r1").assertIsDisplayed()
    }

    @Test
    fun alPulsarUnVideoLoReproduce() = runComposeUiTest {
        var played: Triple<String, String, String>? = null
        val controller = controller(
            recommendations = listOf(recommendation()),
            extractMetadata = { _ -> MediaMetadata("Mi vídeo", "Canal", null, MediaType.YOUTUBE_VIDEO) },
        )
        setContent {
            PlyrTheme {
                FeedScreen(
                    controller = controller,
                    onBack = {},
                    onPlayVideo = { videoId, title, artist -> played = Triple(videoId, title, artist) },
                )
            }
        }

        onNodeWithTag("recommendation_r1").performClick()

        assertEquals(Triple("r1", "Mi vídeo", "Canal"), played)
    }

    @Test
    fun alPulsarUnaPlaylistLaAbreEnLaBusqueda() = runComposeUiTest {
        var opened: String? = null
        var played = false
        val controller = controller(
            recommendations = listOf(
                Recommendation(
                    id = "r1",
                    groupId = general.id,
                    nickname = "ana",
                    url = "https://www.youtube.com/playlist?list=PL123",
                ),
            ),
            extractMetadata = { _ -> MediaMetadata("Mi lista", "Canal", null, MediaType.YOUTUBE_PLAYLIST) },
        )
        setContent {
            PlyrTheme {
                FeedScreen(
                    controller = controller,
                    onBack = {},
                    onPlayVideo = { _, _, _ -> played = true },
                    onOpenPlaylist = { opened = it },
                )
            }
        }

        onNodeWithTag("recommendation_r1").performClick()

        assertEquals("PL123", opened)
        assertFalse(played)
    }

    private fun recommendation() = Recommendation(
        id = "r1",
        groupId = general.id,
        nickname = "ana",
        url = "https://youtu.be/r1",
    )
}
