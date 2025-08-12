package com.plyr.utils

import android.content.Context
import android.util.Log

/**
 * YouTubeAudioExtractor - Utilidad para extraer audio de YouTube con calidad configurable
 *
 * Esta clase maneja:
 * - Extracción de URLs de audio de YouTube basada en la configuración de calidad
 * - Mapeo de calidades de audio a formatos específicos
 * - Optimización de streaming según las preferencias del usuario
 *
 * Las calidades se mapean de la siguiente manera:
 * - worst: Prioriza menor uso de datos (formatos de menor bitrate)
 * - medium: Equilibrio entre calidad y datos (formatos estándar)
 * - best: Máxima calidad disponible (formatos de mayor bitrate)
 */
object YouTubeAudioExtractor {

    private const val TAG = "YouTubeAudioExtractor"

    /**
     * Parámetros de calidad para yt-dlp/youtube-dl según configuración del usuario
     */
    private val qualityFormats = mapOf(
        Config.AUDIO_QUALITY_WORST to listOf(
            "worstaudio[ext=m4a]",
            "worstaudio[ext=webm]",
            "worstaudio",
            "worst[height<=360]"
        ),
        Config.AUDIO_QUALITY_MEDIUM to listOf(
            "bestaudio[ext=m4a][abr<=128]",
            "bestaudio[ext=webm][abr<=128]",
            "bestaudio[abr<=128]",
            "best[height<=720]"
        ),
        Config.AUDIO_QUALITY_BEST to listOf(
            "bestaudio[ext=m4a]",
            "bestaudio[ext=webm]",
            "bestaudio",
            "best"
        )
    )

    /**
     * Obtiene el formato de audio recomendado basado en la configuración actual.
     *
     * @param context Contexto de la aplicación para acceder a configuración
     * @return String con el formato de audio para yt-dlp/youtube-dl
     */
    fun getAudioFormat(context: Context): String {
        val currentQuality = Config.getAudioQuality(context)
        val formats = qualityFormats[currentQuality] ?: qualityFormats[Config.AUDIO_QUALITY_MEDIUM]!!

        Log.d(TAG, "Calidad de audio configurada: $currentQuality")
        Log.d(TAG, "Formatos de audio seleccionados: $formats")

        // Unir formatos con '/' para que yt-dlp pruebe en orden de preferencia
        return formats.joinToString("/")
    }

    /**
     * Obtiene los parámetros adicionales para la extracción según calidad configurada.
     *
     * @param context Contexto de la aplicación
     * @return Map con parámetros adicionales para el extractor
     */
    fun getExtractionParams(context: Context): Map<String, Any> {
        val currentQuality = Config.getAudioQuality(context)

        return when (currentQuality) {
            Config.AUDIO_QUALITY_WORST -> mapOf(
                "prefer_ffmpeg" to true,
                "postprocessors" to listOf(
                    mapOf(
                        "key" to "FFmpegAudioConvertor",
                        "preferredcodec" to "m4a",
                        "preferredquality" to "64"
                    )
                )
            )
            Config.AUDIO_QUALITY_MEDIUM -> mapOf(
                "prefer_ffmpeg" to true,
                "postprocessors" to listOf(
                    mapOf(
                        "key" to "FFmpegAudioConvertor",
                        "preferredcodec" to "m4a",
                        "preferredquality" to "128"
                    )
                )
            )
            Config.AUDIO_QUALITY_BEST -> mapOf(
                "prefer_ffmpeg" to true,
                "postprocessors" to listOf(
                    mapOf(
                        "key" to "FFmpegAudioConvertor",
                        "preferredcodec" to "m4a",
                        "preferredquality" to "192"
                    )
                )
            )
            else -> mapOf()
        }
    }

    /**
     * Obtiene una descripción amigable de la calidad actual y su impacto.
     *
     * @param context Contexto de la aplicación
     * @return String descriptivo de la calidad actual
     */
    fun getQualityDescription(context: Context): String {
        val currentQuality = Config.getAudioQuality(context)
        val baseDescription = Config.AUDIO_QUALITY_DESCRIPTIONS[currentQuality]
            ?: Config.AUDIO_QUALITY_DESCRIPTIONS[Config.AUDIO_QUALITY_MEDIUM]!!

        val additionalInfo = when (currentQuality) {
            Config.AUDIO_QUALITY_WORST -> " • Bitrate: ~64 kbps • Uso de datos mínimo"
            Config.AUDIO_QUALITY_MEDIUM -> " • Bitrate: ~128 kbps • Buena calidad y uso moderado de datos"
            Config.AUDIO_QUALITY_BEST -> " • Bitrate: ~192+ kbps • Máxima calidad disponible"
            else -> ""
        }

        return "$baseDescription$additionalInfo"
    }

    /**
     * Estima el uso de datos por minuto de audio según la calidad configurada.
     *
     * @param context Contexto de la aplicación
     * @return Uso estimado de datos en MB por minuto
     */
    fun getEstimatedDataUsagePerMinute(context: Context): Double {
        val currentQuality = Config.getAudioQuality(context)

        return when (currentQuality) {
            Config.AUDIO_QUALITY_WORST -> 0.48   // ~64 kbps = 0.48 MB/min
            Config.AUDIO_QUALITY_MEDIUM -> 0.96  // ~128 kbps = 0.96 MB/min
            Config.AUDIO_QUALITY_BEST -> 1.44    // ~192 kbps = 1.44 MB/min
            else -> 0.96
        }
    }

    /**
     * Genera el comando completo para yt-dlp basado en la configuración actual.
     *
     * @param context Contexto de la aplicación
     * @param videoUrl URL del video de YouTube
     * @return Lista de argumentos para yt-dlp
     */
    fun generateYtDlpCommand(context: Context, videoUrl: String): List<String> {
        val audioFormat = getAudioFormat(context)
        val currentQuality = Config.getAudioQuality(context)

        val baseCommand = mutableListOf(
            "yt-dlp",
            "--extract-flat", "false",
            "--format", audioFormat,
            "--no-playlist",
            "--get-url"
        )

        // Añadir parámetros específicos según calidad
        when (currentQuality) {
            Config.AUDIO_QUALITY_WORST -> {
                baseCommand.addAll(listOf(
                    "--audio-quality", "3",  // Calidad baja
                    "--prefer-ffmpeg"
                ))
            }
            Config.AUDIO_QUALITY_MEDIUM -> {
                baseCommand.addAll(listOf(
                    "--audio-quality", "5",  // Calidad media
                    "--prefer-ffmpeg"
                ))
            }
            Config.AUDIO_QUALITY_BEST -> {
                baseCommand.addAll(listOf(
                    "--audio-quality", "0",  // Mejor calidad
                    "--prefer-ffmpeg"
                ))
            }
        }

        baseCommand.add(videoUrl)

        Log.d(TAG, "Comando yt-dlp generado: ${baseCommand.joinToString(" ")}")
        return baseCommand
    }
}
