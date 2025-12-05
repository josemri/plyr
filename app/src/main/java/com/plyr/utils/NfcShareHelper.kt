package com.plyr.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Helper simple para compartir una URL mediante NFC (NDEF URI push).
 *
 * Observaciones:
 * - Las APIs de push NFC requieren una Activity (foreground). Por eso las funciones esperan un Context que sea Activity.
 * - Si el dispositivo no tiene NFC o el contexto no es Activity se lanzará una excepción.
 */
object NfcShareHelper {
    private var adapterRef: NfcAdapter? = null
    private var activityRef: WeakReference<Activity>? = null

    private fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return if (ctx is Activity) ctx else null
    }

    /** Habilita el envío NDEF (URI) hasta que se llame a disableNfcPush. */
    fun enableNfcPush(context: Context, url: String) {
        val activity = findActivity(context)
            ?: throw IllegalArgumentException("Could not find an Activity from the provided Context. Pass an Activity context.")

        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
            ?: throw UnsupportedOperationException("NFC is not available on this device.")

        val message = createNdefMessage(url)

        try {
            // Preferir setNdefPushMessage si está disponible
            try {
                val method = nfcAdapter.javaClass.getMethod("setNdefPushMessage", NdefMessage::class.java, Activity::class.java)
                method.invoke(nfcAdapter, message, activity)
            } catch (e: NoSuchMethodException) {
                // fallback: invoke enableForegroundNdefPush if present
                val fallback = nfcAdapter.javaClass.methods.find { it.name == "enableForegroundNdefPush" }
                if (fallback != null) {
                    fallback.invoke(nfcAdapter, activity, message)
                } else {
                    throw UnsupportedOperationException("No compatible NDEF push method found on NfcAdapter")
                }
            }
            adapterRef = nfcAdapter
            activityRef = WeakReference(activity)
            Log.d("NfcShareHelper", "NFC push enabled for URL: $url")
        } catch (e: Exception) {
            Log.e("NfcShareHelper", "Failed to enable NFC push: ${e.message}")
            throw e
        }
    }

    /** Desactiva el envío NDEF si estaba activo. */
    fun disableNfcPush(context: Context) {
        val activity = findActivity(context)
            ?: throw IllegalArgumentException("Could not find an Activity from the provided Context. Pass an Activity context.")

        val nfcAdapter = NfcAdapter.getDefaultAdapter(context) ?: return

        try {
            // intentar limpiar el mensaje push
            try {
                val method = nfcAdapter.javaClass.getMethod("setNdefPushMessage", NdefMessage::class.java, Activity::class.java)
                method.invoke(nfcAdapter, null, activity)
            } catch (e: NoSuchMethodException) {
                // fallback a método disableForegroundNdefPush si existe
                val fallback = nfcAdapter.javaClass.methods.find { it.name == "disableForegroundNdefPush" }
                if (fallback != null) {
                    fallback.invoke(nfcAdapter, activity)
                } else {
                    // nada que hacer
                }
            }
            adapterRef = null
            activityRef = null
            Log.d("NfcShareHelper", "NFC push disabled")
        } catch (e: Exception) {
            Log.e("NfcShareHelper", "Failed to disable NFC push: ${e.message}")
            throw e
        }
    }

    private fun createNdefMessage(url: String): NdefMessage {
        val record = NdefRecord.createUri(url)
        return NdefMessage(arrayOf(record))
    }
}
