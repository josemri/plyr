package com.plyr.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * DownloadedTrackEntity - Entidad para representar canciones descargadas localmente
 *
 * Almacena información de tracks descargados con su ruta de archivo local.
 */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    /** ID único del track */
    @PrimaryKey
    val id: String,

    /** ID único del track en Spotify */
    val spotifyTrackId: String,

    /** Nombre/título del track */
    val name: String,

    /** Lista de artistas separados por coma */
    val artists: String,

    /** ID del video de YouTube correspondiente */
    val youtubeVideoId: String,

    /** Ruta del archivo de audio descargado */
    val localFilePath: String,

    /** Duración del track en milisegundos */
    val durationMs: Long? = null,

    /** Timestamp de la descarga */
    val downloadTime: Long = System.currentTimeMillis()
)

