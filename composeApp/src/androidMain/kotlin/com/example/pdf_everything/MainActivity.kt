package com.example.pdf_everything

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pdf_everything.app.App
import com.example.pdf_everything.core.commands.CommandDispatcher
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.files.DocumentFileService
import com.example.pdf_everything.core.history.HistoryManager
import com.example.pdf_everything.core.recovery.FileSystemProvider
import com.example.pdf_everything.core.recovery.RecoveryService
import com.example.pdf_everything.core.services.AppState
import com.example.pdf_everything.core.services.PlatformService
import com.example.pdf_everything.core.services.createPdfEngine
import com.example.pdf_everything.core.settings.AndroidSettings
import com.example.pdf_everything.core.settings.SettingsRepository
import com.example.pdf_everything.core.shortcuts.ShortcutRegistry
import java.io.File

class MainActivity : ComponentActivity() {

    private val platformService by lazy {
        PlatformService().apply {
            context = this@MainActivity
        }
    }

    private val pdfEngine by lazy { createPdfEngine() }
    private val historyManager by lazy { HistoryManager() }
    private val commandDispatcher by lazy { CommandDispatcher(historyManager) }
    private val settingsRepository by lazy { SettingsRepository(AndroidSettings(applicationContext)) }

    private val fileSystemProvider by lazy {
        object : FileSystemProvider {
            private val dir = File(applicationContext.filesDir, "recovery")
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
    }

    private val recoveryService by lazy {
        RecoveryService(engine = pdfEngine, fileSystemProvider = fileSystemProvider)
    }

    private val documentFileService by lazy {
        DocumentFileService(engine = pdfEngine, platform = platformService)
    }

    private val appState by lazy {
        AppState(
            pdfEngine = pdfEngine,
            commandDispatcher = commandDispatcher,
            historyManager = historyManager,
            documentFileService = documentFileService,
            recoveryService = recoveryService,
            shortcutRegistry = ShortcutRegistry.createDefault(),
            settingsRepository = settingsRepository
        )
    }

    private val openPdfLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleOpenedUri(uri)
                }
            }
        }

    private var pendingIntentSource: DocumentSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            App(appState = appState)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let { uri ->
                    pendingIntentSource = DocumentSource.ContentUri(
                        uri = uri.toString(),
                        displayName = getDisplayName(uri) ?: "shared.pdf"
                    )
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "application/pdf") {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                        pendingIntentSource = DocumentSource.ContentUri(
                            uri = uri.toString(),
                            displayName = getDisplayName(uri) ?: "shared.pdf"
                        )
                    }
                }
            }
        }
    }

    private fun handleOpenedUri(uri: Uri) {
        pendingIntentSource = DocumentSource.ContentUri(
            uri = uri.toString(),
            displayName = getDisplayName(uri) ?: "document.pdf"
        )
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex >= 0) cursor.getString(displayNameIndex) else null
                } else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
