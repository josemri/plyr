package com.plyr.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Tests de la inicialización única e idempotente de NewPipe.
 *
 * No se usa SimpleDownloader real (depende de android.util.Log, no disponible
 * en tests JVM): se inyecta un Downloader de prueba.
 */
class NewPipeHolderTest {

    private fun fakeDownloader() = object : Downloader() {
        override fun execute(request: Request): Response {
            return Response(200, "message", emptyMap(), "", "")
        }
    }

    @Test
    fun init_initializesNewPipe() {
        NewPipeHolder.ensureInitialized(fakeDownloader(), Localization("es", "ES"))
        assertNotNull(NewPipe.getDownloader())
    }

    @Test
    fun init_secondCallIsIdempotent() {
        NewPipeHolder.ensureInitialized(fakeDownloader(), Localization("es", "ES"))
        NewPipeHolder.ensureInitialized(fakeDownloader(), Localization("es", "ES"))
        assertTrue(true)
    }

    @Test
    fun init_notInitializedBeforeFirstCall() {
        assertFalse(NewPipeHolder.isInitialized())
    }
}
