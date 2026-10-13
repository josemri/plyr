package com.plyr.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de `stableKeys`: claves únicas y estables para listas perezosas.
 */
class StableKeysTest {

    @Test
    fun emptyInputProducesEmptyKeys() {
        assertEquals(emptyList<String>(), stableKeys(emptyList()))
    }

    @Test
    fun uniqueIdsKeepTheirOwnKey() {
        assertEquals(listOf("a", "b", "c"), stableKeys(listOf("a", "b", "c")))
    }

    @Test
    fun duplicateIdsAreDisambiguated() {
        assertEquals(
            listOf("a", "b", "a#1", "a#2", "b#1"),
            stableKeys(listOf("a", "b", "a", "a", "b"))
        )
    }

    @Test
    fun keysAreAlwaysUnique() {
        val keys = stableKeys(listOf("x", "x", "x", "x"))
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun keysAreStableWhenPreservingPrefix() {
        val before = stableKeys(listOf("a", "b", "c"))
        val after = stableKeys(listOf("z", "a", "b", "c"))
        // Las claves de "a", "b" y "c" no cambian al insertar por delante.
        assertEquals(before, after.drop(1))
    }

    @Test
    fun duplicateKeyDiffersFromOriginal() {
        val keys = stableKeys(listOf("a", "a"))
        assertNotEquals(keys[0], keys[1])
        assertTrue(keys[1].startsWith(keys[0]))
    }
}
