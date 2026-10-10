package com.plyr.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.OutputStream

/**
 * Almacén de audio descargado para uso offline.
 *
 * Vive como ficheros sueltos (`<videoId>.m4a`) en una subcarpeta `audio/` de la
 * **misma carpeta SAF** que el ZIP de sincronización. Se eligió así a propósito:
 *
 * - **No van dentro de `plyr-sync.zip`**: el ZIP se reescribe entero en cada
 *   sincronización, así que meter audio obligaría a reconstruir megabytes en
 *   cada cambio y a subir los límites de `ImportArchive` (8 MB/entrada).
 * - **Incremental**: bajar una lista solo añade los ficheros que falten; nunca
 *   reescribe los ya bajados.
 * - **Viaja igual que el ZIP**: si la carpeta es de nube (Drive, etc.), los
 *   ficheros se sincronizan por su cuenta; en otro dispositivo con la misma
 *   carpeta se detectan y no se re-descargan.
 *
 * Si no hay carpeta de sync configurada (o su permiso caducó), se usa
 * `filesDir/audio` como respaldo (queda en el dispositivo, sin viajar).
 *
 * "Ya descargado" = existe el fichero. No hay tabla Room nueva ni migración.
 *
 * El índice de lo descargado se lista **una vez por sesión** y se cachea
 * ([downloaded]): comprobar cada pista al reproducir no debe costar una
 * consulta al proveedor (que para una carpeta de nube es una ida y vuelta).
 */
object DownloadedAudioStore {

    private const val TAG = "DownloadedAudioStore"
    private const val DIR_NAME = "audio"
    private const val EXTENSION = "m4a"
    private const val SUFFIX = ".$EXTENSION"
    private const val MIME_AUDIO = "audio/mp4"
    private const val MIME_OCTET = "application/octet-stream"
    private const val LOCAL_KEY = "local"

    /** Resumen del audio offline: cuántas pistas y cuántos bytes ocupan. */
    data class Summary(val count: Int, val bytes: Long)

    private data class Entry(val uri: Uri, val size: Long)

    /** Cuenta los bytes escritos, para conocer el tamaño sin volver a consultar. */
    private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            count += len
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    /**
     * Índice cacheado: [cacheKey] identifica el origen (la carpeta SAF, o
     * `"local"`), y [cache] mapea `videoId` → fichero (URI + tamaño).
     */
    @Volatile private var cacheKey: String? = null
    @Volatile private var cache: Map<String, Entry> = emptyMap()

    /**
     * URI reproducible del audio de [videoId] si está descargado, o `null`.
     * Para SAF es un `content://` del documento; para el respaldo local, un
     * `file://`. Ambos los reproduce Media3.
     */
    fun localUri(context: Context, videoId: String): Uri? {
        if (videoId.isBlank()) return null
        return downloaded(context)[videoId]?.uri
    }

    /**
     * Escribe el audio de [videoId] a un destino temporal y lo renombra al
     * terminar, de modo que un fallo o una cancelación no deja un `.m4a` a
     * medias que luego parezca "descargado".
     *
     * [produce] recibe el stream de salida abierto y debe escribir los bytes;
     * si lanza, el temporal se borra y el fichero no queda registrado.
     * Bloqueante: llamar bajo `Dispatchers.IO`.
     */
    fun write(context: Context, videoId: String, produce: (OutputStream) -> Unit): Boolean {
        val name = "$videoId.$EXTENSION"
        val tree = SafFiles.tree(context)
        val entry = if (tree != null) {
            writeSaf(context, tree, name, produce)
        } else {
            writeFile(context, name, produce)
        }
        if (entry != null && cacheKey == (tree?.toString() ?: LOCAL_KEY)) {
            cache = cache + (videoId to entry)
        }
        return entry != null
    }

    /** Cuántas pistas hay descargadas y cuánto ocupan (para el diálogo de gestión). */
    fun summary(context: Context): Summary =
        downloaded(context).values.let { entries ->
            Summary(entries.size, entries.sumOf { it.size })
        }

