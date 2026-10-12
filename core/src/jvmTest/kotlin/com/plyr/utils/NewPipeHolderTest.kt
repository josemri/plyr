package com.plyr.utils

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
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
 * `NewPipeHolder` es un singleton de proceso (NewPipe.init() solo admite una
 * llamada), así que estos tests no asumen estado limpio: comprueban que tras
 * inicializar, las llamadas posteriores son idempotentes y no reemplazan el
 * `Downloader` ya instalado. El estado "sin inicializar" no es verificable aquí
 * porque otro test del mismo JVM (p. ej. el smoke test de red) puede haber
 * inicializado NewPipe antes.
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
    fun init_secondCallIsIdempotentAndKeepsFirstDownloader() {
        NewPipeHolder.ensureInitialized(fakeDownloader(), Localization("es", "ES"))
        val installed = NewPipe.getDownloader()

        // Un segundo init (con otro Downloader/idioma) no debe reemplazarlo.
        NewPipeHolder.ensureInitialized(fakeDownloader(), Localization("en", "US"))

        assertSame(installed, NewPipe.getDownloader())
        assertTrue(NewPipeHolder.isInitialized())
    }
}
