package com.plyr.desktop.sync

import com.plyr.core.util.Logger
import com.plyr.utils.ManifestFormatException
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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val TAG = "DataSync"

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
        val merged: ImportSummary? = null,
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
 * DataSync - Mantiene un único archivo de copia de seguridad siempre al día en
 * una carpeta que el usuario eligió una vez.
 *
 * Es el equivalente de escritorio de `DataSync` de Android, pero sobre `java.nio`
 * en vez de SAF. La carpeta puede ser una ruta local o una carpeta sincronizada
 * por el SO (Drive, Dropbox, OneDrive), que se comporta igual.
 *
 * ## Cuándo escribe
 *
 * Se marca "sucio" cuando cambian los datos (ver [markDirty]) y se vuelca
 * pasado un [debounceMs] sin más cambios, para no escribir si el usuario solo
 * está toqueteando la interfaz. Se omite la escritura si la huella del contenido
 * no ha cambiado, y se puede forzar desde los ajustes con [flush]`(force = true)`.
 *
 * ## Qué hace con la copia que ya estaba
 *
 * Sincronizar va en los dos sentidos: antes de escribir, el archivo de la carpeta
 * se lee y lo que haya en él que la app no tenga entra en el dispositivo antes de
 * volver a exportar el estado ya fusionado. Si el archivo no se puede leer, no se
 * escribe nada (podría ser la única copia).
 *
 * ## Cómo escribe
 *
 * Nunca directamente sobre el archivo bueno: se escribe en `plyr-sync.zip.part` y
 * solo cuando está completo se mueve a su sitio de forma atómica. Un ZIP a medio
 * escribir destruiría la copia de seguridad.
 */
