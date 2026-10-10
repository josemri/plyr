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
 *
 * El índice de lo descargado se listan **una vez por sesión** y se cachea
 * ([downloadedMap]): comprobar cada pista al reproducir no debe costar una
 * consulta al proveedor (que para una carpeta de nube es una ida y vuelta).
 */
object DownloadedAudioStore {

    private const val TAG = "DownloadedAudioStore"
    private const val DIR_NAME = "audio"
    private const val EXTENSION = "m4a"
    private const val SUFFIX = ".$EXTENSION"
    private const val MIME_AUDIO = "audio/mp4"
    private const val MIME_OCTET = "application/octet-stream"
    private const val MIME_DIR = "vnd.android.document/directory"
    private const val LOCAL_KEY = "local"

    /**
     * Índice cacheado: [cacheKey] identifica el origen (la carpeta SAF, o
     * `"local"`), y [cache] mapea `videoId` → URI reproducible del fichero.
     */
    @Volatile private var cacheKey: String? = null
    @Volatile private var cache: Map<String, Uri> = emptyMap()

    /** Nombre del fichero de audio de un vídeo. */
    fun fileName(videoId: String): String = "$videoId.$EXTENSION"

    /**
     * URI reproducible del audio de [videoId] si está descargado, o `null`.
     * Para SAF es un `content://` del documento; para el respaldo local, un
     * `file://`. Ambos los reproduce Media3.
     */
    fun localUri(context: Context, videoId: String): Uri? {
        if (videoId.isBlank()) return null
        return downloadedMap(context)[videoId]
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
        val uri = if (tree != null) {
            writeSaf(context, tree, name, produce)
        } else {
            writeFile(context, name, produce)
        }
        if (uri != null && cacheKey == (tree?.toString() ?: LOCAL_KEY)) {
            cache = cache + (videoId to uri)
        }
        return uri != null
    }

    // === SAF ===

    private fun writeSaf(
        context: Context,
        tree: Uri,
        name: String,
        produce: (OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return null
        val dirId = ensureAudioDir(context, tree, rootId) ?: return null
        val tempUri = createDocument(resolver, tree, dirId, name + ".part") ?: return null
        return try {
            resolver.openOutputStream(tempUri, "w")?.use { out -> produce(out) }
                ?: run {
                    runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
                    return null
                }
            val renamed = runCatching { DocumentsContract.renameDocument(resolver, tempUri, name) }.getOrNull()
            if (renamed == null) {
                Log.e(TAG, "El proveedor no dejo renombrar $name")
                runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
                null
            } else {
                renamed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo audio en la carpeta de sync: ${e.message}")
            runCatching { DocumentsContract.deleteDocument(resolver, tempUri) }
            null
        }
    }

    private fun ensureAudioDir(context: Context, tree: Uri, rootId: String): String? {
        listChildren(context, tree, rootId)[DIR_NAME]?.let { return it }
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

    /** Hijos directos de [parentId] como `nombre → documentId`. */
    private fun listChildren(context: Context, tree: Uri, parentId: String): Map<String, String> {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        }.getOrNull() ?: return emptyMap()
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
                if (idColumn < 0 || nameColumn < 0) return emptyMap()
                val children = HashMap<String, String>()
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn) ?: continue
                    children[name] = cursor.getString(idColumn)
                }
                children
            } ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo listar la carpeta de audio: ${e.message}")
            emptyMap()
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

    private fun writeFile(context: Context, name: String, produce: (OutputStream) -> Unit): Uri? {
        val dir = localDir(context)
        val temp = File(dir, "$name.part")
        return try {
            temp.outputStream().use { out -> produce(out) }
            if (temp.length() == 0L) {
                temp.delete()
                null
            } else {
                val target = File(dir, name)
                if (temp.renameTo(target)) Uri.fromFile(target) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo audio local: ${e.message}")
            temp.delete()
            null
        }
    }

    /**
     * Índice `videoId → URI` de todo lo descargado, cacheado por origen. Se
     * reconstruye solo si cambia la carpeta (o su permiso) entre llamadas.
     */
    private fun downloadedMap(context: Context): Map<String, Uri> {
        val tree = backupTree(context)
        val key = tree?.toString() ?: LOCAL_KEY
        if (cacheKey == key) return cache

        val map: Map<String, Uri> = if (tree == null) {
            localDir(context).listFiles().orEmpty()
                .filter { it.name.endsWith(SUFFIX) }
                .associate { it.name.removeSuffix(SUFFIX) to Uri.fromFile(it) }
        } else {
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            val dirId = rootId?.let { listChildren(context, tree, it)[DIR_NAME] }
            val children = if (dirId == null) emptyMap() else listChildren(context, tree, dirId)
            children.mapNotNull { (name, docId) ->
                if (!name.endsWith(SUFFIX)) return@mapNotNull null
                runCatching { DocumentsContract.buildDocumentUriUsingTree(tree, docId) }.getOrNull()
                    ?.let { name.removeSuffix(SUFFIX) to it }
            }.toMap()
        }

        cache = map
        cacheKey = key
        return map
    }
}
