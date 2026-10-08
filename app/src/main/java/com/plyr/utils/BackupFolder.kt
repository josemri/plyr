package com.plyr.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.provider.DocumentsContract
import android.util.Log
import java.io.IOException

/**
 * BackupFolder - Acceso persistente a una **carpeta** elegida por el usuario
 * mediante SAF (`OpenDocumentTree`).
 *
 * Es la diferencia entre el botón de exportar de siempre y la copia automática:
 *
 * - `CreateDocument` da permiso de un solo uso sobre **un archivo**. Se pierde al
 *   reiniciar y no hay forma de "guardar la ruta" para reescribir después.
 * - `OpenDocumentTree` da permiso de lectura y escritura sobre **todo un árbol
 *   de directorios**, y `takePersistableUriPermission` lo vuelve duradero. A
 *   partir de ahí la app puede reescribir el archivo por su cuenta, sin volver a
 *   molestar al usuario.
 *
 * Google Drive expone un `DocumentsProvider`, así que una carpeta de Drive
 * sirve igual que una local.
 *
 * El archivo se llama siempre [BACKUP_FILE_NAME]: no se generan copias con
 * fecha porque el objetivo es tener *un* archivo que se actualiza solo, no un
 * histórico. Para eso está el botón de exportar manual.
 */
object BackupFolder {

    private const val TAG = "BackupFolder"

    /** Nombre del archivo de copia dentro de la carpeta elegida. */
    const val BACKUP_FILE_NAME = "plyr-sync.zip"

    /** Nombre temporal mientras se escribe; se renombra al terminar. */
    const val STAGING_FILE_NAME = "plyr-sync.zip.part"

    /** Nombre del archivo anterior durante el intercambio, por si hay que volver atrás. */
    const val PREVIOUS_FILE_NAME = "plyr-sync.zip.old"

    private const val MIME_ZIP = "application/zip"
    private const val MIME_OCTET = "application/octet-stream"

    /**
     * Los flags de permiso persistente. `ACTION_OPEN_DOCUMENT_TREE` concede
     * lectura y escritura sobre el árbol, y ambos hay que re-declarar para que
     * el acceso sobreviva a un reinicio.
     */
    const val PERSISTABLE_FLAGS: Int =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /**
     * Fijate en [STAGING_FILE_NAME] y [PREVIOUS_FILE_NAME] para distinguir un
     * archivo nuestro de uno ajeno. Extraído para poder testearlo sin Android.
     */
    fun isManagedFile(displayName: String?): Boolean =
        displayName == BACKUP_FILE_NAME ||
            displayName == STAGING_FILE_NAME ||
            displayName == PREVIOUS_FILE_NAME

    // === PERMISO ===

