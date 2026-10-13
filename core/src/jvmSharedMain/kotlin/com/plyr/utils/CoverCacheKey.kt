package com.plyr.utils

import java.security.MessageDigest
import java.util.Locale

/**
 * Clave de caché de una portada remota: el SHA-256 de su URL en hexadecimal.
 *
 * Las URLs de portada de YouTube son largas y contienen caracteres que no valen
 * como nombre de archivo (`?`, `&`, `/`, `%`), así que tanto Android como el
 * escritorio guardan cada portada bajo esta clave. Vive en `:core` para que las
 * dos plataformas usen exactamente el mismo algoritmo y no diverjan.
 */
object CoverCacheKey {

    /** Nombre de archivo (sin extensión) que corresponde a [url]. */
    fun cacheKey(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(Locale.ROOT, byte) }
    }
}
