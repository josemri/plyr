package com.plyr.desktop.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del comprobador de actualizaciones de escritorio (M15). Solo se prueba
 * la parte pura —interpretación de la release y comparación de versiones—, sin
 * salir a la red.
 */
class UpdateCheckerTest {

    private val release = """
        {
          "tag_name": "v1.4.0",
          "name": "plyr 1.4.0",
          "body": "notas de la versión",
          "assets": [
            { "name": "plyr-1.4.0.apk", "browser_download_url": "https://example.com/plyr.apk" },
            { "name": "plyr-1.4.0.deb", "browser_download_url": "https://example.com/plyr.deb" }
          ]
        }
    """.trimIndent()

    @Test
    fun parseaLaVersionYEscogeElInstalableDelSo() {
        val info = UpdateChecker.parseRelease(release, "1.3.0", listOf(".deb", ".rpm"))

        assertEquals("1.4.0", info.latestVersion)
        assertEquals("https://example.com/plyr.deb", info.downloadUrl)
        assertEquals("notas de la versión", info.releaseNotes)
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun sinInstalableParaElSoCaeALaPaginaDeReleases() {
        val info = UpdateChecker.parseRelease(release, "1.3.0", listOf(".msi"))

        assertEquals(UpdateChecker.RELEASES_URL, info.downloadUrl)
    }

    @Test
    fun conLaVersionAlDiaNoHayActualizacion() {
        val info = UpdateChecker.parseRelease(release, "1.4.0", listOf(".deb"))

        assertFalse(info.isUpdateAvailable)
    }

    @Test
    fun elTagNoLegibleSeResuelveDesdeElNombre() {
        val json = """{ "tag_name": "latest", "name": "Release 2.3.1" }"""

        val info = UpdateChecker.parseRelease(json, "1.3.0", listOf(".deb"))

        assertEquals("2.3.1", info.latestVersion)
        assertTrue(info.isUpdateAvailable)
    }

    @Test
    fun sinTagNiNombreLegibleSeQuedaEnLaVersionActual() {
        val json = """{ "tag_name": "", "name": "" }"""

        val info = UpdateChecker.parseRelease(json, "1.3.0", listOf(".deb"))

        assertEquals("1.3.0", info.latestVersion)
        assertFalse(info.isUpdateAvailable)
    }

    @Test
    fun isNewerVersionComparaPorComponentes() {
        assertTrue(UpdateChecker.isNewerVersion("1.3.0", "1.3.1"))
        assertTrue(UpdateChecker.isNewerVersion("1.3.0", "1.10.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.3", "1.3.1"))
        assertFalse(UpdateChecker.isNewerVersion("1.3.0", "1.3.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.10.0", "1.3.0"))
    }
}
