package com.example.pdf_everything

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.pdf_everything.app.App
import com.example.pdf_everything.core.commands.CommandDispatcher
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.files.DocumentFileService
import com.example.pdf_everything.core.history.HistoryManager
import com.example.pdf_everything.core.recovery.FileSystemProvider
import com.example.pdf_everything.core.recovery.RecoveryService
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.services.PdfEngine
import com.example.pdf_everything.core.services.PlatformService
import com.example.pdf_everything.core.settings.DesktopSettings
import com.example.pdf_everything.core.settings.SettingsRepository
import com.example.pdf_everything.core.shortcuts.KeyModifier
import com.example.pdf_everything.core.shortcuts.ShortcutAction
import com.example.pdf_everything.core.shortcuts.ShortcutRegistry
import kotlinx.coroutines.runBlocking
import java.io.File

fun createPdfEngine(): PdfEngine {
    val adapter = com.example.pdf_everything.core.services.PdfBoxAdapter()
    return adapter
}

/**
 * Desktop entry point.
 *
 * Per spec §7.1: accepts an optional file path as the first CLI argument
 * and opens it automatically on launch.
 *
 * Per spec §9: all keyboard shortcuts are wired via [ShortcutRegistry]
 * with an [onPreviewKeyEvent] handler on the top-level window.
 */
fun main(args: Array<String>) {
    // ── Create all services per spec ──────────────────────────────
    val pdfEngine: PdfEngine = createPdfEngine()

    // PDFBox doesn't need async init but we honour the contract
    runBlocking { pdfEngine.initialize() }

    val platformService = PlatformService()
    val historyManager = HistoryManager()
    val commandDispatcher = CommandDispatcher(historyManager)

    val recoveryDir = System.getProperty("user.home") + "/.pdf-everything/recovery"
    val fileSystemProvider = DesktopFileSystemProvider(recoveryDir)
    val recoveryService = RecoveryService(
        engine = pdfEngine,
        fileSystemProvider = fileSystemProvider
    )

    val documentFileService = DocumentFileService(
        engine = pdfEngine,
        platform = platformService
    )

    val settingsRepository = SettingsRepository(DesktopSettings())

    val shortcutRegistry = ShortcutRegistry.createDefault()

    val appState = AppState(
        pdfEngine = pdfEngine,
        commandDispatcher = commandDispatcher,
        historyManager = historyManager,
        documentFileService = documentFileService,
        recoveryService = recoveryService,
        shortcutRegistry = shortcutRegistry,
        settingsRepository = settingsRepository
    )

    // ── CLI arg: open file from command line (spec §7.1) ───────────
    val cliFilePath = args.firstOrNull()
    val cliFile = cliFilePath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
    if (cliFile != null) {
        runBlocking {
            val source = DocumentSource.FilePath(cliFile.absolutePath)
            val result = documentFileService.openDocument(source)
            if (result is com.example.pdf_everything.core.services.EngineResult.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                val doc = result.value as com.example.pdf_everything.core.document.Document
                appState.openDocument(doc)
            }
        }
    }

    // ── Window ──────────────────────────────────────────────────────
    application {
        val windowState = rememberWindowState(
            width = 1280.dp,
            height = 800.dp
        )

        Window(
            onCloseRequest = {
                runBlocking { pdfEngine.shutdown() }
                exitApplication()
            },
            title = "PDF Everything",
            state = windowState,
            // ── Keyboard shortcut handler (spec §9) ─────────────
            onPreviewKeyEvent = { keyEvent ->
                handleDesktopShortcut(keyEvent, shortcutRegistry, appState)
            }
        ) {
            App(appState = appState)
        }
    }
}

/**
 * Translates a Compose [KeyEvent] into a [ShortcutRegistry] lookup
 * and dispatches the matching action (spec §9).
 *
 * Supports Ctrl/Alt/Meta modifiers + letter keys.
 * Returns true if the shortcut was consumed.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun handleDesktopShortcut(
    keyEvent: KeyEvent,
    shortcutRegistry: ShortcutRegistry,
    appState: AppState
): Boolean {
    // Only handle key-down (not key-up or repeats)
    if (keyEvent.type != KeyEventType.KeyDown) return false

    val modifiers = buildList {
        if (keyEvent.isCtrlPressed) add(KeyModifier.Ctrl)
        if (keyEvent.isAltPressed) add(KeyModifier.Alt)
        if (keyEvent.isMetaPressed) add(KeyModifier.Meta)
        if (keyEvent.isShiftPressed) add(KeyModifier.Shift)
    }

    val binding = shortcutRegistry.resolve(keyEvent.key, modifiers)
    if (binding != null) {
        when (binding.action) {
            ShortcutAction.UNDO -> {
                appState.currentDocument?.let { doc ->
                    val result = appState.commandDispatcher.undo(doc)
                    appState.updateDocument(result)
                }
            }
            ShortcutAction.REDO -> {
                appState.currentDocument?.let { doc ->
                    val result = appState.commandDispatcher.redo(doc)
                    appState.updateDocument(result)
                }
            }
            ShortcutAction.SAVE -> {
                appState.currentDocument?.let { doc ->
                    runBlocking {
                        appState.documentFileService.saveDocument(doc.documentId)
                    }
                }
            }
            ShortcutAction.PRINT -> {
                appState.currentDocument?.let { doc ->
                    val adapter = appState.pdfEngine as? com.example.pdf_everything.core.services.PdfBoxAdapter
                    if (adapter != null) {
                        val printService = com.example.pdf_everything.core.services.DesktopPrintService(adapter)
                        printService.print(doc)
                    }
                }
            }
            ShortcutAction.CLOSE_TAB -> appState.closeActiveTab()
            ShortcutAction.SWITCH_TAB_NEXT -> appState.switchNextTab()
            ShortcutAction.SWITCH_TAB_PREV -> appState.switchPrevTab()
            ShortcutAction.OPEN -> { /* File picker triggered from UI */ }
            ShortcutAction.FIND,
            ShortcutAction.ZOOM_IN,
            ShortcutAction.ZOOM_OUT,
            ShortcutAction.ZOOM_RESET,
            ShortcutAction.NEXT_PAGE,
            ShortcutAction.PREV_PAGE,
            ShortcutAction.ROTATE_CW,
            ShortcutAction.ROTATE_CCW,
            ShortcutAction.DELETE_PAGE,
            ShortcutAction.FULLSCREEN,
            ShortcutAction.NEW_TAB,
            ShortcutAction.ESCAPE,
            ShortcutAction.SAVE_AS -> { /* Viewer-level or UI-level dispatch */ }
        }
        return true
    }
    return false
}

/** Desktop file-system provider for RecoveryService persistence. */
class DesktopFileSystemProvider(
    private val recoveryDir: String
) : FileSystemProvider {

    private val dir = File(recoveryDir)

    override fun writeText(fileName: String, text: String) {
        dir.mkdirs()
        File(dir, fileName).writeText(text)
    }

    override fun readText(fileName: String): String? {
        val file = File(dir, fileName)
        return if (file.exists()) file.readText() else null
    }

    override fun delete(fileName: String) {
        File(dir, fileName).delete()
    }
}
