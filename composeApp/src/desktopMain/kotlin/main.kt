package com.example.pdf_everything

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.example.pdf_everything.app.App
import com.example.pdf_everything.core.commands.CommandDispatcher
import com.example.pdf_everything.core.files.DocumentFileService
import com.example.pdf_everything.core.history.HistoryManager
import com.example.pdf_everything.core.recovery.RecoveryService
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.services.PdfEngine
import com.example.pdf_everything.core.services.PlatformService
import java.io.File

fun createPdfEngine(): PdfEngine {
    val adapter = com.example.pdf_everything.core.services.PdfBoxAdapter()
    // PdfBoxAdapter extends PdfEngineAdapter which implements PdfEngine
    return adapter
}

fun main() {
    // ── Create all services per spec ──────────────────────────────
    val pdfEngine: PdfEngine = createPdfEngine()

    // Safe init (PDFBox doesn't need async init but we honour the contract)
    val runtime = kotlinx.coroutines.runtime.recoverable
    kotlinx.coroutines.runBlocking { pdfEngine.initialize() }

    val platformService = PlatformService()
    val historyManager = HistoryManager()
    val commandDispatcher = CommandDispatcher()

    val recoveryDir = System.getProperty("user.home") + "/.pdf-everything/recovery"
    val recoveryService = RecoveryService(
        fileSystemProvider = DesktopFileSystemProvider(recoveryDir)
    )

    val documentFileService = DocumentFileService(
        engine = pdfEngine,
        platform = platformService
    )

    val appState = AppState(
        pdfEngine = pdfEngine,
        commandDispatcher = commandDispatcher,
        historyManager = historyManager,
        documentFileService = documentFileService,
        recoveryService = recoveryService
    )

    // ── Window ──────────────────────────────────────────────────────
    application {
        Window(
            onCloseRequest = {
                kotlinx.coroutines.runBlocking { pdfEngine.shutdown() }
                exitApplication()
            },
            title = "PDF Everything",
            state = WindowState(width = 1280.dp, height = 800.dp)
        ) {
            App(appState = appState)
        }
    }
}

/** Desktop file-system provider for RecoveryService persistence. */
class DesktopFileSystemProvider(
    private val recoveryDir: String
) : com.example.pdf_everything.core.recovery.FileSystemProvider {

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