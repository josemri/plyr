package com.plyr.network

data class AppPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val tracks: AppPlaylistTracks?,
    val images: List<AppImage>?
) {
    fun getImageUrl(): String {
        return images.firstImageUrl()
    }
}

data class AppPlaylistTracks(
    val href: String?,
    val total: Int
)

data class AppImage(
    val url: String,
    val height: Int?,
    val width: Int?
)

data class AppTrack(
    val id: String,
    val name: String,
    val artists: List<AppArtist>,
    val durationMs: Int? = null,
    val album: AppAlbumSimple? = null
) {
    fun getArtistNames(): String {
        return artists.artistNames()
    }

    fun getDisplayName(): String {
        return "$name - ${getArtistNames()}"
    }
}

data class AppAlbumSimple(
    val id: String,
    val name: String,
    val releaseDate: String? = null,
    val images: List<AppImage>? = null
)

data class AppArtist(
    val name: String
)

fun List<AppImage>?.firstImageUrl(): String = this?.firstOrNull()?.url ?: ""

fun List<AppArtist>.artistNames(): String = joinToString(", ") { it.name }
