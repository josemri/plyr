package com.plyr.utils

import java.security.MessageDigest

/**
 * ExportDigest - Huella del contenido de una exportación.
 *
 * Sirve para responder a la única pregunta que importa al sincronizar: *¿han
 * cambiado los datos desde la última vez que escribí el archivo?* Escribir en
 * una carpeta de Drive son varias llamadas de red, así que si el contenido es
 * idéntico no se toca nada.
 *
 * Deliberadamente no incluye la marca de tiempo de exportación: cambiaría en
 * cada ejecución y la huella siempre sería distinta, que es justo lo contrario
 * de lo que se quiere.
 *
 * No depende de Android, como [ExportManifest], para poder testearlo en la JVM.
 */
object ExportDigest {

    /**
     * Longitud que marca "este campo no existe". Es negativa para que nunca
     * pueda confundirse con el tamaño real de un valor.
     */
    private const val ABSENT = -1

    /**
     * Acumula la huella de lo que se le va pasando y la devuelve en hexadecimal.
     *
     * Cada campo va precedido de su longitud en bytes. Sin eso, una lista con
     * nombre "ab" y descripción "c" daría la misma huella que una con nombre
     * "a" y descripción "bc", y dos bibliotecas distintas se darían por
     * iguales sin serlo.
     */
    class Accumulator {
        private val digest = MessageDigest.getInstance("SHA-256")

        /** Añade una lista con su portada ya resuelta (null si no tiene). */
        fun addPlaylist(playlist: ExportPlaylist, coverBytes: ByteArray?) = apply {
            updateField(playlist.id)
            updateField(playlist.name)
            updateField(playlist.description)
            updateField(playlist.coverEntry)
            updateField(playlist.tracks.size.toString())
            playlist.tracks.forEach { track ->
                updateField(track.position.toString())
                updateField(track.name)
                updateField(track.artists.joinToString(", "))
                updateField(track.remoteTrackId)
                updateField(track.youtubeVideoId)
            }
            // Sin portada (null) y con portada vacía (0 bytes) son estados
            // distintos: una descarga fallida deja la lista sin imagen.
            updateFieldLength(if (coverBytes == null) ABSENT else coverBytes.size)
            if (coverBytes != null) digest.update(coverBytes)
        }

        /** Huella acumulada hasta ahora, en minúsculas hexadecimal. */
        fun hex(): String = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }

        // === INTERNOS ===

        /**
         * Un null se marca con longitud -1 en vez de con su cadena vacía: si
         * no, "sin descripción" y "descripción vacía" serían indistinguibles y
         * un cambio real de la biblioteca pasaría desapercibido.
         */
        private fun updateField(value: String?) {
            if (value == null) {
                updateFieldLength(ABSENT)
                return
            }
            val bytes = value.toByteArray(Charsets.UTF_8)
            updateFieldLength(bytes.size)
            digest.update(bytes)
        }

        /** Longitud de 4 bytes big-endian; [ABSENT] marca "sin valor". */
        private fun updateFieldLength(length: Int) {
            digest.update(
                byteArrayOf(
                    (length ushr 24).toByte(),
                    (length ushr 16).toByte(),
                    (length ushr 8).toByte(),
                    length.toByte()
                )
            )
        }
    }
}
