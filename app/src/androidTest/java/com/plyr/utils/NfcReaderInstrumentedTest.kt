package com.plyr.utils

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentados de la lectura NFC: construcción real de [NdefRecord]
 * (clase de Android) y extracción de la URL por [NfcReader.processNfcIntent],
 * el mismo camino que recorre un tag recibido.
 */
@RunWith(AndroidJUnit4::class)
class NfcReaderInstrumentedTest {

    @Before
    fun clearState() {
        NfcReader.clearLastUrl()
    }

    private fun intentWith(message: NdefMessage): Intent =
        Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(message))
        }

    @Test
    fun readsUriRecordFromNdefMessage() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val message = NdefMessage(arrayOf(NdefRecord.createUri(url)))

        val read = NfcReader.processNfcIntent(intentWith(message))

        assertEquals(url, read)
        assertEquals(url, NfcReader.lastReadUrl.value)
    }

    @Test
    fun readsTextRecordFromNdefMessage() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", url)))

        val read = NfcReader.processNfcIntent(intentWith(message))

        assertEquals(url, read)
    }

    @Test
    fun ignoresNonPlayableRecord() {
        val message = NdefMessage(arrayOf(NdefRecord.createTextRecord("en", "no es una url")))

        assertNull(NfcReader.processNfcIntent(intentWith(message)))
    }

    @Test
    fun ignoresTagIntentWithoutNdefMessages() {
        assertNull(NfcReader.processNfcIntent(Intent(NfcAdapter.ACTION_TAG_DISCOVERED)))
    }

    @Test
    fun ignoresNonNfcIntent() {
        assertNull(NfcReader.processNfcIntent(Intent(Intent.ACTION_VIEW)))
    }
}
