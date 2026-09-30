package com.plyr.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Resultado de intentar volcar los cambios a la carpeta de copia. */
sealed interface SyncResult {
    /**
     * Se escribió un archivo nuevo.
     *
     * [merged] es lo que entró en el dispositivo desde la copia anterior de la
     * carpeta, cuando la sincronización trajo algo de ahí ([ArchiveUnreadable]
     * es el caso contrario: no se pudo leer y no se escribió nada).
     */
    data class Written(
        val summary: ExportSummary,
        val merged: ImportSummary? = null
    ) : SyncResult

    /** Los datos no habían cambiado desde la última vez: no se tocó nada. */
    data object UpToDate : SyncResult

    /** No hay carpeta de copia configurada todavía. */
    data object NotConfigured : SyncResult

    /**
     * El archivo de la carpeta existe pero no se pudo leer. No se escribe
     * nada: podría ser la única copia de los datos.
     */
    data class ArchiveUnreadable(val error: Throwable) : SyncResult

    /**
     * Falló la escritura. Solo lo ve el botón de sincronizar; la copia
     * automática lo ignora a propósito (ver [DataSync.flush]).
     */
    data class Failed(val error: Throwable) : SyncResult
}

/**
 * Hay un archivo en la carpeta y no se puede leer. Se distingue del fallo de
 * escritura para poder dejar la copia vieja intacta en vez de pisarla sin haber
 * visto qué había dentro.
 */
private class UnreadableArchiveException(cause: Throwable) : Exception(cause)

/**
 * DataSync - Mantiene un único archivo de copia de seguridad siempre al día en
 * una carpeta que el usuario eligió una vez.
 *
 * La idea: en vez de pedirle un archivo cada vez que toca exportar, la app
 * elige **una carpeta** y mantiene dentro `plyr-sync.zip` al día por su cuenta.
 * Sirve lo mismo para una carpeta de Drive que para una local, y no exige
 * ninguna acción del usuario después de configurarlo.
 *
 * ## Cuándo escribe
 *
 * Ni por cada acción ni en segundo plano. Se marca "sucio" cuando cambian los
 * datos (ver [markDirty]) y se vuelca
 * - pasado un [DEBOUNCE_MS] sin más cambios, para no escribir si el usuario
 *   solo está toqueteando la interfaz, y
 * - al salir de la app ([flushOnStop], desde `MainActivity.onStop`), que es el
 *   momento en que el proceso sigue vivo y hay red.
 *
 * Se omite la escritura si la huella del contenido no ha cambiado: escribir en
 * Drive son varias llamadas de red y no puede ser gratis.
 *
 * ## Qué hace con la copia que ya estaba
 *
 * Sincronizar va en los dos sentidos. Antes de escribir, el archivo de la
 * carpeta se lee y lo que haya en él que la app no tenga entra en el
 * dispositivo (ver [mergeArchiveFromFolder]); después se exporta el estado ya
 * fusionado, de modo que el archivo y la app terminan con todo.
 *
 * Sin ese paso, instalar la app de cero y pulsar el botón—sobre todo,
 * eligiendo la carpeta de siempre— pisaba la copia buena con una copia vacía:
 * una instalación nueva solo tiene `liked_songs` sin canciones, y el archivo
 * anterior se renombraba a `.old` y se borraba sin llegar a leerlo.
 *
 * La fusión solo añade: las listas que ya están en el dispositivo no se tocan y
 * `liked_songs` se une a los favoritos actuales sin duplicar. Una excepción son
 * las listas borradas (tombs): borrar queda registrado y viaja en el archivo,
 * así un borrado hecho aquí se aplica en cualquier otro dispositivo y no
 * resucita al sincronizar. Y si el archivo no se puede leer, no se escribe nada.
 *
 * ## Cómo escribe
 *
 * Nunca directamente sobre el archivo bueno. Se escribe primero en
 * `plyr-sync.zip.part` y solo cuando está completo se renombra a su sitio
 * definitivo. Un ZIP a medio escribir —la app se cierra, se queda sin red, el
 * móvil se apaga— destruiría la copia de seguridad, que es justo lo que este
 * mecanismo existe para no perder.
 */
