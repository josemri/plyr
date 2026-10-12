package com.plyr.utils

import com.plyr.network.SimpleDownloader
import java.util.Locale
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Inicializa NewPipe Extractor una única vez y de forma idempotente.
 *
 * NewPipe.init() lanza si ya ha sido llamado, por lo que tener guardas
 * independientes en YouTubeManager, MediaMetadataExtractor y
 * YouTubeSearchManager era frágil: la primera en ejecutarse "ganaba" y las
 * demás fallaban. Este holder garantiza un único init a nivel de proceso.
 */
object NewPipeHolder {
    private var initialized = false

    @Synchronized
    fun ensureInitialized() {
        val locale = Locale.getDefault()
        ensureInitialized(
            SimpleDownloader.getInstance(),
            Localization(locale.language, locale.country)
        )
    }

    @Synchronized
    fun ensureInitialized(downloader: Downloader, localization: Localization) {
        if (initialized) return
        NewPipe.init(downloader, localization)
        initialized = true
    }

    fun isInitialized(): Boolean = initialized
}
