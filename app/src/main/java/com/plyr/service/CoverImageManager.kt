package com.plyr.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * CoverImageManager - Gestión de imágenes de portada de playlists locales.
 *
 * - [decode]: lee un Uri de imagen y lo decodifica con downsampling para no
 *   cargar imágenes gigantes en memoria.
 * - [crop]: recorta un cuadrado de la imagen según un [CoverCropRect].
 * - [resizeToSquare]: escala el recorte final a un tamaño razonable.
 * - [save]: guarda el resultado como JPEG en `filesDir/covers` y devuelve la
 *   ruta como `file://` Uri (la entiende Coil), o null si falla.
 */
object CoverImageManager {

    private const val TAG = "CoverImageManager"

    /** Tamaño máximo de decodificación (reducción de uso de memoria). */
    const val MAX_DECODE_SIDE = 2048

    /** Tamaño máximo del recorte final guardado. */
    const val MAX_OUTPUT_SIDE = 1024

    /** Calidad JPEG del archivo final. */
    private const val JPEG_QUALITY = 90

    /**
     * Decodifica una imagen desde [uri] con downsampling para que ningún lado
     * supere [MAX_DECODE_SIDE]. Devuelve null si no se puede leer.
     */
    fun decode(context: Context, uri: Uri): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_DECODE_SIDE ||
                bounds.outHeight / (sample * 2) >= MAX_DECODE_SIDE
            ) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            Log.e(TAG, "Error decodificando imagen: ${e.message}", e)
            null
        }
    }

    /**
     * Recorta el cuadrado [rect] de [bitmap]. El resultado siempre es un
     * cuadrado contenido dentro de la imagen (se recorta a los límites).
     */
    fun crop(bitmap: Bitmap, rect: CoverCropRect): Bitmap {
        val left = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, bitmap.height)
        val side = minOf(right - left, bottom - top)
        return Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    /**
     * Escala un bitmap cuadrado a [maxSide] píxeles (mantiene la proporción).
     * Si ya es más pequeño, devuelve la misma instancia.
     */
    fun resizeToSquare(bitmap: Bitmap, maxSide: Int = MAX_OUTPUT_SIDE): Bitmap {
        val side = minOf(maxSide, minOf(bitmap.width, bitmap.height))
        if (side >= bitmap.width && side >= bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, side, side, true)
    }

    /**
     * Guarda [bitmap] como portada de la playlist local [playlistRawId]
     * (sobrescribiendo la anterior). Devuelve la URI `file://...` para guardar
     * en `imageUrl`, o null si falla.
     */
    fun save(context: Context, playlistRawId: String, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "playlist_$playlistRawId.jpg")
            file.outputStream().use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    Log.e(TAG, "No se pudo comprimir la portada")
                    return null
                }
            }
            val path = Uri.fromFile(file).toString()
            Log.d(TAG, "Portada guardada: $path")
            path
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando portada: ${e.message}", e)
            null
        }
    }
}
