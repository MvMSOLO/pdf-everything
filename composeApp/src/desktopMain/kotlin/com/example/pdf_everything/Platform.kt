package com.example.pdf_everything

import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.pdf_engine.api.PdfEngine
import com.example.pdf_everything.phase6.Phase6OperationResult
import com.example.pdf_everything.phase6.PrintRequest
import com.example.pdf_everything.phase6.RecoveryEntry
import com.example.pdf_everything.phase6.SaveAsRequest
import com.example.pdf_everything.phase7.DefaultPdfAssociationStatus
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

actual val isDesktop: Boolean = true

private object DesktopLaunchState {
    private var initialPath: String? = null

    fun set(args: Array<String>) {
        val normalized = args.asSequence()
            .map { it.trim().trim('"') }
            .filter { it.isNotBlank() }
        initialPath = normalized.firstOrNull { arg ->
            val file = File(arg)
            file.isFile && file.extension.equals("pdf", ignoreCase = true)
        }?.let { File(it).absoluteFile.normalize().path }
    }

    fun consume(): DocumentSource? = initialPath?.let { value ->
        initialPath = null
        DocumentSource.FilePath(value)
    }
}

private object DesktopDropState {
    var installed = AtomicBoolean(false)
}

fun setDesktopStartupArgument(args: Array<String>) {
    DesktopLaunchState.set(args)
}

actual fun consumeStartupPdfSource(): DocumentSource? = DesktopLaunchState.consume()

actual fun requestPdfSaveAs(request: SaveAsRequest, onSelected: (String?) -> Unit) = Phase6Platform.requestSaveAs(request, onSelected)

actual fun printPdfDocument(engine: PdfEngine, document: Document, request: PrintRequest, onFinished: (Phase6OperationResult) -> Unit) = Phase6Platform.print(engine, document, request, onFinished)

actual fun defaultPdfAssociationStatus(): DefaultPdfAssociationStatus = WindowsPdfAssociation.status()

actual fun openWindowsDefaultAppSettings(): Boolean = WindowsPdfAssociation.openDefaultApps()

actual fun hasSeenDesktopDefaultAppPrompt(): Boolean = WindowsPdfAssociation.promptSeen()

actual fun markDesktopDefaultAppPromptSeen() { WindowsPdfAssociation.markPromptSeen() }

private object WindowsPdfAssociation {
    private const val PROG_ID = "PDFEverything.Document.1"
    private val promptFile = File(System.getProperty("user.home"), ".pdf-everything/default-app-prompt-seen")

    fun status(): DefaultPdfAssociationStatus {
        if (!isWindows()) return DefaultPdfAssociationStatus.UNSUPPORTED
        val userChoice = regQuery("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.pdf\\UserChoice", "ProgId")
        if (userChoice != null) {
            if (isOurProgId(userChoice) || commandBelongsToApplication(userChoice)) return DefaultPdfAssociationStatus.DEFAULT_APP
            val candidateRegistered = isCandidateRegistered()
            return if (candidateRegistered) DefaultPdfAssociationStatus.REGISTERED_NOT_DEFAULT else DefaultPdfAssociationStatus.UNKNOWN
        }
        val effective = regQuery("HKCR\\.pdf", "")
        return when {
            effective != null && (isOurProgId(effective) || commandBelongsToApplication(effective)) -> DefaultPdfAssociationStatus.DEFAULT_APP
            isCandidateRegistered() -> DefaultPdfAssociationStatus.REGISTERED_NOT_DEFAULT
            else -> DefaultPdfAssociationStatus.NOT_REGISTERED
        }
    }

    fun openDefaultApps(): Boolean = runCatching {
        ProcessBuilder("cmd.exe", "/c", "start", "", "ms-settings:defaultapps").start()
        true
    }.getOrDefault(false)

    fun promptSeen(): Boolean = promptFile.isFile

