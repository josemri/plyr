package com.plyr.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log
import com.plyr.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper para leer URLs de tags NFC
 */
object NfcReader {
    private const val TAG = "NfcReader"

    private val _isReading = MutableStateFlow(false)
    val isReading: StateFlow<Boolean> = _isReading.asStateFlow()

    private val _lastReadUrl = MutableStateFlow<String?>(null)
    val lastReadUrl: StateFlow<String?> = _lastReadUrl.asStateFlow()

    private val _lastScanResult = MutableStateFlow<ScanResult?>(null)
    val lastScanResult: StateFlow<ScanResult?> = _lastScanResult.asStateFlow()

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    /**
     * Inicia el modo de lectura NFC
     */
    fun startReading(activity: Activity): Boolean {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)

        if (nfcAdapter == null) {
            Log.e(TAG, "❌ NFC no está disponible en este dispositivo")
            return false
        }

        if (!nfcAdapter!!.isEnabled) {
            Log.e(TAG, "❌ NFC está deshabilitado")
            return false
        }

        val intent = Intent(activity, activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        pendingIntent = PendingIntent.getActivity(
            activity,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                Log.e(TAG, "Error en MIME type", e)
            }
        }

        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)

        val filters = arrayOf(ndefFilter, techFilter, tagFilter)
        val techLists = arrayOf(arrayOf(Ndef::class.java.name))

        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
        _isReading.value = true
        Log.d(TAG, "📡 Modo lectura NFC activado - Acerca un tag NFC")

        return true
    }

    /**
     * Detiene el modo de lectura NFC
     */
    fun stopReading(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
        _isReading.value = false
        Log.d(TAG, "🛑 Modo lectura NFC desactivado")
    }

    /**
     * Procesa un intent NFC y extrae la URL
     */
    fun processNfcIntent(intent: Intent): String? {
        val action = intent.action

        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED) {
            return null
        }

        Log.d(TAG, "🏷️ Intent NFC recibido: $action")

        // Intentar leer mensaje NDEF
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        if (rawMessages != null && rawMessages.isNotEmpty()) {
            for (rawMessage in rawMessages) {
                val ndefMessage = rawMessage as? NdefMessage ?: continue
                for (record in ndefMessage.records) {
                    val url = extractUrlFromRecord(record)
                    if (url != null && UrlParser.isPlayableUrl(url)) {
                        Log.d(TAG, "✅ URL encontrada en NDEF: $url")
                        _lastReadUrl.value = url
                        _lastScanResult.value = UrlParser.parseScanText(url)
                        return url
                    }
                }
            }
        }

        // Si no hay NDEF, intentar leer del tag directamente
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag != null) {
            val url = readUrlFromTag(tag)
            if (url != null) {
                Log.d(TAG, "✅ URL leída del tag: $url")
                _lastReadUrl.value = url
                _lastScanResult.value = UrlParser.parseScanText(url)
                return url
            }
        }

        Log.w(TAG, "⚠️ No se encontró URL válida en el tag")
        return null
    }

    /**
     * Lee una URL directamente de un tag NFC
     */
    private fun readUrlFromTag(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null

        return try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            ndef.close()

            if (ndefMessage != null) {
                for (record in ndefMessage.records) {
                    val url = extractUrlFromRecord(record)
                    if (url != null && UrlParser.isPlayableUrl(url)) {
                        return url
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error leyendo tag", e)
            try { ndef.close() } catch (_: Exception) {}
            null
        }
    }

    /**
     * Extrae una URL de un registro NDEF
     */
    private fun extractUrlFromRecord(record: NdefRecord): String? {
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                if (record.type.contentEquals(NdefRecord.RTD_URI)) {
                    parseUriRecord(record)
                } else if (record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                    parseTextRecord(record)
                } else {
                    null
                }
            }
            NdefRecord.TNF_ABSOLUTE_URI -> {
                String(record.payload, Charsets.UTF_8)
            }
            NdefRecord.TNF_EXTERNAL_TYPE -> {
                String(record.payload, Charsets.UTF_8)
            }
            else -> null
        }
    }

    /**
     * Parsea un registro URI según la especificación NFC Forum
     */
    private fun parseUriRecord(record: NdefRecord): String? {
        val payload = record.payload
        if (payload.isEmpty()) return null

        val prefixByte = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrElse(prefixByte) { "" }
        val uri = String(payload, 1, payload.size - 1, Charsets.UTF_8)

        return prefix + uri
    }

    /**
     * Parsea un registro de texto
     */
    private fun parseTextRecord(record: NdefRecord): String? {
        val payload = record.payload
        if (payload.isEmpty()) return null

        val statusByte = payload[0].toInt() and 0xFF
        val isUtf16 = (statusByte and 0x80) != 0
        val langCodeLength = statusByte and 0x3F

        val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
        val textOffset = 1 + langCodeLength

        return if (textOffset < payload.size) {
            String(payload, textOffset, payload.size - textOffset, charset)
        } else {
            null
        }
    }

    /**
     * Verifica si la URL es reconocida
     */
    fun clearLastUrl() {
        _lastReadUrl.value = null
        _lastScanResult.value = null
    }

    fun consumeScanResult(): ScanResult? {
        val result = _lastScanResult.value
        _lastScanResult.value = null
        return result
    }

    // Prefijos URI según NFC Forum RTD URI
    private val URI_PREFIXES = mapOf(
        0x00 to "",
        0x01 to "http://www.",
        0x02 to "https://www.",
        0x03 to "http://",
        0x04 to "https://",
        0x05 to "tel:",
        0x06 to "mailto:",
        0x07 to "ftp://anonymous:anonymous@",
        0x08 to "ftp://ftp.",
        0x09 to "ftps://",
        0x0A to "sftp://",
        0x0B to "smb://",
        0x0C to "nfs://",
        0x0D to "ftp://",
        0x0E to "dav://",
        0x0F to "news:",
        0x10 to "telnet://",
        0x11 to "imap:",
        0x12 to "rtsp://",
        0x13 to "urn:",
        0x14 to "pop:",
        0x15 to "sip:",
        0x16 to "sips:",
        0x17 to "tftp:",
        0x18 to "btspp://",
        0x19 to "btl2cap://",
        0x1A to "btgoep://",
        0x1B to "tcpobex://",
        0x1C to "irdaobex://",
        0x1D to "file://",
        0x1E to "urn:epc:id:",
        0x1F to "urn:epc:tag:",
        0x20 to "urn:epc:pat:",
        0x21 to "urn:epc:raw:",
        0x22 to "urn:epc:",
        0x23 to "urn:nfc:"
    )
}
