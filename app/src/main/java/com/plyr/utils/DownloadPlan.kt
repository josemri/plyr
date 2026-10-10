package com.plyr.utils

import com.plyr.database.TrackEntity

/**
 * Selección pura de qué pistas hay que descargar de una lista.
 *
 * Se separa de la descarga en sí para poder testearlo sin Android: es donde se
 * decide "esto ya está, esto no" y donde se colarían los duplicados.
 */
object DownloadPlan {

    /**
     * Pistas de [tracks] que todavía no están en el almacén, **una por
     * `youtubeVideoId`** (la primera que aparece gana). Se descartan las que no
     * tienen `youtubeVideoId` (sin él no hay nada que resolver ni descargar).
     *
     * [isDownloaded] se inyecta para no depender del sistema de ficheros en los
     * tests.
     */
    fun pending(tracks: List<TrackEntity>, isDownloaded: (String) -> Boolean): List<TrackEntity> {
        val seen = mutableSetOf<String>()
        return tracks.filter { track ->
            val videoId = track.youtubeVideoId
            !videoId.isNullOrBlank() && seen.add(videoId) && !isDownloaded(videoId)
        }
    }
}
