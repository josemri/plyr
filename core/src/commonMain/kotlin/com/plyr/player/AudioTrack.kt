package com.plyr.player

/**
 * Una pista de audio ya resuelta, lista para entregar a un [AudioPlayer].
 *
 * Es el equivalente neutral de un `MediaItem` de media3: no depende de Android
 * ni de ExoPlayer, de modo que la lógica de cola puede construirlos y testearse
 * en un JVM. El adaptador de cada plataforma decide cómo materializarlos
 * (ExoPlayer en Android, VLCJ en escritorio).
 */
data class AudioTrack(
    /** Identificador único de esta entrada en la ventana (`track.id` + posición de cola). */
    val mediaId: String,
    val title: String,
    val artist: String,
    val uri: String,
)
