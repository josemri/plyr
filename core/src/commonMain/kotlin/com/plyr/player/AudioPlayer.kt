package com.plyr.player

/**
 * Estado de reproducción neutral, independiente de media3.
 *
 * Refleja lo que el reproductor de cada plataforma expone (`ExoPlayer` en
 * Android, `EmbeddedMediaPlayer` en escritorio) sin filtrar sus constantes.
 */
enum class PlaybackState {
    /** Sin nada cargado (equivalente a `Player.STATE_IDLE`). */
    IDLE,

    /** Cargando/bufferizando. */
    BUFFERING,

    /** Listo para sonar. */
    READY,

    /** La pista actual ha terminado de forma natural. */
    ENDED,
}

/**
 * Interfaz mínima que la lógica de cola necesita de un motor de audio.
 *
 * Es el único punto por el que `PlayerViewModel` toca el reproductor: en Android
 * lo implementa [com.plyr.player.ExoAudioPlayer] sobre `ExoPlayer`, y en
 * escritorio lo hará `VlcAudioPlayer` sobre VLCJ. Así la lógica de ventana y
 * transición se testea sin Android.
 */
interface AudioPlayer {

    /** Items cargados ahora mismo en la ventana del reproductor. */
    val mediaItemCount: Int

    /** Índice dentro de la ventana del item en curso. */
    val currentMediaItemIndex: Int

    /** Posición de reproducción de la pista actual, en ms. */
    val currentPosition: Long

    /** Duración de la pista actual en ms, o 0 si aún no se conoce. */
    val duration: Long

    val isPlaying: Boolean

    /**
     * Estado de reproducción neutral.
     *
     * Sustituye a las constantes `Player.STATE_*` de media3: la lógica de cola
     * solo distingue "no hay nada cargado" ([PlaybackState.IDLE]) y
     * "bufferizando" ([PlaybackState.BUFFERING]).
     */
    val playbackState: PlaybackState

    fun setListener(listener: AudioPlayerListener)

    /** Reemplaza la ventana por [items] y arranca en [startIndex] (sin reproducir). */
    fun setMediaItems(items: List<AudioTrack>, startIndex: Int)

    /** Añade [items] al final de la ventana. */
    fun addMediaItems(items: List<AudioTrack>)

    /** Elimina de la ventana `[fromIndex, toExclusive)`. */
    fun removeMediaItems(fromIndex: Int, toExclusive: Int)

    fun prepare()
    fun play()
    fun pause()
    fun stop()

    /** Vacía la ventana sin destruir el reproductor. */
    fun clearMediaItems()

    /** Salta al item [index] de la ventana, en la posición [positionMs] (o [TIME_UNSET]). */
    fun seekTo(index: Int, positionMs: Long)

    /** Salta a [positionMs] dentro del item actual. */
    fun seekTo(positionMs: Long)

    /** Avanza el incremento configurado (10 s). */
    fun seekForward()

    /** Retrocede el incremento configurado (10 s). */
    fun seekBack()

    /**
     * Activa el bucle continuo del item actual.
     *
     * Solo "repetir uno" se delega al motor: así el repeat es continuo y sin
     * cortes. El resto de transiciones las decide la cola de la app.
     */
    fun setRepeatOne(enabled: Boolean)

    /** Libera el reproductor. */
    fun release()

    companion object {
        /** Posición "sin definir" para `seekTo(index, positionMs)`. */
        const val TIME_UNSET: Long = Long.MIN_VALUE + 1
    }
}

/** Eventos del reproductor traducidos a tipos neutrales. */
interface AudioPlayerListener {
    fun onPlaybackStateChanged(state: PlaybackState)
    fun onIsPlayingChanged(isPlaying: Boolean)

    /** Cambió el item en curso; [automatic] si fue por transición automática. */
    fun onMediaItemTransition(automatic: Boolean)

    fun onPlayerError(error: Throwable)
}
