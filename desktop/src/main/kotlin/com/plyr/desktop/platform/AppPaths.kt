package com.plyr.desktop.platform

import java.nio.file.Files
import java.nio.file.Path

/**
 * Carpetas de datos de la app para un SO concreto.
 *
 * Los valores son cadenas (no `Path`) porque así se pueden calcular y comprobar
 * para cualquier SO desde cualquier máquina: `Path.of` interpreta los separadores
 * del SO anfitrión, no los del SO destino.
 */
data class AppDirectories(
    /** Preferencias de usuario (equivalente a `SharedPreferences` en Android). */
    val configDir: String,
    /** Datos que deben persistir (base de datos, ficheros de usuario). */
    val dataDir: String,
    /** Datos regenerables que el SO puede borrar (portadas, temporales). */
    val cacheDir: String,
)

/**
 * Rutas de datos, configuración y caché por sistema operativo.
 *
 * Sigue las convenciones de cada plataforma para que la app se comporte como una
 * aplicación nativa:
 * - **Windows:** `%APPDATA%\plyr` (config y datos) y `%LOCALAPPDATA%\plyr\cache`.
 * - **Linux:** `$XDG_CONFIG_HOME/plyr`, `$XDG_DATA_HOME/plyr` y
 *   `$XDG_CACHE_HOME/plyr`, con los fallbacks de XDG (`~/.config`,
 *   `~/.local/share`, `~/.cache`).
 * - **macOS:** `~/Library/Application Support/plyr` y `~/Library/Caches/plyr`.
 *
 * Es lógica pura: [resolve] recibe el SO, `$HOME` y el entorno, así que se puede
 * testear para los tres sistemas desde cualquiera de ellos. [directories] usa los
 * valores reales del proceso.
 */
object AppPaths {

    const val APP_NAME = "plyr"

    /** Nombre del fichero SQLite con los datos del usuario. */
    const val DATABASE_FILE_NAME = "plyr.db"

    /** Carpetas reales del sistema donde corre la app. */
    val directories: AppDirectories by lazy { resolve() }

    /** Fichero de base de datos, dentro de [directories]`.dataDir`. */
    val databaseFile: String by lazy { Path.of(directories.dataDir, DATABASE_FILE_NAME).toString() }

    /** Crea las tres carpetas si no existen y devuelve la de configuración. */
    fun ensureDirectories(dirs: AppDirectories = directories): AppDirectories {
        listOf(dirs.configDir, dirs.dataDir, dirs.cacheDir).forEach { dir ->
            runCatching { Files.createDirectories(Path.of(dir)) }
        }
        return dirs
    }

    /**
     * Calcula las carpetas para [osName], [home] y [env].
     *
     * @param osName valor de `os.name` (`"Linux"`, `"Windows 11"`, `"Mac OS X"`).
     * @param home valor de `user.home`.
     * @param env acceso al entorno (`APPDATA`, `XDG_*`); por defecto `System.getenv`.
     */
    fun resolve(
        osName: String = System.getProperty("os.name").orEmpty(),
        home: String = System.getProperty("user.home").orEmpty(),
        env: (String) -> String? = { System.getenv(it) },
    ): AppDirectories = when {
        osName.startsWith("Windows", ignoreCase = true) -> windows(home, env)
        osName.startsWith("Mac", ignoreCase = true) ||
            osName.startsWith("Darwin", ignoreCase = true) -> macos(home)
        else -> linux(home, env)
    }

    private fun windows(home: String, env: (String) -> String?): AppDirectories {
        val roaming = env("APPDATA")?.ifBlank { null } ?: "$home\\AppData\\Roaming"
        val local = env("LOCALAPPDATA")?.ifBlank { null } ?: "$home\\AppData\\Local"
        val appData = "$roaming\\$APP_NAME"
        return AppDirectories(
            configDir = appData,
            dataDir = appData,
            cacheDir = "$local\\$APP_NAME\\cache",
        )
    }

    private fun linux(home: String, env: (String) -> String?): AppDirectories {
        val config = env("XDG_CONFIG_HOME")?.ifBlank { null } ?: "$home/.config"
        val data = env("XDG_DATA_HOME")?.ifBlank { null } ?: "$home/.local/share"
        val cache = env("XDG_CACHE_HOME")?.ifBlank { null } ?: "$home/.cache"
        return AppDirectories(
            configDir = "$config/$APP_NAME",
            dataDir = "$data/$APP_NAME",
            cacheDir = "$cache/$APP_NAME",
        )
    }

    private fun macos(home: String): AppDirectories = AppDirectories(
        configDir = "$home/Library/Application Support/$APP_NAME",
        dataDir = "$home/Library/Application Support/$APP_NAME",
        cacheDir = "$home/Library/Caches/$APP_NAME",
    )
}
