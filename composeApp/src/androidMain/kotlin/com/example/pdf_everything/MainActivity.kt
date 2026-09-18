package com.example.pdf_everything

import App
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.pdf_everything.phase6.SaveAsRequest
import androidx.activity.enableEdgeToEdge
import com.example.pdf_everything.core.document.DocumentSource
import com.example.pdf_everything.pdf_engine.AndroidPdfEngineContext

class MainActivity : ComponentActivity() {
    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) deliverPickedPdf(uri)
    }

    private val saveAsLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        AndroidSaveAs.deliver(uri)
    }

    private val openImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) AndroidFilePicker.deliver(DocumentSource.ContentUri(uri.toString()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPdfEngineContext.applicationContext = applicationContext
        ActivityHolder.activity = this
        enableEdgeToEdge()
        setContent { App() }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    fun launchImagePicker() {
        openImageLauncher.launch(arrayOf("image/*"))
    }

    fun launchSaveAs(name: String) { saveAsLauncher.launch(if (name.endsWith(".pdf", true)) name else "$name.pdf") }

    fun launchPdfPicker() {
        AndroidFilePicker.launcher = { openPdfLauncher.launch(arrayOf("application/pdf")) }
        openPdfLauncher.launch(arrayOf("application/pdf"))
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val source = AndroidIntentFiles.extractPdfSource(this, incoming) ?: return
        AndroidFilePicker.deliver(source)
    }

    private fun deliverPickedPdf(uri: Uri) {
        val source = runCatching { AndroidIncomingPdf.preparePickedDocument(this, uri) }
            .getOrElse { AndroidIncomingPdf.copyToWorkspaceOrThrow(this, uri) }
        AndroidFilePicker.deliver(source)
    }

    override fun onDestroy() {
        ActivityHolder.activity = null
        AndroidFilePicker.launcher = null
        AndroidFilePicker.callback = null
        AndroidFilePicker.pendingSource = null
        AndroidPdfEngineContext.applicationContext = null
        super.onDestroy()
    }
}
