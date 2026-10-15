package com.plyr.desktop.qr

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.i18n.Translations
import com.plyr.desktop.ui.theme.PlyrTheme
import com.plyr.ui.components.ShareType
import com.plyr.ui.components.ShareUrlPolicy
import com.plyr.ui.components.ShareableItem
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Diálogo de compartir (M25): el botón «recomendar» publica la URL compartida en
 * el feed con el [RecommendationSender] inyectado y muestra
 * `recommendation_sent`/`recommendation_error`; sin URL no aparece.
 */
@OptIn(ExperimentalTestApi::class)
class QrShareDialogTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
    }

    private val trackItem = ShareableItem(
        remoteId = "v1",
        shareUrl = null,
        youtubeId = "dQw4w9WgXcQ",
        title = "Canción",
        artist = "Artista",
        type = ShareType.TRACK,
    )

    private val expectedUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test
    fun recomendarPublicaLaUrlYAviseDeExito() = runComposeUiTest {
        val enviadas = mutableListOf<String>()
        val sender = RecommendationSender { url ->
            enviadas += url
            true
        }

        setContent {
            PlyrTheme { QrShareDialog(item = trackItem, onDismiss = {}, recommend = sender) }
        }

        onNodeWithTag("qr_recommend").performClick()

        onNodeWithText(Translations.get("recommendation_sent")).assertIsDisplayed()
        assertEquals(listOf(ShareUrlPolicy.resolve(ShareType.TRACK, shareUrl = null, youtubeId = "dQw4w9WgXcQ")), enviadas)
    }

    @Test
    fun recomendarAvisaDelError() = runComposeUiTest {
        setContent {
            PlyrTheme {
                QrShareDialog(item = trackItem, onDismiss = {}, recommend = RecommendationSender { false })
            }
        }

        onNodeWithTag("qr_recommend").performClick()

        onNodeWithText(Translations.get("recommendation_error")).assertIsDisplayed()
    }

    @Test
    fun sinUrlNoHayBotonDeRecomendar() = runComposeUiTest {
        val sinUrl = ShareableItem(
            remoteId = null,
            shareUrl = null,
            youtubeId = null,
            title = "Sin enlace",
            artist = "",
            type = ShareType.TRACK,
        )

        setContent {
            PlyrTheme {
                QrShareDialog(item = sinUrl, onDismiss = {}, recommend = RecommendationSender { true })
            }
        }

        onNodeWithTag("qr_no_url").assertIsDisplayed()
        onNodeWithTag("qr_recommend").assertDoesNotExist()
    }
}
