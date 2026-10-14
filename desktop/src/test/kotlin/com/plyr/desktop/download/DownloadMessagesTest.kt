package com.plyr.desktop.download

import com.plyr.desktop.config.DesktopConfig
import com.plyr.desktop.config.Language
import com.russhwolf.settings.MapSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Mensajes (traducidos) de la UI de descarga offline de M17: el estado de la
 * barra de descarga y el resumen del diálogo de almacén. Son texto de usuario,
 * así que se comprueban con un idioma fijo.
 */
class DownloadMessagesTest {

    @Before
    fun setUp() {
        DesktopConfig.installSettings(MapSettings())
        DesktopConfig.language = Language.ENGLISH
    }

    @Test
    fun sinDescargaNiResultadoNoHayMensaje() {
        assertEquals("", downloadStatusMessage(DownloadState()))
    }

    @Test
    fun duranteLaDescargaMuestraElRecuentoActual() {
        val state = DownloadState(isDownloading = true, completed = 1, total = 4, progress = 0.4f)

        assertEquals("downloading 2/4", downloadStatusMessage(state))
    }

    @Test
    fun elResultadoReflejaElDesenlace() {
        assertEquals(
            "downloaded 3/3",
            downloadStatusMessage(DownloadState(outcome = DownloadOutcome.DOWNLOADED, completed = 3, total = 3)),
        )
        assertEquals(
            "downloaded 1/2 (with errors)",
            downloadStatusMessage(DownloadState(outcome = DownloadOutcome.PARTIAL, completed = 1, total = 2)),
        )
        assertEquals(
            "already downloaded",
            downloadStatusMessage(DownloadState(outcome = DownloadOutcome.ALREADY_DOWNLOADED)),
        )
        assertEquals(
            "download cancelled",
            downloadStatusMessage(DownloadState(outcome = DownloadOutcome.CANCELLED)),
        )
    }

    @Test
    fun elResumenDelAlmacenDistingueCargandoVacioYOcupado() {
        assertEquals("loading...", offlineStorageMessage(null))
        assertEquals("no downloaded audio", offlineStorageMessage(DownloadedAudioStore.Summary(0, 0)))
        assertEquals(
            "audio: 3 tracks · 1.0 KB",
            offlineStorageMessage(DownloadedAudioStore.Summary(3, 1024)),
        )
    }
}