    fun markPromptSeen() {
        runCatching { promptFile.parentFile?.mkdirs(); promptFile.writeText("1") }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    private fun isOurProgId(progId: String): Boolean = progId.equals(PROG_ID, ignoreCase = true) || progId.contains("PDFEverything", ignoreCase = true)

    private fun isCandidateRegistered(): Boolean {
        if (regQuery("HKCR\\$PROG_ID", "") != null) return true
        val apps = regQuery("HKCR\\.pdf\\OpenWithProgids", "")
        return apps?.contains("PDFEverything", ignoreCase = true) == true
    }

    private fun commandBelongsToApplication(progId: String): Boolean {
        val command = regQuery("HKCR\\$progId\\shell\\open\\command", "") ?: return false
        val commandExecutable = extractExecutable(command).replace('/', '\\')
        val currentExecutable = ProcessHandle.current().info().command().orElse("").replace('/', '\\')
        return currentExecutable.isNotBlank() && commandExecutable.isNotBlank() && commandExecutable.equals(currentExecutable, ignoreCase = true)
    }

    private fun regQuery(key: String, value: String): String? = runCatching {
        val command = if (value.isBlank()) arrayOf("reg.exe", "QUERY", key, "/ve") else arrayOf("reg.exe", "QUERY", key, "/v", value)
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        if (process.exitValue() != 0) null else parseRegistryValue(output, if (value.isBlank()) "(Default)" else value)
    }.getOrNull()

    private fun parseRegistryValue(output: String, valueName: String): String? = output.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith(valueName, ignoreCase = true) }
        ?.split(Regex("\\s{2,}"), limit = 3)
        ?.lastOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun extractExecutable(command: String): String {
        val trimmed = command.trim()
        return if (trimmed.startsWith('"')) trimmed.substringAfter('"').substringBefore('"') else trimmed.substringBefore(' ')
    }
}

actual fun requestImageOpen(onSelected: (DocumentSource) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Insert image", FileDialog.LOAD).apply {
        filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".webp", true)
        }
        isVisible = true
    }
    val file = dialog.file
    val directory = dialog.directory
    if (file != null && directory != null) onSelected(DocumentSource.FilePath(File(directory, file).absolutePath))
}

actual fun requestPdfOpen(onSelected: (DocumentSource) -> Unit) {
    val dialog = FileDialog(null as Frame?, "Open PDF", FileDialog.LOAD).apply {
        filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
        isVisible = true
    }
    val file = dialog.file
    val directory = dialog.directory
    if (file != null && directory != null) onSelected(DocumentSource.FilePath(File(directory, file).absolutePath))
}

actual fun installDesktopFileDrop(onSelected: (DocumentSource) -> Unit) {
    if (!DesktopDropState.installed.compareAndSet(false, true)) return
    Thread {
        repeat(20) {
            try {
                val window = findAppWindow() ?: run { Thread.sleep(250); return@repeat }
                if (window.dropTarget == null) {
                    window.dropTarget = DropTarget(
                        window,
                        DnDConstants.ACTION_COPY,
                        PdfDropTargetListener(onSelected),
                        true
                    )
                }
                return@Thread
            } catch (_: Throwable) {
                Thread.sleep(250)
            }
        }
    }.apply { isDaemon = true; name = "pdf-everything-drop-target" }.start()
}

private fun findAppWindow(): Window? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    ?: Window.getWindows().firstOrNull { it.isDisplayable && it.isShowing && it.name != "" }

private class PdfDropTargetListener(private val onSelected: (DocumentSource) -> Unit) : DropTargetAdapter() {
    override fun drop(event: DropTargetDropEvent) {
        try {
            if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                event.rejectDrop()
                return
            }
            event.acceptDrop(DnDConstants.ACTION_COPY)
            val files = event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                ?: emptyList<Any>()
            files.filterIsInstance<File>()
                .firstOrNull { it.isFile && it.name.endsWith(".pdf", ignoreCase = true) }
                ?.let { onSelected(DocumentSource.FilePath(it.absolutePath)) }
            event.dropComplete(true)
        } catch (_: Throwable) {
            runCatching { event.dropComplete(false) }
        }
    }
}
