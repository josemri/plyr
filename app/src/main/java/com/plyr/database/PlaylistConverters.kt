package com.plyr.database

import androidx.room.TypeConverter

class PlaylistConverters {
    @TypeConverter
    fun fromSource(source: PlaylistSource): String = source.name

    @TypeConverter
    fun toSource(value: String?): PlaylistSource {
        return value?.let {
            try {
                PlaylistSource.valueOf(it)
            } catch (_: IllegalArgumentException) {
                PlaylistSource.UNKNOWN
            }
        } ?: PlaylistSource.UNKNOWN
    }
}
