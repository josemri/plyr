package com.plyr.desktop

import com.plyr.desktop.player.VlcAudioPlayer
import com.plyr.desktop.player.VlcEnvironment
import com.plyr.network.YouTubeManager
import com.plyr.player.AudioPlayerListener
import com.plyr.player.AudioTrack
import com.plyr.player.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Prueba de sonido de M4, por línea de comandos.
 *
 * Demuestra de punta a punta el motor de audio de escritorio: busca una canción
 * en YouTube, extrae su URL de audio y la reproduce con VLCJ. Se ejecuta con
 *
 *     ./gradlew :desktop:soundCheck --args="artista canción"
 *
 * y es la verificación "headless" complementaria a la ventana de `:desktop:run`.
 */
private const val DEFAULT_QUERY = "Daft Punk Get Lucky"
private const val DEFAULT_SECONDS = 20

fun main(args: Array<String>) {
    val query = args.filterNot { it.startsWith("--") }.joinToString(" ").ifBlank { DEFAULT_QUERY }
    val seconds = args.firstOrNull { it.startsWith("--seconds=") }
        ?.substringAfter('=')
        ?.toIntOrNull()
        ?: DEFAULT_SECONDS

    println("plyr · prueba de sonido (M4)")
    println("Buscando: \"$query\"")

    val libraryDir = VlcEnvironment.findLibraryDir()
    if (libraryDir == null) {
        System.err.println()
        System.err.println(VlcEnvironment.missingMessage())
        exitProcess(2)
    }
    println("libvlc encontrado en: ${libraryDir.absolutePath}")

    val exitCode = runBlocking { runSoundCheck(query, seconds, libraryDir) }
    exitProcess(exitCode)
}

private suspend fun runSoundCheck(
    query: String,
    seconds: Int,
    libraryDir: java.io.File,
): Int {
    val videoId = YouTubeManager.searchVideoId(query)
    if (videoId == null) {
        System.err.println("No se encontró ningún resultado en YouTube para \"$query\".")
        return 1
    }
    println("Vídeo encontrado: https://youtu.be/$videoId")

    val url = YouTubeManager.getAudioUrl(videoId)
    if (url == null) {
        System.err.println("No se pudo extraer la URL de audio del vídeo (¿cambió YouTube?).")
        return 1
    }
    println("URL de audio: ${url.take(80)}…")

    val player = try {
        VlcAudioPlayer(initialListener = LoggingListener(), libraryDir = libraryDir)
    } catch (e: VlcEnvironment.LibVlcMissingException) {
        System.err.println(e.message)
        return 2
    }

    var reachedAudio = false
    try {
        player.setMediaItems(
            listOf(AudioTrack(mediaId = "soundcheck", title = "Sound check", artist = "plyr", uri = url)),
            startIndex = 0,
        )
        player.prepare()
        player.play()

        var ticks = 0
        val totalTicks = seconds * 2
        while (ticks < totalTicks && player.playbackState != PlaybackState.ENDED) {
            delay(500)
            ticks++
            val position = player.currentPosition / 1000.0
            val duration = player.duration / 1000.0
            reachedAudio = reachedAudio || player.currentPosition > 0
            val state = if (player.isPlaying) "sonando" else "en pausa"
            println("  %.1fs / %.1fs  (%s)".format(position, duration, state))
        }
    } finally {
        player.stop()
        player.release()
    }

    println()
    if (reachedAudio) {
        println("✔ El motor de audio reprodujo la pista.")
        return 0
    }
    System.err.println("✖ No se reprodujo audio (VLC no avanzó de posición).")
    return 1
}

private class LoggingListener : AudioPlayerListener {
    override fun onPlaybackStateChanged(state: PlaybackState) = println("  · estado: $state")

    override fun onIsPlayingChanged(isPlaying: Boolean) = Unit

    override fun onMediaItemTransition(automatic: Boolean) = Unit

    override fun onPlayerError(error: Throwable) = System.err.println("  · error: ${error.message}")
}
