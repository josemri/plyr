package com.plyr.desktop.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Rutas de datos/caché por sistema operativo, sin tocar el SO real. */
class AppPathsTest {

    private val noEnv: (String) -> String? = { null }

    @Test
    fun linux_usaXdgConFallback() {
        val dirs = AppPaths.resolve("Linux", "/home/u", noEnv)

        assertEquals("/home/u/.config/plyr", dirs.configDir)
        assertEquals("/home/u/.local/share/plyr", dirs.dataDir)
        assertEquals("/home/u/.cache/plyr", dirs.cacheDir)
    }

    @Test
    fun linux_respetaXdgSiEstaDefinido() {
        val env = mapOf(
            "XDG_CONFIG_HOME" to "/custom/config",
            "XDG_DATA_HOME" to "/custom/data",
            "XDG_CACHE_HOME" to "/custom/cache",
        )

        val dirs = AppPaths.resolve("Linux", "/home/u") { env[it] }

        assertEquals("/custom/config/plyr", dirs.configDir)
        assertEquals("/custom/data/plyr", dirs.dataDir)
        assertEquals("/custom/cache/plyr", dirs.cacheDir)
    }

    @Test
    fun windows_usaAppData() {
        val env = mapOf(
            "APPDATA" to "C:\\Users\\u\\AppData\\Roaming",
            "LOCALAPPDATA" to "C:\\Users\\u\\AppData\\Local",
        )

        val dirs = AppPaths.resolve("Windows 11", "C:\\Users\\u") { env[it] }

        assertEquals("C:\\Users\\u\\AppData\\Roaming\\plyr", dirs.configDir)
        assertEquals("C:\\Users\\u\\AppData\\Roaming\\plyr", dirs.dataDir)
        assertEquals("C:\\Users\\u\\AppData\\Local\\plyr\\cache", dirs.cacheDir)
    }

    @Test
    fun windows_fallbackSinVariables() {
        val dirs = AppPaths.resolve("Windows 10", "C:\\Users\\u", noEnv)

        assertEquals("C:\\Users\\u\\AppData\\Roaming\\plyr", dirs.configDir)
        assertEquals("C:\\Users\\u\\AppData\\Local\\plyr\\cache", dirs.cacheDir)
    }

    @Test
    fun macos_usaLibrary() {
        val dirs = AppPaths.resolve("Mac OS X", "/Users/u", noEnv)

        assertEquals("/Users/u/Library/Application Support/plyr", dirs.configDir)
        assertEquals("/Users/u/Library/Application Support/plyr", dirs.dataDir)
        assertEquals("/Users/u/Library/Caches/plyr", dirs.cacheDir)
    }

    @Test
    fun databaseFile_estaBajoLaCarpetaDeDatos() {
        assertTrue(AppPaths.databaseFile.startsWith(AppPaths.directories.dataDir))
        assertTrue(AppPaths.databaseFile.endsWith(AppPaths.DATABASE_FILE_NAME))
    }

    @Test
    fun ensureDirectories_lasCrea() {
        val base = Files.createTempDirectory("plyr-paths").toFile()
        val dirs = AppDirectories(
            configDir = Path.of(base.path, "config").toString(),
            dataDir = Path.of(base.path, "data").toString(),
            cacheDir = Path.of(base.path, "cache").toString(),
        )

        AppPaths.ensureDirectories(dirs)

        assertTrue(Files.isDirectory(Path.of(dirs.configDir)))
        assertTrue(Files.isDirectory(Path.of(dirs.dataDir)))
        assertTrue(Files.isDirectory(Path.of(dirs.cacheDir)))
    }
}
