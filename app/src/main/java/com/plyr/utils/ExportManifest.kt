package com.plyr.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Una pista tal y como se serializa en `playlists.json`.
 */
data class ExportTrack(
    val position: Int,
    val name: String,
    val artists: List<String>,
    val remoteTrackId: String,
    val youtubeVideoId: String?
)

/**
 * Una lista tal y como se serializa en `playlists.json`. [coverEntry] es la ruta
 * interna de la portada dentro del ZIP (`covers/<id>.jpg`) o null si la lista no
 * tiene portada o no se pudo incrustar.
 */
data class ExportPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val coverEntry: String?,
    val tracks: List<ExportTrack>
) {
    val trackCount: Int get() = tracks.size
}

/**
 * ExportManifest - Serialización *pura* del archivo `playlists.json` que acompaña
 * al ZIP de exportación, además del nombre de las entradas internas.
 *
 * No depende de `org.json` ni de ninguna clase de Android a propósito: así el
 * formato se puede validar con tests JVM (ver `ExportManifestTest`).
 * El ZIP en sí lo escribe `DataExporter`.
 */
object ExportManifest {

    /** Versión del formato; incrementarla si cambia la estructura del JSON. */
    const val FORMAT_VERSION = 1

    /** Nombre de la app, para identificar el origen del archivo. */
    const val APP_NAME = "_plyr"

    /** Entrada del manifiesto dentro del ZIP. */
    const val FILE_NAME = "playlists.json"

    /** Carpeta interna donde viven las portadas del ZIP. */
    const val COVERS_DIR = "covers"

    /** MIME type para el selector de archivos (SAF). */
    const val ZIP_MIME_TYPE = "application/zip"

    /**
     * MIME types para *abrir* un ZIP. No todos los exploradores identifican bien
     * un .zip y lo dejan como binario genérico, así que se acepta también
     * `application/octet-stream` para que el archivo sea alcanzable.
     */
    val ZIP_MIME_TYPES = arrayOf(ZIP_MIME_TYPE, "application/octet-stream")

    private const val FILE_NAME_PREFIX = "plyr-export-"
    private const val FILE_NAME_DATE_PATTERN = "yyyy-MM-dd_HH-mm"
    private const val FALLBACK_ENTRY_NAME = "playlist"
    private const val MAX_ENTRY_NAME_LENGTH = 64

    /**
     * Nombre de archivo sugerido para el selector del sistema, con la fecha para
     * no sobrescribir exportaciones anteriores.
     */
    fun suggestedFileName(timestampMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat(FILE_NAME_DATE_PATTERN, Locale.US).format(Date(timestampMillis))
        return "$FILE_NAME_PREFIX$stamp.zip"
    }

    /**
     * Ruta interna de la portada de [playlistId] dentro del ZIP. El id se sanea
     * para que nunca pueda escapar de [COVERS_DIR].
     */
    fun coverEntry(playlistId: String): String = "$COVERS_DIR/${sanitizeEntryName(playlistId)}.jpg"

    /**
     * Como [coverEntry], pero garantiza que la ruta devuelta no esté ya en
     * [taken] (la añade). Dos ids distintos pueden sanearse al mismo texto
     * (`a/b` y `a_b`), y `ZipOutputStream` aborta ante entradas duplicadas, así
     * que aquí se resuelve con un sufijo numérico.
     */
    fun uniqueCoverEntry(playlistId: String, taken: MutableSet<String>): String {
        val base = coverEntry(playlistId)
        if (taken.add(base)) return base

        val stem = base.removeSuffix(".jpg")
        var index = 2
        while (!taken.add("$stem-$index.jpg")) index++
        return "$stem-$index.jpg"
    }

