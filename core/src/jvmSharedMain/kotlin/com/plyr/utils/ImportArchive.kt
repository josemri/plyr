package com.plyr.utils

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * ImportArchive - Lectura acotada de un ZIP de exportación de plyr.
 *
 * Solo se queda con las entradas que el formato define (`playlists.json` y las
 * portadas de `covers/`), de modo que el resto del archivo se ignora. No usa
 * ninguna clase de Android, así que se puede probar con streams JVM.
 *
 * Los nombres de entrada nunca se usan como rutas de disco (la portada se
 * reescribe con el nombre del propio dispositivo), así que un archivo manipulado
 * no puede escapar de donde sea que se escriba.
 */
object ImportArchive {

    /** Tope por entrada: las portadas exportadas no pasan de unos cientos de KB. */
    const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    /** Tope total descomprimido, para no quedarse sin memoria con un ZIP manipulado. */
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    private const val MAX_ENTRY_NAME_LENGTH = 260
    private const val BUFFER_SIZE = 8 * 1024

    /**
     * Devuelve las entradas del ZIP indexadas por su nombre interno.
     *
     * @throws IOException si el flujo no es un ZIP legible, si no contiene el
     *   manifiesto, o si se superan los límites de tamaño.
     */    @Throws(IOException::class)
    fun read(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var total = 0L

        ZipInputStream(BufferedInputStream(input, BUFFER_SIZE)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    val name = entry.name
                    if (entry.isDirectory || !isRelevantEntry(name)) continue

                    val bytes = readBounded(zip) ?: throw IOException(
                        "La entrada \"$name\" del ZIP supera el límite de ${MAX_ENTRY_BYTES / 1024 / 1024} MB"
                    )
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw IOException(
                            "El ZIP supera el límite de ${MAX_TOTAL_BYTES / 1024 / 1024} MB descomprimidos"
                        )
                    }
                    entries[name] = bytes
                } finally {
                    zip.closeEntry()
                }
            }
        }

        if (!entries.containsKey(ExportManifest.FILE_NAME)) {
            throw IOException(
                "El archivo no contiene \"${ExportManifest.FILE_NAME}\", así que no es una exportación de plyr"
            )
        }
        return entries
    }

    /**
     * Solo interesan el manifiesto y las portadas; el resto se descarta al leer.
     *
     * Se exige la forma exacta del formato: `covers/<nombre>.jpg` y nada de
     * subcarpetas, porque `ExportManifest.coverEntry` nunca las genera. Los
     * nombres con `..` se rechazan aunque no se usen como ruta: un id de playlist
     * real nunca los produce, así que solo aparecen en archivos hechos a mano.
     */
    private fun isRelevantEntry(name: String): Boolean {
        if (name.contains("..")) return false
        if (name == ExportManifest.FILE_NAME) return true
        if (name.length > MAX_ENTRY_NAME_LENGTH) return false

        val prefix = "${ExportManifest.COVERS_DIR}/"
        if (!name.startsWith(prefix)) return false
        val coverName = name.removePrefix(prefix)
        return coverName.isNotEmpty() && !coverName.contains('/')
    }

    /**
     * Lee la entrada actual hasta [MAX_ENTRY_BYTES]. Devuelve null si se pasó del
     * límite, para no acumular memoria de más.
     */
    private fun readBounded(zip: ZipInputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = zip.read(buffer)
            if (read == -1) return out.toByteArray()
            if (out.size() + read > MAX_ENTRY_BYTES) return null
            out.write(buffer, 0, read)
        }
    }
}
