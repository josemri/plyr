package com.plyr.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
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
 */
object DownloadedAudioStore {

    private const val TAG = "DownloadedAudioStore"
    private const val DIR_NAME = "audio"
    private const val EXTENSION = "m4a"
    private const val MIME_AUDIO = "audio/mp4"
    private const val MIME_OCTET = "application/octet-stream"
    private const val MIME_DIR = "vnd.android.document/directory"

    /** Nombre del fichero de audio de un vídeo. */
    fun fileName(videoId: String): String = "$videoId.$EXTENSION"

    /** ¿Existe ya el audio de [videoId] en el almacén? */
    fun contains(context: Context, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val name = fileName(videoId)
        val tree = backupTree(context)
        if (tree != null) {
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return false
            val dirId = findChildId(context, tree, rootId, DIR_NAME) ?: return false
            return findChildId(context, tree, dirId, name) != null
        }
        return File(localDir(context), name).exists()
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
        val name = fileName(videoId)
        val tree = backupTree(context)
        return if (tree != null) {
            writeSaf(context, tree, name, produce)
        } else {
            writeFile(context, name, produce)
        }
    }

    // === SAF ===

    private fun writeSaf(
        context: Context,
        tree: Uri,
        name: String,
        produce: (OutputStream) -> Unit
    ): Boolean {
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return false
        val dirId = ensureAudioDir(context, tree, rootId) ?: return false
        val tempUri = createDocument(resolver, tree, dirId, name + ".part") ?: return false
        return try {
            resolver.openOutputStream(tempUri, "w")?.use { out -> produce(out) }
                ?: run {
                    runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
                    return false
                }
            val renamed = runCatching { DocumentsContract.renameDocument(resolver, tempUri, name) }.getOrNull()
            if (renamed == null) {
                Log.e(TAG, "El proveedor no dejo renombrar $name")
                runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
                return false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo audio en la carpeta de sync: ${e.message}")
            runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
            false
        }
    }

    private fun ensureAudioDir(context: Context, tree: Uri, rootId: String): String? {
        findChildId(context, tree, rootId, DIR_NAME)?.let { return it }
        val parent = runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, rootId) }.getOrNull() ?: return null
        val created = runCatching {
            DocumentsContract.createDocument(context.contentResolver, parent, MIME_DIR, DIR_NAME)
        }.getOrNull() ?: return null
        return runCatching { DocumentsContract.getDocumentId(created) }.getOrNull()
    }

    private fun createDocument(resolver: ContentResolver, tree: Uri, parentId: String, name: String): Uri? {
        val parent = runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, parentId) }.getOrNull() ?: return null
        for (mime in listOf(MIME_AUDIO, MIME_OCTET)) {
            runCatching { DocumentsContract.createDocument(resolver, parent, mime, name) }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }

    private fun findChildId(context: Context, tree: Uri, parentId: String, name: String): String? {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        }.getOrNull() ?: return null
        return try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (idColumn < 0 || nameColumn < 0) return null
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == name) return cursor.getString(idColumn)
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo listar la carpeta de audio: ${e.message}")
            null
        }
    }

    /** Carpeta de sync utilizable, o null para caer al respaldo local. */
    private fun backupTree(context: Context): Uri? {
        val stored = Config.getBackupTreeUri(context) ?: return null
        val tree = stored.toUri()
        return if (BackupFolder.hasPersistedAccess(context, tree)) tree else null
    }

    // === Respaldo local (sin carpeta de sync) ===

    private fun localDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun writeFile(
        context: Context,
        name: String,
        produce: (OutputStream) -> Unit
    ): Boolean {
        val dir = localDir(context)
        val temp = File(dir, "$name.part")
        return try {
            temp.outputStream().use { out -> produce(out) }
            if (temp.length() == 0L) {
                temp.delete()
                false
            } else {
                temp.renameTo(File(dir, name))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo audio local: ${e.message}")
            temp.delete()
            false
        }
    }
}
