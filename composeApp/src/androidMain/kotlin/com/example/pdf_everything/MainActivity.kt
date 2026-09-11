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
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.core.services.PlatformService
import kotlinx.coroutines.DelicateCoroutinesApi

/**
 * Main Android Activity.
 *
 * Per spec §33‑§35:
 *   - Handles incoming VIEW/SEND intents for PDF files
 *   - Registers SAF file-pick result launchers
 *   - Passes content URIs and byte arrays to the document layer
 */
class MainActivity : ComponentActivity() {

    private val platformService = PlatformService().apply {
        context = this@MainActivity
    }

    // SAF open-file launcher
    private val openPdfLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleOpenedUri(uri)
                }
            }
        }

    // SAF save-file launcher
    private val savePdfLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleSaveUri(uri)
                }
            }
        }

    // Incoming intent from external app (VIEW / SEND)
    private var pendingIntentSource: DocumentSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if we were launched with a PDF intent
        handleIncomingIntent(intent)

        setContent {
            App(
                platformService = platformService,
                onOpenFileRequest = {
                    launchFilePicker()
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    // ── Intent handling ─────────────────────────────────────────────────

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
            Intent.ACTION_SEND_MULTIPLE -> {
                // Handle multiple PDFs — open the first for now
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()?.let { uri ->
                    pendingIntentSource = DocumentSource.ContentUri(
                        uri = uri.toString(),
                        displayName = getDisplayName(uri) ?: "shared.pdf"
                    )
                }
            }
        }
    }

    // ── File picker ──────────────────────────────────────────────────────

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "Open PDF")
        }
        openPdfLauncher.launch(intent)
    }

    private fun launchSavePicker(defaultName: String = "document.pdf") {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, defaultName)
        }
        savePdfLauncher.launch(intent)
    }

    // ── URI result handlers ──────────────────────────────────────────────

    private fun handleOpenedUri(uri: Uri) {
        val source = DocumentSource.ContentUri(
            uri = uri.toString(),
            displayName = getDisplayName(uri) ?: "document.pdf"
        )
        // TODO: feed to DocumentFileService.openDocument(source)
        // For now, store for the App layer to pick up
        pendingIntentSource = source
    }

    private fun handleSaveUri(uri: Uri) {
        // TODO: write document bytes to the content URI
    }

    // ── Utility ─────────────────────────────────────────────────────────

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

    /**
     * Retrieve and consume the pending intent source (called by App layer).
     */
    fun consumePendingIntentSource(): DocumentSource? {
        val source = pendingIntentSource
        pendingIntentSource = null
        return source
    }
}