class DataSync(
    private val exporter: DataExporter,
    private val importer: DataImporter,
    private val config: BackupConfig,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val debounceMs: Long = DEBOUNCE_MS,
) {

    /**
     * Serializa las escrituras. Sin esto, el debounce y un volcado manual podrían
     * solaparse y pelearse por el mismo archivo temporal.
     */
    private val writeLock = Mutex()

    private var debounceJob: Job? = null

    /** La copia automática está activa: hay carpeta elegida. */
    val isConfigured: Boolean get() = config.isConfigured

    /** Carpeta configurada, o null. */
    fun folder(): Path? = config.folderPath()

    /**
     * Fija (o cambia) la carpeta de copia. Si cambia, se olvida la huella para
     * forzar el primer volcado, y se programa el mismo (la UI suele pedir además
     * un `flush(force = true)` inmediato para dar confirmación al usuario).
     */
    fun setFolder(folder: Path) {
        val previous = config.folder
        config.folder = folder.toString()
        if (previous != config.folder) config.hash = null
        markDirty()
    }

    /**
     * Se llama desde cualquier punto que modifique las listas. No hace E/S ni
     * toca el disco: solo programa el volcado, que es lo que permite llamarlo
     * sin miedo desde el repositorio.
     */
    fun markDirty() {
        if (!isConfigured) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            flush(force = false)
        }
    }

    /**
     * Escribe la copia ahora. [force] ignora la comparación de huellas, para el
     * botón de sincronizar de los ajustes, donde el usuario quiere confirmación
     * de que se ha escrito.
     */
    suspend fun flush(force: Boolean): SyncResult = writeLock.withLock {
        if (!isConfigured) return@withLock SyncResult.NotConfigured

        try {
            val folder = config.folderPath()
                ?: return@withLock SyncResult.NotConfigured
            Files.createDirectories(folder)

            // null si la app no tiene ninguna lista. El archivo de la carpeta
            // puede traerlas de vuelta, así que aún no se rinde aquí.
            val current = try {
                exporter.buildBundle()
            } catch (e: EmptyExportException) {
                null
            }

            if (current != null && !force && config.hash == current.contentHash) {
                Logger.d(TAG, "La copia ya estaba al día")
                return@withLock SyncResult.UpToDate
            }

            // La fusión va antes que la huella: importar cambia el estado local,
            // que es lo que luego se exporta. NonCancellable porque importar marca
            // los datos como sucios y eso cancelaría este mismo volcado.
            val merge = withContext(NonCancellable) { mergeArchiveFromFolder(folder, current) }

            val bundle = merge?.bundle ?: current
            if (bundle == null) {
                // Ni la app ni la carpeta tienen listas que copiar.
                Logger.d(TAG, "No hay listas que sincronizar")
                return@withLock SyncResult.UpToDate
            }

            writeAtomically(folder, bundle)
            config.hash = bundle.contentHash

            Logger.d(
                TAG,
                "Copia actualizada: ${bundle.playlistCount} listas, ${bundle.trackCount} pistas",
            )
            SyncResult.Written(
                ExportSummary(
                    playlistCount = bundle.playlistCount,
                    trackCount = bundle.trackCount,
                    coverCount = bundle.covers.size,
                ),
                merged = merge?.takeIf { it.summary.broughtAnything() }?.summary,
            )
        } catch (e: UnreadableArchiveException) {
            // Se prefiere la copia antigua, que puede ser la única, a escribir
            // encima sin haber podido leer qué había dentro.
            Logger.w(TAG, "No se pudo leer la copia de la carpeta; no se escribe: ${e.cause?.message}")
            SyncResult.ArchiveUnreadable(e.cause ?: e)
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo actualizar la copia: ${e.message}")
            SyncResult.Failed(e)
        }
    }

    /**
     * Importa la copia de la carpeta, la misma que la copia automática mantiene.
     * No muestra ningún selector: si hay carpeta, el archivo está ahí.
     */
    suspend fun importFromBackupFolder(): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = config.folderPath()
                ?: throw IOException("No hay ninguna carpeta de copia configurada")
            val file = BackupFolder.backupFile(folder)
            if (!Files.isRegularFile(file)) {
                throw IOException("No hay ningún archivo de copia en esa carpeta")
            }
            importer.importFrom(file).getOrThrow()
        }
    }

    /** Cancela el scope interno (al salir de la app). */
    fun shutdown() {
        debounceJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    /**
     * Lo que entró en el dispositivo desde la copia anterior de la carpeta.
     */
    private class ArchiveMerge(
        val summary: ImportSummary,
        val bundle: ExportBundle,
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
     * @throws UnreadableArchiveException si el archivo existe pero no se puede
     *   leer (permisos, ZIP a medias). Entonces no se debe escribir nada.
     */
    private suspend fun mergeArchiveFromFolder(folder: Path, current: ExportBundle?): ArchiveMerge? {
        val file = BackupFolder.backupFile(folder)
        if (!Files.isRegularFile(file)) return null

        val result = importer.importFrom(file)
        val summary = result.getOrNull()
        if (summary == null) {
            val cause = result.exceptionOrNull() ?: IOException("Fallo desconocido al leer la copia")
            if (cause !is ManifestFormatException) throw UnreadableArchiveException(cause)
            Logger.w(TAG, "El archivo de la carpeta no es una copia de plyr; se reescribe: ${cause.message}")
            return null
        }

        if (!summary.broughtAnything()) {
            // No había nada que traer: el estado ya recogido sigue valiendo.
            return current?.let { ArchiveMerge(summary, it) }
        }

        val merged = try {
            exporter.buildBundle()
        } catch (e: EmptyExportException) {
            return null
        }

        Logger.d(
            TAG,
            "Copia de la carpeta fusionada: ${summary.importedPlaylists} listas nuevas, " +
                "${summary.mergedLikedTracks} favoritos",
        )
        return ArchiveMerge(summary, merged)
    }

    /**
     * Escribe en un temporal y lo pasa a su nombre definitivo, para que el
     * archivo bueno nunca esté a medio escribir. El renombrado es atómico cuando
     * el sistema de archivos lo permite; si no, se reemplaza igualmente.
     */
    private fun writeAtomically(folder: Path, bundle: ExportBundle) {
        val staging = BackupFolder.stagingFile(folder)
        try {
            Files.newOutputStream(staging).use { exporter.writeTo(it, bundle) }
            moveIntoPlace(staging, BackupFolder.backupFile(folder))
        } catch (e: Exception) {
            // El temporal a medias se borra para no dejar basura en la carpeta.
            runCatching { Files.deleteIfExists(staging) }
                .onFailure { Logger.w(TAG, "No se pudo limpiar el temporal: ${it.message}") }
            throw e
        }
    }

    private fun moveIntoPlace(staging: Path, target: Path) {
        try {
            Files.move(
                staging,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Logger.d(TAG, "Renombrado atómico no soportado; se reemplaza directamente")
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** Espera a que se calme la actividad antes de escribir. */
        const val DEBOUNCE_MS = 30_000L
    }
}
