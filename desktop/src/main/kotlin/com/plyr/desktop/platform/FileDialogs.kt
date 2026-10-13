package com.plyr.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Diálogos nativos del sistema para elegir carpeta y archivos.
 *
 * En Android la elección pasa por SAF (`OpenDocumentTree`, `CreateDocument`); en
 * escritorio se usan los selectores de Swing, que delegan en el diálogo nativo
 * del SO. Se ejecutan en el hilo de la interfaz (`EDT`), que es obligatorio para
 * Swing, y devuelven null si el usuario cancela.
 */
object FileDialogs {

    /** Abre un selector de carpetas. Null si el usuario cancela. */
    fun chooseFolder(title: String, initial: File? = null): File? = onEdt {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
            initial?.takeIf { it.isDirectory }?.let { currentDirectory = it }
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }

    /** Abre un selector de archivos ZIP. Null si el usuario cancela. */
    fun chooseZipToOpen(title: String, initial: File? = null): File? = onEdt {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("plyr (*.zip)", "zip")
            initial?.takeIf { it.isDirectory }?.let { currentDirectory = it }
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }

    /**
     * Abre el diálogo nativo de "guardar como" para un ZIP. Usa `FileDialog`
     * (AWT) porque en macOS y Windows es el diálogo realmente nativo. Null si el
     * usuario cancela.
     */
    fun saveZip(title: String, suggestedName: String, initial: File? = null): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = suggestedName
        initial?.takeIf { it.isDirectory }?.let { dialog.directory = it.absolutePath }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        val directory = dialog.directory ?: return null
        return File(directory, file)
    }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var result: T? = null
        var error: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                result = block()
            } catch (t: Throwable) {
                error = t
            }
        }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    init {
        // El look & feel del SO en el que corre la app; si falla, el de Swing.
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }
}
