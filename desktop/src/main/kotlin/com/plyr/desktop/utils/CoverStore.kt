package com.plyr.desktop.utils

import com.plyr.core.util.Logger
import com.plyr.desktop.platform.AppPaths
import com.plyr.desktop.sync.CoverImages
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private const val TAG = "CoverStore"
private const val DIR_NAME = "covers"

/**
 * Portadas personalizadas de las listas (M8).
 *
 * Android guarda el resultado del recorte y lo referencia por una ruta propia;
 * aquí se replica con `javax.imageio`: el cuadrado recortado se reescala a
 * [SIDE] px, se recomprime como JPEG (mismo escritor que la exportación, M10) y
 * se escribe bajo la carpeta de datos. `imageUrl` persiste la URL `file://`
 * resultante, que tanto Coil como el exportador saben leer.
 */
object CoverStore {

    /** Lado del cuadrado al que se normalizan las portadas de usuario. */
    const val SIDE = 512

    /** Lee [file] como imagen, o null si no es una imagen decodificable. */
    fun load(file: File): BufferedImage? = try {
        ImageIO.read(file)
    } catch (e: Exception) {
        Logger.w(TAG, "No se pudo leer la imagen: ${e.message}")
        null
    }

    /** Guarda [image] (ya cuadrada) como portada de [playlistId]. */
    fun save(playlistId: String, image: BufferedImage): String? = try {
        val target = directory().resolve("${safeName(playlistId)}.jpg")
        Files.createDirectories(target.parent)
        val bytes = CoverImages.encodeJpeg(resizeToSquare(image, SIDE)) ?: error("sin escritor JPEG")
        target.toFile().writeBytes(bytes)
        target.toUri().toString()
    } catch (e: Exception) {
        Logger.w(TAG, "No se pudo guardar la portada: ${e.message}")
        null
    }

    private fun directory(): Path = Path.of(AppPaths.directories.dataDir, DIR_NAME)

    private fun safeName(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun resizeToSquare(source: BufferedImage, side: Int): BufferedImage {
        val scaled = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, side, side)
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            )
            graphics.drawImage(source, 0, 0, side, side, null)
        } finally {
            graphics.dispose()
        }
        return scaled
    }
}