    /** Borra el audio de [videoId] si existe. Devuelve si había algo que borrar. */
    fun delete(context: Context, videoId: String): Boolean {
        val entry = downloaded(context)[videoId] ?: return false
        val deleted = if (entry.uri.scheme == ContentResolver.SCHEME_CONTENT) {
            SafFiles.delete(context, entry.uri)
        } else {
            entry.uri.path?.let { File(it).delete() } ?: false
        }
        if (deleted) cache = cache - videoId
        return deleted
    }

    /** Borra **todo** el audio offline. Devuelve si se pudo borrar todo. */
    fun deleteAll(context: Context): Boolean {
        val allDeleted = downloaded(context).values.map { entry ->
            if (entry.uri.scheme == ContentResolver.SCHEME_CONTENT) {
                SafFiles.delete(context, entry.uri)
            } else {
                entry.uri.path?.let { File(it).delete() } ?: false
            }
        }.all { it }
        cache = emptyMap()
        cacheKey = null
        return allDeleted
    }

    // === SAF ===

    private fun writeSaf(
        context: Context,
        tree: Uri,
        name: String,
        produce: (OutputStream) -> Unit
    ): Entry? {
        val rootId = SafFiles.treeId(tree) ?: return null
        val dirId = SafFiles.ensureDir(context, tree, rootId, DIR_NAME) ?: return null
        var temp: Uri? = null
        return try {
            temp = createTempDocument(context, tree, dirId, "$name.part") ?: return null
            val stream = context.contentResolver.openOutputStream(temp, "w")
                ?: run {
                    SafFiles.delete(context, temp)
                    return null
                }
            val counter = CountingOutputStream(stream)
            counter.use { out -> produce(out) }
            val renamed = SafFiles.rename(context, temp, name)
            if (renamed == null) {
                Log.e(TAG, "El proveedor no dejo renombrar $name")
                SafFiles.delete(context, temp)
                null
            } else {
                Entry(renamed, counter.count)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo audio en la carpeta de sync: ${e.message}")
            temp?.let { SafFiles.delete(context, it) }
            null
        }
    }

    /** Crea el documento temporal probando los MIME de audio habituales. */
    private fun createTempDocument(context: Context, tree: Uri, dirId: String, name: String): Uri? {
        for (mime in listOf(MIME_AUDIO, MIME_OCTET)) {
            SafFiles.createDocument(context, tree, dirId, name, mime)?.let { return it }
        }
        return null
    }

    // === Respaldo local (sin carpeta de sync) ===

    private fun localDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun writeFile(context: Context, name: String, produce: (OutputStream) -> Unit): Entry? {
        val dir = localDir(context)
        val temp = File(dir, "$name.part")
        return try {
            val counter = CountingOutputStream(temp.outputStream())
            counter.use { out -> produce(out) }
            if (counter.count == 0L) {
                temp.delete()
                null
            } else {
                val target = File(dir, name)
                if (temp.renameTo(target)) Entry(Uri.fromFile(target), target.length()) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo audio local: ${e.message}")
            temp.delete()
            null
        }
    }

    /**
     * Índice `videoId → fichero` de todo lo descargado, cacheado por origen. Se
     * reconstruye solo si cambia la carpeta (o su permiso) entre llamadas.
     */
    private fun downloaded(context: Context): Map<String, Entry> {
        val tree = SafFiles.tree(context)
        val key = tree?.toString() ?: LOCAL_KEY
        if (cacheKey == key) return cache

        val map: Map<String, Entry> = if (tree == null) {
            localDir(context).listFiles().orEmpty()
                .filter { it.name.endsWith(SUFFIX) }
                .associate { it.name.removeSuffix(SUFFIX) to Entry(Uri.fromFile(it), it.length()) }
        } else {
            val rootId = SafFiles.treeId(tree)
            val dirId = rootId?.let { SafFiles.children(context, tree, it)[DIR_NAME]?.id }
            val children = if (dirId == null) emptyMap() else SafFiles.children(context, tree, dirId)
            children.mapNotNull { (name, child) ->
                if (!name.endsWith(SUFFIX)) return@mapNotNull null
                SafFiles.docUri(tree, child.id)?.let { name.removeSuffix(SUFFIX) to Entry(it, child.size) }
            }.toMap()
        }

        cache = map
        cacheKey = key
        return map
    }
}
