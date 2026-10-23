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
     * Directorio de plugins de VLC junto a [libraryDir], si existe y tiene
     * módulos, o `null` si no se encuentran.
     *
     * `libvlc` localiza sus plugins en su ruta compilada, que en cualquier
     * instalación del sistema es la de este directorio; no hace falta
     * decírselo. Se expone solo para avisar cuando faltan: en Debian
     * `libvlc5` (la biblioteca) y `vlc-plugin-base` (los módulos de sonido)
     * son paquetes distintos, y tener el primero sin el segundo significa que
     * el motor arranca pero no puede reproducir nada.
     */
    fun pluginDirOrNull(libraryDir: File): File? {
        val os = System.getProperty("os.name").lowercase()
        val relative = when {
            os.contains("win") -> listOf("plugins", "vlc/plugins")
            // `VLC.app/Contents/MacOS/lib` → `VLC.app/Contents/MacOS/plugins`
            // y, en Homebrew, `<prefix>/lib/vlc/plugins`.
            os.contains("mac") -> listOf("plugins", "../plugins", "vlc/plugins")
            else -> listOf("vlc/plugins")
        }
        return relative
            .map { File(libraryDir, it) }
            .firstOrNull { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
    }

    /**
     * Mensaje para cuando `libvlc` sí está, pero su motor no arranca.
     *
     * Distingue el caso de los plugins, que en Debian cuesta detectar porque
     * la biblioteca y los módulos viajan en paquetes distintos y el error que
     * devuelve VLC por sí solo no lo explica.
     */
    fun failureMessage(libraryDir: File): String = buildString {
        append("No se pudo iniciar el motor de VLC aunque 'libvlc' está instalada.\n")
        if (pluginDirOrNull(libraryDir) == null) {
            append("Faltan sus módulos (plugins), que en Linux van en otro paquete:\n")
            append("  · Debian/Ubuntu:  sudo apt-get install libvlc5 vlc-plugin-base\n")
            append("  · Fedora:         sudo dnf install vlc\n")
            append("  · macOS:          brew install --cask vlc\n")
            append("  · Windows:        https://www.videolan.org/vlc/\n")
        } else {
            append(missingMessage())
        }
    }

    /** Rutas de carga para JNA: el directorio de `libvlc` y su subdirectorio `vlc/`. */
    fun librarySearchPath(libraryDir: File): String =
        listOf(libraryDir.absolutePath, File(libraryDir, "vlc").absolutePath).joinToString(File.pathSeparator)

    /** Mensaje claro para cuando falta `libvlc`, con cómo instalarlo. */
    fun missingMessage(): String = buildString {
        append("No se encontró 'libvlc' (el motor nativo de VLC).\n")
        append("Instálalo con tu gestor de paquetes, por ejemplo:\n")
        // Solo la biblioteca y los módulos: `vlc` añade además la aplicación
        // gráfica completa, con muchas más dependencias de las necesarias.
        append("  · Debian/Ubuntu:  sudo apt-get install libvlc5 vlc-plugin-base\n")
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