object DataSync {

    private const val TAG = "DataSync"

    /** Espera a que se calme la actividad antes de escribir. */
    const val DEBOUNCE_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Serializa las escrituras. Sin esto, el debounce y el `onStop` podrían
     * solaparse y pelearse por el mismo archivo temporal.
     */
    private val writeLock = Mutex()

    private val isDirty = AtomicBoolean(false)
    private var debounceJob: Job? = null

    /**
     * Se llama desde cualquier punto que modifique las listas. No hace E/S ni
     * toca el disco: solo programa el volcado, que es lo que permite llamarlo
     * sin miedo desde el repositorio.
     */
    fun markDirty(context: Context) {
        if (!Config.isAutoSyncEnabled(context)) return

        isDirty.set(true)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            flush(context, force = false)
        }
    }

    /**
     * Vuelca los cambios a la carpeta. Pensado para `MainActivity.onStop`.
     *
     * Espera al volcado pendiente en vez de cancelarlo: `onStop` es el último
     * momento fiable antes de que el proceso pueda morir, así que aquí no
     * interesa ir rápido sino llegar a escribir.
     */
    fun flushOnStop(context: Context) {
        if (!Config.isAutoSyncEnabled(context)) return

        val pending = debounceJob
        scope.launch {
            pending?.join()
            if (isDirty.get()) flush(context, force = false)
        }
    }

    /**
     * Escribe la copia ahora. [force] ignora la comparación de huellas, para el
     * botón de sincronizar de los ajustes, donde el usuario quiere
     * confirmación de que se ha escrito.
     */
    suspend fun flush(context: Context, force: Boolean): SyncResult = writeLock.withLock {
        val appContext = context.applicationContext
        if (!Config.isAutoSyncEnabled(appContext)) return@withLock SyncResult.NotConfigured

        try {
            val treeUri = BackupFolder.requireTree(appContext)

            // null si la app no tiene ninguna lista. El archivo de la carpeta
            // puede traerlas de vuelta, así que aún no se rinde aquí.
            val current = try {
                DataExporter.buildBundle(appContext)
            } catch (e: EmptyExportException) {
                null
            }

            if (current != null && !force && Config.getBackupHash(appContext) == current.contentHash) {
                Log.d(TAG, "La copia ya estaba al dia")
                isDirty.set(false)
                return@withLock SyncResult.UpToDate
            }

            // La fusión va antes que la huella: importar cambia el estado local,
            // que es lo que luego se exporta.
            //
            // NonCancellable: importar marca los datos como sucios y eso cancela
            // el trabajo de debounce, que puede ser el que esté ejecutando esta
            // misma sincronización (markDirty -> flush). Cancelado en mitad de la
            // fusión, la llamada a buildBundle de abajo abortaría la escritura.
            val merge = withContext(NonCancellable) { mergeArchiveFromFolder(appContext, treeUri, current) }

            val bundle = merge?.bundle ?: current
            if (bundle == null) {
                // Ni la app ni la carpeta tienen listas que copiar.
                Log.d(TAG, "No hay listas que sincronizar")
                isDirty.set(false)
                return@withLock SyncResult.UpToDate
            }

            val documentUri = BackupFolder.resolveBackupFile(appContext, treeUri)
                ?: throw IOException("No se pudo abrir el archivo de copia en la carpeta")

            val newDocumentId = writeAtomically(appContext, documentUri, treeUri, bundle)
            Config.setBackupDocumentId(appContext, newDocumentId)
            Config.setBackupHash(appContext, bundle.contentHash)
            isDirty.set(false)

            Log.d(TAG, "Copia actualizada: ${bundle.playlistCount} listas, ${bundle.trackCount} pistas")
            SyncResult.Written(
                ExportSummary(
                    playlistCount = bundle.playlistCount,
                    trackCount = bundle.trackCount,
                    coverCount = bundle.covers.size
                ),
                merged = merge?.takeIf { it.summary.broughtAnything() }?.summary
            )
        } catch (e: UnreadableArchiveException) {
            // Se prefiere la copia antigua, que puede ser la única, a escribir
            // encima sin haber podido leer qué había dentro.
            Log.w(TAG, "No se pudo leer la copia de la carpeta; no se escribe: ${e.cause?.message}")
            SyncResult.ArchiveUnreadable(e.cause ?: e)
        } catch (e: Exception) {
            // Un fallo de red no puede escalar a un crash ni dejar al usuario
            // con un error en pantalla: la copia es una función de fondo. Se
            // reintenta en el siguiente cambio o al volver a abrir la app.
            Log.w(TAG, "No se pudo actualizar la copia: ${e.message}")
            SyncResult.Failed(e)
        }
    }

    /**
     * Lo que entró en el dispositivo desde la copia anterior de la carpeta.
     */
    private class ArchiveMerge(
        val summary: ImportSummary,
        val bundle: ExportBundle
    )

    /**
     * Lee el archivo de la carpeta y deja en la app todo lo que hubiera en él y
     * la app no tuviera. Cuando hay algo que traer, [ArchiveMerge.bundle] refleja
     * el estado ya fusionado (se vuelve a recoger); si no, se reutiliza
     * [current], que sigue siendo válido.
     *
     * Devuelve null si no hay archivo en la carpeta, o si existe pero no es una
     * copia de plyr: no hay nada que fusionar y el archivo lleva el nombre de la
     * app, así que se puede reescribir.
     *
     * La fusión sigue la política de [DataImporter], pensada para repetirse sin
     * daño: las listas que ya están en el dispositivo se dejan intactas (la app
     * gana), `liked_songs` se une a los favoritos actuales sin duplicar y las
     * filas legacy `album_*` se ignoran.
     *
     * @throws UnreadableArchiveException si el archivo existe pero no se puede
     *   leer (sin red, permiso, ZIP a medias). Entonces no se debe escribir
     *   nada: el archivo viejo puede ser la única copia de los datos.
     */
    private suspend fun mergeArchiveFromFolder(
        context: Context,
        treeUri: Uri,
        current: ExportBundle?
    ): ArchiveMerge? {
        val documentUri = BackupFolder.findExistingBackupFile(context, treeUri) ?: return null

        val result = DataImporter.importFrom(context, documentUri)
        val summary = result.getOrNull()
        if (summary == null) {
            val cause = result.exceptionOrNull() ?: IOException("Fallo desconocido al leer la copia")
            if (cause !is ManifestFormatException) throw UnreadableArchiveException(cause)
            Log.w(TAG, "El archivo de la carpeta no es una copia de plyr; se reescribe: ${cause.message}")
            return null
        }

        if (!summary.broughtAnything()) {
            // No había nada que traer: el estado ya recogido sigue valiendo.
            return current?.let { ArchiveMerge(summary, it) }
        }

        val merged = try {
            DataExporter.buildBundle(context)
        } catch (e: EmptyExportException) {
            return null
        }

        Log.d(
            TAG,
            "Copia de la carpeta fusionada: ${summary.importedPlaylists} listas nuevas, " +
                "${summary.mergedLikedTracks} favoritos"
        )
        return ArchiveMerge(summary, merged)
    }

    /**
     * Escribe en un temporal y lo pasa a su nombre definitivo, para que el
     * archivo bueno nunca esté a medio escribir.
     *
     * @return el `documentId` del documento que queda como copia buena, que no
     *   siempre es [documentUri]: si el intercambio fue por renombrado, el
     *   archivo bueno es el temporal con otro nombre y conserva su id.
     */
    private fun writeAtomically(
        context: Context,
        documentUri: Uri,
        treeUri: Uri,
        bundle: ExportBundle
    ): String {
        val resolver = context.contentResolver
        val stagingUri = BackupFolder.createChild(
            context = context,
            treeUri = treeUri,
            displayName = BackupFolder.STAGING_FILE_NAME,
            cache = false
        ) ?: throw IOException("No se pudo crear el archivo temporal en la carpeta")
        val stagingId = stagingUri.documentIdOrEmpty()

        try {
            val out = resolver.openOutputStream(stagingUri, "wt")
                ?: throw IOException("No se pudo escribir en el archivo temporal")
            out.use { DataExporter.writeTo(it, bundle) }

            return swapIntoPlace(context, stagingUri, documentUri)
        } catch (e: Exception) {
            // El temporal a medias se borra para no dejar basura en la carpeta.
            runCatching { DocumentsContract.deleteDocument(resolver, stagingUri) }
                .onFailure { Log.w(TAG, "No se pudo limpiar el temporal: ${it.message}") }
            throw e
        }
    }

    /**
     * Pasa el temporal a su nombre definitivo y devuelve el `documentId` del
     * archivo bueno.
     *
     * El archivo viejo se aparta como `.old` en vez de borrarse directamente,
     * para poder volver atrás si el renombrado falla a medias. Se intenta
     * primero renombrar, que es una operación de metadatos barata; si el
     * proveedor no lo soporta se cae a copiar los bytes, que siempre funciona
     * aunque sea más lento.
     */
    private fun swapIntoPlace(
        context: Context,
        stagingUri: Uri,
        targetUri: Uri
    ): String {
        val resolver = context.contentResolver

        val previousUri = runCatching {
            DocumentsContract.renameDocument(resolver, targetUri, BackupFolder.PREVIOUS_FILE_NAME)
        }.getOrNull()

        val renamed = runCatching {
            DocumentsContract.renameDocument(resolver, stagingUri, BackupFolder.BACKUP_FILE_NAME)
        }.getOrNull() != null

        if (renamed) {
            // El temporal conserva su documentId al cambiar de nombre.
            previousUri?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            return stagingUri.documentIdOrEmpty()
        }

        // Sin renombrado: se copia el temporal sobre el destino, que sí
        // conserva su id porque el documento no cambia de nombre.
        Log.d(TAG, "El proveedor no permite renombrar; se copia el temporal")
        val staged = resolver.openInputStream(stagingUri)
            ?: throw IOException("No se pudo releer el temporal")
        val target = resolver.openOutputStream(targetUri, "wt")
            ?: throw IOException("No se pudo escribir en el archivo de copia")
        staged.use { input -> input.copyTo(target) }
        target.close()
        runCatching { DocumentsContract.deleteDocument(resolver, stagingUri) }
        return targetUri.documentIdOrEmpty()
    }

    // === RESTAURACIÓN ===

    /**
     * Importa la copia de la carpeta, la misma que la copia automática mantiene.
     * No muestra ningún selector: si hay carpeta, el archivo está ahí.
     */
    suspend fun importFromBackupFolder(context: Context): Result<ImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appContext = context.applicationContext
                val treeUri = BackupFolder.requireTree(appContext)
                val documentUri = BackupFolder.resolveBackupFile(appContext, treeUri)
                    ?: throw IOException("No hay ningun archivo de copia en esa carpeta")
                DataImporter.importFrom(appContext, documentUri).getOrThrow()
            }
        }

    // === UTILIDADES ===

    /** Si el archivo de la carpeta aportó algo que la app no tuviera. */
    private fun ImportSummary.broughtAnything(): Boolean =
        importedPlaylists > 0 || mergedLikedTracks > 0 || deletedPlaylists > 0

    private fun Uri.documentIdOrEmpty(): String =
        runCatching { DocumentsContract.getDocumentId(this) }.getOrNull().orEmpty()
}
