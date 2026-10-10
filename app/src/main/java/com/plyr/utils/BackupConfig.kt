package com.plyr.utils

import android.content.Context
import androidx.core.content.edit

/**
 * Preferencias de la copia de seguridad automática (carpeta SAF, documento
 * cacheado y huella del último contenido escrito).
 *
 * Persistidas en el fichero compartido de [ConfigPrefs]. Ver `DataSync`.
 */
object BackupConfig {

    // Carpeta SAF elegida, documento interno cacheado y huella del último
    // contenido escrito (ver `DataSync`).
    private const val KEY_BACKUP_TREE_URI = "backup_tree_uri"
    private const val KEY_BACKUP_DOC_ID = "backup_doc_id"
    private const val KEY_BACKUP_HASH = "backup_hash"

    /**
     * Carpeta SAF donde la app mantiene `plyr-sync.zip` actualizado.
     *
     * Es un *árbol* (`OpenDocumentTree`), no un documento: el permiso que se
     * toma sobre él es persistente y sobrevive a reinicios, así que la app
     * puede reescribir el archivo por su cuenta sin volver a preguntar nada.
     * Null si el usuario aún no ha elegido carpeta.
     */
    fun getBackupTreeUri(context: Context): String? =
        ConfigPrefs.get(context).getString(KEY_BACKUP_TREE_URI, null)

    /**
     * Fija la carpeta de copia de seguridad. `documentId` es el documento hijo
     * ya cacheado dentro de ese árbol (o null si aún no existe, en cuyo caso
     * `BackupFolder` lo creará). Se guardan juntos porque el `documentId` solo
     * tiene sentido dentro de su árbol.
     */
    fun setBackupTree(context: Context, treeUri: String, documentId: String?) {
        ConfigPrefs.get(context).edit {
            putString(KEY_BACKUP_TREE_URI, treeUri)
            if (documentId != null) putString(KEY_BACKUP_DOC_ID, documentId) else remove(KEY_BACKUP_DOC_ID)
            // El contenido escrito pertenece a la carpeta anterior: no vale
            // para la nueva, así que se olvida la huella.
            remove(KEY_BACKUP_HASH)
        }
    }

    /**
     * `documentId` cacheado del archivo de copia dentro del árbol elegido, o
     * null si todavía no se ha creado. Evita consultar al proveedor en cada
     * sincronización.
     */
    fun getBackupDocumentId(context: Context): String? =
        ConfigPrefs.get(context).getString(KEY_BACKUP_DOC_ID, null)

    fun setBackupDocumentId(context: Context, documentId: String) {
        ConfigPrefs.get(context).edit { putString(KEY_BACKUP_DOC_ID, documentId) }
    }

    /**
     * Huella SHA-256 de lo último escrito en la carpeta. Sirve para no reescribir
     * el ZIP cuando los datos no han cambiado desde la última sincronización.
     */
    fun getBackupHash(context: Context): String? =
        ConfigPrefs.get(context).getString(KEY_BACKUP_HASH, null)

    fun setBackupHash(context: Context, hash: String) {
        ConfigPrefs.get(context).edit { putString(KEY_BACKUP_HASH, hash) }
    }

    /**
     * Si la app debe volcar los cambios a la carpeta por su cuenta al salir.
     *
     * No hay interruptor: basta con que haya carpeta. Tenerla *es* la
     * decisión del usuario, y un flag aparte solo servía para poder
     * desincronizar, que ya no se ofrece en la interfaz.
     */
    fun isAutoSyncEnabled(context: Context): Boolean =
        ConfigPrefs.get(context).getString(KEY_BACKUP_TREE_URI, null) != null

    /**
     * Olvida la carpeta de copia: se usa cuando el permiso SAF deja de ser
     * válido (el usuario la borró o revocó el acceso) para no reintentar en
     * bucle contra un árbol inalcanzable.
     */
    fun clearBackupTree(context: Context) {
        ConfigPrefs.get(context).edit {
            remove(KEY_BACKUP_TREE_URI)
            remove(KEY_BACKUP_DOC_ID)
            remove(KEY_BACKUP_HASH)
        }
    }
}
