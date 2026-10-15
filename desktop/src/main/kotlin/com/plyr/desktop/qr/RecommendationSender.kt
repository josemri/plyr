package com.plyr.desktop.qr

import com.plyr.desktop.config.DesktopConfig
import com.plyr.network.SupabaseClient

/**
 * Envía una URL al feed de recomendaciones (M25).
 *
 * Se inyecta en [QrShareDialog] para poder testear el diálogo sin red, igual que
 * [com.plyr.desktop.feed.FeedRemote] hace con el feed.
 */
fun interface RecommendationSender {
    /** @return `true` si la recomendación se publicó en el grupo general. */
    suspend fun send(url: String): Boolean
}

/**
 * Implementación real: publica [url] en el grupo general con el apodo guardado
 * ([DesktopConfig.userNickname]), como el `requestRecommendation` de Android.
 * Sin apodo o sin grupo general devuelve `false` sin tocar la red.
 */
object SupabaseRecommendationSender : RecommendationSender {
    override suspend fun send(url: String): Boolean {
        val nickname = DesktopConfig.userNickname.trim()
        if (nickname.isEmpty()) return false
        val group = SupabaseClient.getGroups().firstOrNull { it.groupType == "general" } ?: return false
        return SupabaseClient.createRecommendation(group.id, nickname, url, comment = null) != null
    }
}
