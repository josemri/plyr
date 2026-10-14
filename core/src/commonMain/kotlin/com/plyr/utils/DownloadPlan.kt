package com.plyr.utils

/**
 * Selección pura de qué pistas hay que descargar de una lista.
 *
 * Se separa de la descarga en sí para poder testearlo sin Android ni sistema de
 * ficheros: es donde se decide "esto ya está, esto no" y donde se colarían los
 * duplicados. Es la misma lógica que usan Android (`DownloadViewModel`) y el
 * escritorio (`DownloadController`).
 *
 * Se mantiene genérico sobre el tipo de pista para no depender de `TrackEntity`
 * (Room, en `:app`) ni de `TrackRecord` (`:core`): cada plataforma decide cómo
 * obtener el id de vídeo con `videoIdOf`.
 */
object DownloadPlan {

    /**
     * Pistas de [tracks] que todavía no están en el almacén, **una por
     * `videoIdOf`** (la primera que aparece gana). Se descartan las que no tienen
     * id de vídeo (sin él no hay nada que resolver ni descargar).
     *
     * [isDownloaded] se inyecta para no depender del sistema de ficheros en los
     * tests.
     */
    fun <T> pending(
        tracks: List<T>,
        videoIdOf: (T) -> String?,
        isDownloaded: (String) -> Boolean,
    ): List<T> {
        val seen = mutableSetOf<String>()
        return tracks.filter { track ->
            val videoId = videoIdOf(track)
            !videoId.isNullOrBlank() && seen.add(videoId) && !isDownloaded(videoId)
        }
    }
}
