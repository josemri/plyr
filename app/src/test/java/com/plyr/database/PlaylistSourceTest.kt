package com.plyr.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de la interpretación del origen persistido en un backup
 * ([PlaylistSource.fromStorage]). Un archivo antiguo no lleva el campo y debe
 * caer a la heurística (`null`), no a un origen inventado.
 */
class PlaylistSourceTest {

    @Test
    fun fromStorage_readsEveryKnownName() {
        PlaylistSource.entries.forEach { source ->
            assertEquals(source, PlaylistSource.fromStorage(source.name))
        }
    }

    @Test
    fun fromStorage_ignoresCaseAndWhitespace() {
        assertEquals(PlaylistSource.SPOTIFY, PlaylistSource.fromStorage("  spotify "))
        assertEquals(PlaylistSource.YOUTUBE, PlaylistSource.fromStorage("YouTubE"))
    }

    @Test
    fun fromStorage_nullOrEmptyOrUnknown_isNull() {
        assertNull(PlaylistSource.fromStorage(null))
        assertNull(PlaylistSource.fromStorage(""))
        assertNull(PlaylistSource.fromStorage("   "))
        assertNull(PlaylistSource.fromStorage("NO_EXISTE"))
    }
}
