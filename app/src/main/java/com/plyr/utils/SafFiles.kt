package com.plyr.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri

/**
 * Primitivas del proveedor de documentos (SAF) para la carpeta de sync de plyr.
 *
 * Aisladas de [DownloadedAudioStore] para no mezclar el manejo de audio con el
 * papeleo de `DocumentsContract`, y para mantener cada objeto por debajo del
 * límite de funciones de detekt.
 */
internal object SafFiles {

    private const val TAG = "SafFiles"
    private const val MIME_DIR = "vnd.android.document/directory"

    /** Hijo de un documento: identificador y tamaño en bytes (0 si no lo da). */
    data class Child(val id: String, val size: Long)

    /** Carpeta de sync utilizable (con permiso persistido), o null. */
    fun tree(context: Context): Uri? {
        val stored = Config.getBackupTreeUri(context) ?: return null
        val tree = stored.toUri()
        return if (BackupFolder.hasPersistedAccess(context, tree)) tree else null
    }

    fun treeId(tree: Uri): String? =
        runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()

    /** Hijos directos de [parentId] como `nombre → child`. */
    fun children(context: Context, tree: Uri, parentId: String): Map<String, Child> {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        }.getOrNull() ?: return emptyMap()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE
        )
        return try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (idCol < 0 || nameCol < 0) return emptyMap()
                val result = HashMap<String, Child>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val size = if (sizeCol < 0 || cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol)
                    result[name] = Child(cursor.getString(idCol), size)
                }
                result
            } ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo listar $parentId: ${e.message}")
            emptyMap()
        }
    }

    /** Crea (si falta) la subcarpeta [name] y devuelve su id. */
    fun ensureDir(context: Context, tree: Uri, parentId: String, name: String): String? {
        children(context, tree, parentId)[name]?.let { return it.id }
        val created = createDocument(context, tree, parentId, name, MIME_DIR) ?: return null
        return runCatching { DocumentsContract.getDocumentId(created) }.getOrNull()
    }

    fun createDocument(
        context: Context,
        tree: Uri,
        parentId: String,
        name: String,
        mime: String
    ): Uri? {
        val parent = docUri(tree, parentId) ?: return null
        return runCatching {
            DocumentsContract.createDocument(context.contentResolver, parent, mime, name)
        }.getOrNull()
    }

    fun docUri(tree: Uri, docId: String): Uri? =
        runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, docId) }.getOrNull()

    fun rename(context: Context, uri: Uri, name: String): Uri? =
        runCatching { DocumentsContract.renameDocument(context.contentResolver, uri, name) }.getOrNull()

    fun delete(context: Context, uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
}