    /**
     * Convierte [name] en un segmento de ruta seguro: fuera todo lo que no sea
     * alfanumérico, `.`, `_` o `-`, y se recorta a [MAX_ENTRY_NAME_LENGTH].
     * Los nombres vacíos (o `.`/`..`) caen a [FALLBACK_ENTRY_NAME].
     */
    fun sanitizeEntryName(name: String): String {
        val cleaned = buildString(name.length) {
            for (ch in name) {
                append(
                    if (ch.isLetterOrDigit() && ch.code < 128) ch
                    else if (ch == '.' || ch == '_' || ch == '-') ch
                    else '_'
                )
            }
        }.take(MAX_ENTRY_NAME_LENGTH)

        return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") {
            FALLBACK_ENTRY_NAME
        } else {
            cleaned
        }
    }

    /**
     * Separa la cadena de artistas que Room guarda como `"A, B, C"` en la lista
     * que realmente se serializa. Mismo criterio que `DatabaseExtensions.toAppTrack`.
     */
    fun parseArtists(artists: String): List<String> =
        artists.split(", ").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Escapa [value] para incrustarla entre comillas en un documento JSON.
     * Los caracteres de control que no tienen secuencia corta (ver RFC 8259) se
     * escapan con su código Unicode.
     */
    fun escape(value: String): String = buildString(value.length + 8) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> when (ch) {
                    '\u0008' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
        }
    }

    /** Igual que [escape] pero un valor null sale como literal `null`. */
    fun jsonString(value: String?): String = if (value == null) "null" else "\"${escape(value)}\""

    /**
     * Construye el documento `playlists.json` completo. El resultado es estable
     * (mismos datos → mismo texto), lo que facilita comparar exportaciones.
     */
    fun build(
        appVersion: String,
        exportedAt: Long,
        playlists: List<ExportPlaylist>
    ): String {
        val trackCount = playlists.sumOf { it.tracks.size }
        return buildString {
            appendLine("{")
            appendLine("  \"app\": ${jsonString(APP_NAME)},")
            appendLine("  \"formatVersion\": $FORMAT_VERSION,")
            appendLine("  \"appVersion\": ${jsonString(appVersion)},")
            appendLine("  \"exportedAt\": $exportedAt,")
            appendLine("  \"playlistCount\": ${playlists.size},")
            appendLine("  \"trackCount\": $trackCount,")
            append("  \"playlists\": [")
            if (playlists.isNotEmpty()) appendLine()
            playlists.forEachIndexed { index, playlist ->
                appendPlaylist(this, playlist, isLast = index == playlists.lastIndex)
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    // === HELPERS DE SERIALIZACIÓN ===

    private fun appendPlaylist(sb: StringBuilder, playlist: ExportPlaylist, isLast: Boolean) {
        sb.appendLine("    {")
        sb.appendLine("      \"id\": ${jsonString(playlist.id)},")
        sb.appendLine("      \"name\": ${jsonString(playlist.name)},")
        sb.appendLine("      \"description\": ${jsonString(playlist.description)},")
        sb.appendLine("      \"cover\": ${jsonString(playlist.coverEntry)},")
        sb.appendLine("      \"trackCount\": ${playlist.tracks.size},")
        sb.append("      \"tracks\": [")
        if (playlist.tracks.isNotEmpty()) {
            sb.appendLine()
            playlist.tracks.forEachIndexed { index, track ->
                appendTrack(sb, track, isLast = index == playlist.tracks.lastIndex)
            }
            sb.append("      ")
        }
        sb.appendLine("]")
        sb.append("    }")
        sb.appendLine(if (isLast) "" else ",")
    }

    private fun appendTrack(sb: StringBuilder, track: ExportTrack, isLast: Boolean) {
        sb.appendLine("        {")
        sb.appendLine("          \"position\": ${track.position},")
        sb.appendLine("          \"name\": ${jsonString(track.name)},")
        sb.append("          \"artists\": [")
        track.artists.forEachIndexed { index, artist ->
            if (index > 0) sb.append(", ")
            sb.append(jsonString(artist))
        }
        sb.appendLine("],")
        sb.appendLine("          \"remoteTrackId\": ${jsonString(track.remoteTrackId)},")
        sb.appendLine("          \"youtubeVideoId\": ${jsonString(track.youtubeVideoId)}")
        sb.append("        }")
        sb.appendLine(if (isLast) "" else ",")
    }
}
