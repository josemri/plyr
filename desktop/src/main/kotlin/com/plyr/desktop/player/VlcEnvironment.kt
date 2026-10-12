package com.plyr.desktop.player

import java.io.File

/**
 * Localiza el runtime nativo de VLC (`libvlc`) para el adaptador de escritorio.
 *
 * VLCJ es solo el *binding* JNA: el reproductor real es `libvlc`, que hay que
 * tener instalado en el sistema (`vlc`/`libvlc`). Esta clase resuelve dónde
 * está y construye los argumentos que VLCJ necesita cuando no está en una ruta
 * estándar (por ejemplo, un VLC extraído a mano en `/tmp` para pruebas).
 */
object VlcEnvironment {

    /** No se encontró `libvlc` en el sistema. */
    class LibVlcMissingException(
        message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)

    /** Variable de entorno para forzar el directorio de `libvlc` en pruebas. */
    const val DIR_ENV = "PLYR_VLC_DIR"

    /**
     * Directorio que contiene `libvlc` (y, si existe, `vlc/plugins`).
     *
     * Prioridad: [DIR_ENV] (override explícito) y, si no, las ubicaciones
     * estándar del sistema operativo. Devuelve `null` si no se encuentra.
     */
    fun findLibraryDir(): File? {
        System.getenv(DIR_ENV)?.let { path ->
            val dir = File(path)
            if (containsLibVlc(dir)) return dir
        }
        return standardCandidates().firstOrNull { containsLibVlc(it) }
    }

    /** `true` si [dir] contiene una `libvlc` nativa. */
    fun containsLibVlc(dir: File): Boolean {
        val names = dir.list() ?: return false
        return names.any {
            it.startsWith("libvlc.so") || it == "libvlc.dylib" || it == "libvlc.dll"
        }
    }

    /**
     * Argumentos extra para `MediaPlayerFactory` cuando el runtime no está en
     * una ruta estándar: apunta a los plugins de VLC del propio directorio.
     */
    fun factoryArgs(libraryDir: File): List<String> {
        val pluginDir = File(libraryDir, "vlc/plugins")
        return if (pluginDir.isDirectory) listOf("--plugin-path=${pluginDir.absolutePath}") else emptyList()
    }

    /** Rutas de carga para JNA: el directorio de `libvlc` y su subdirectorio `vlc/`. */
    fun librarySearchPath(libraryDir: File): String =
        listOf(libraryDir.absolutePath, File(libraryDir, "vlc").absolutePath).joinToString(File.pathSeparator)

    /** Mensaje claro para cuando falta `libvlc`, con cómo instalarlo. */
    fun missingMessage(): String = buildString {
        append("No se encontró 'libvlc' (el motor nativo de VLC).\n")
        append("Instálalo con tu gestor de paquetes, por ejemplo:\n")
        append("  · Debian/Ubuntu:  sudo apt-get install vlc\n")
        append("  · Fedora:         sudo dnf install vlc\n")
        append("  · macOS:          brew install --cask vlc\n")
        append("  · Windows:        https://www.videolan.org/vlc/\n")
        append("Si VLC está en una ruta no estándar, exporta $DIR_ENV=/ruta/a/libvlc.")
    }

    private fun standardCandidates(): List<File> {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("linux") -> listOf(
                "/usr/lib/x86_64-linux-gnu",
                "/usr/lib/aarch64-linux-gnu",
                "/usr/lib",
                "/usr/lib64",
                "/usr/local/lib",
            ).map(::File)
            os.contains("mac") -> listOf(
                "/Applications/VLC.app/Contents/MacOS/lib",
                "/opt/homebrew/lib",
                "/usr/local/lib",
            ).map(::File)
            os.contains("win") -> listOfNotNull(
                System.getenv("ProgramFiles")?.let { File(it, "VideoLAN/VLC") },
                System.getenv("ProgramFiles(x86)")?.let { File(it, "VideoLAN/VLC") },
            )
            else -> emptyList()
        }
    }
}
