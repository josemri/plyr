package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSizeTest {

    @Test
    fun bytes_seQuedanEnBytes() {
        assertEquals("0 B", StorageSize.format(0))
        assertEquals("1 B", StorageSize.format(1))
        assertEquals("512 B", StorageSize.format(512))
        assertEquals("1023 B", StorageSize.format(1023))
    }

    @Test
    fun kilobytes_conUnDecimal() {
        assertEquals("1.0 KB", StorageSize.format(1024))
        assertEquals("1.5 KB", StorageSize.format(1536))
        assertEquals("1023.5 KB", StorageSize.format(1048064))
    }

    @Test
    fun megabytes_conUnDecimal() {
        assertEquals("1.0 MB", StorageSize.format(1024L * 1024))
        assertEquals("4.5 MB", StorageSize.format((4.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun gigabytes_conDosDecimales() {
        assertEquals("1.00 GB", StorageSize.format(1024L * 1024 * 1024))
        assertEquals("2.50 GB", StorageSize.format((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun usaPuntoDecimalIndependienteDelIdioma() {
        // Locale.ROOT garantiza el punto aunque el idioma del dispositivo use coma
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.5 KB", StorageSize.format(1536))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
