package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests de las operaciones puras de tombs de borrado. */
class TombstonesTest {

    @Test
    fun merge_uneLocalesYDelArchivo() {
        assertEquals(
            setOf("a", "b", "c"),
            Tombstones.merge(local = listOf("a", "b"), incoming = listOf("b", "c")),
        )
    }

    @Test
    fun merge_deVacios() {
        assertEquals(emptySet<String>(), Tombstones.merge(emptyList(), emptyList()))
        assertEquals(setOf("a"), Tombstones.merge(emptyList<String>(), listOf("a")))
    }

    @Test
    fun add_esIdempotente() {
        val once = Tombstones.add(listOf("a"), "b")
        assertEquals(setOf("a", "b"), once)
        assertEquals(once, Tombstones.add(once, "b"))
    }

    @Test
    fun remove_olvidaElTomb() {
        assertEquals(setOf("a"), Tombstones.remove(listOf("a", "b"), "b"))
    }

    @Test
    fun remove_deLoQueNoEsta_noCambiaNada() {
        assertEquals(setOf("a"), Tombstones.remove(listOf("a"), "z"))
    }

    @Test
    fun remove_noMutaLaEntrada() {
        val original = mutableSetOf("a", "b")
        Tombstones.remove(original, "a")
        assertEquals(setOf("a", "b"), original)
    }
}
