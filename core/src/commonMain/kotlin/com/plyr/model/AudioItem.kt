package com.plyr.model

data class AudioItem(
    val title: String,
    val url: String,
    val videoId: String = "",
    val channel: String = "",
    val duration: String = ""
)