    /**
     * Convierte el Uri devuelto por el selector de carpeta en un permiso que
     * sobrevive a reinicios. Devuelve false si el sistema no lo concede (al
     *gunos proveedores de terceros no soportan permisos persistentes).
     */
    fun persistAccess(context: Context, treeUri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(treeUri, PERSISTABLE_FLAGS)
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "El proveedor no concede permiso persistente: ${e.message}")
        false
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo tomar el permiso de la carpeta: ${e.message}")
        false
    }

    /**
     * Si el permiso sobre [treeUri] sigue vivo. Sirve para distinguir "el
     * usuario revocó el acceso" de "el proveedor falló": lo primero deja de
     * reintentar para siempre, lo segundo se reintenta más tarde.
     */
    fun hasPersistedAccess(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    /** Suelta el permiso de la carpeta. Se llama al cambiarla o al desactivarla. */
    fun releaseAccess(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(treeUri, PERSISTABLE_FLAGS)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo liberar el permiso de la carpeta: ${e.message}")
        }
    }

    // === RESOLUCIÓN DEL ARCHIVO ===

    /**
     * Devuelve el Uri del archivo de copia **solo si ya existe** en [treeUri], o
     * null si no está.
     *
     * Es la contraparte de [resolveBackupFile], que crea el archivo si falta.
     * La usa el botón de sincronizar para distinguir "tengo dónde escribir" de
     * "el usuario borró el archivo y tengo que volver a preguntarle dónde": crear
     * el archivo sin permiso dejaría un ZIP vacío donde antes había una copia
     * buena.
     */
    fun findExistingBackupFile(context: Context, treeUri: Uri): Uri? {
        val resolver = context.contentResolver

        Config.getBackupDocumentId(context)?.let { cachedId ->
            runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, cachedId) }
                .getOrNull()
                ?.takeIf { exists(resolver, it) }
                ?.let { return it }
        }

        val foundId = findChild(resolver, treeUri, BACKUP_FILE_NAME) ?: return null
        Config.setBackupDocumentId(context, foundId)
        return runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, foundId) }
            .getOrNull()
    }

    /**
     * Devuelve el Uri del archivo de copia dentro de [treeUri], creándolo si
     * hace falta, o null si el proveedor falla.
     *
     * Primero intenta el `documentId` cacheado (sin coste de red); si ya no
     * existe —porque el usuario lo borró desde Drive— lo busca por nombre y, si
     * tampoco está, lo crea. Así el archivo se regenera solo si alguien lo
     * borra a mano.
     */
    fun resolveBackupFile(context: Context, treeUri: Uri): Uri? {
        val resolver = context.contentResolver

        Config.getBackupDocumentId(context)?.let { cachedId ->
            runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, cachedId) }
                .getOrNull()
                ?.takeIf { exists(resolver, it) }
                ?.let { return it }
            Log.d(TAG, "El documento cacheado ya no existe; se buscara por nombre")
        }

        findChild(resolver, treeUri, BACKUP_FILE_NAME)?.let { childId ->
            Config.setBackupDocumentId(context, childId)
            return runCatching { DocumentsContract.buildDocumentUriUsingTree(treeUri, childId) }
                .getOrNull()
        }

        return createChild(context, treeUri, BACKUP_FILE_NAME)
    }

    /**
     * Crea [displayName] en [treeUri] y devuelve su Uri, o null si el proveedor
     * falla. Usa el MIME de ZIP con un respaldo a binario genérico: algunos
     * proveedores (Drive incluido) rechazan tipos que no reconocen.
     *
     * [cache] guarda el nuevo id como documento de copia. Se pone a false para
     * el archivo temporal, que no debe dejar su id en la caché: si la
     * sincronización falla a medias y se borra, quedaría apuntando al vacío.
     */
    fun createChild(
        context: Context,
        treeUri: Uri,
        displayName: String,
        cache: Boolean = true
    ): Uri? {
        val parent = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        }.getOrNull() ?: return null

        val resolver = context.contentResolver
        for (mime in listOf(MIME_ZIP, MIME_OCTET)) {
            val created = try {
                DocumentsContract.createDocument(resolver, parent, mime, displayName)
            } catch (e: Exception) {
                Log.w(TAG, "createDocument($mime) fallo: ${e.message}")
                null
            }
            if (created != null) {
                Log.d(TAG, "Creado $displayName en la carpeta de copia")
                if (cache) {
                    runCatching { DocumentsContract.getDocumentId(created) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { Config.setBackupDocumentId(context, it) }
                }
                return created
            }
        }
        Log.e(TAG, "El proveedor no dejo crear $displayName en la carpeta")
        return null
    }

    /** Busca un hijo directo de [treeUri] por nombre exacto, o null. */
    fun findChild(resolver: android.content.ContentResolver, treeUri: Uri, displayName: String): String? {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
        }.getOrNull() ?: return null

        return try {
            resolver.query(
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
                    if (cursor.getString(nameColumn) == displayName) return cursor.getString(idColumn)
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo listar la carpeta: ${e.message}")
            null
        }
    }

    /**
     * Si el documento sigue ahí. Se comprueba abriéndolo en lectura porque es
     * lo único que garantiza todos los proveedores: un `documentId` cacheado
     * puede seguir siendo válido en el Uri y aun así apuntar a nada si el
     * usuario borró el archivo desde Drive.
     */
    private fun exists(resolver: android.content.ContentResolver, documentUri: Uri): Boolean = try {
        resolver.openInputStream(documentUri)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    /** Lanza [IOException] con un mensaje entendible si el Uri no se puede usar. */
    fun requireTree(context: Context): Uri {
        val stored = Config.getBackupTreeUri(context)
            ?: throw IOException("No hay carpeta de copia de seguridad configurada")
        val treeUri = stored.toUri()
        if (!hasPersistedAccess(context, treeUri)) {
            // El permiso se perdió: se olvida la carpeta para no reintentar solos.
            Config.clearBackupTree(context)
            throw IOException("El acceso a la carpeta de copia ha caducado")
        }
        return treeUri
    }
}
