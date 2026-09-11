package com.example.pdf_everything.core.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.pdf_everything.core.document.DocumentSource

/**
 * Android actual for [PlatformService].
 *
 * File picking uses the system SAF (Storage Access Framework) intent.
 * File association registration is a no-op (handled via AndroidManifest.xml).
 */
actual class PlatformService actual constructor() {

    actual val platformName: String = "android"
    actual val isDesktop: Boolean = false
    actual val isMobile: Boolean = true

    // The Context is set from the Android Application / Activity
    var context: Context? = null

    actual suspend fun pickOpenFile(
        title: String,
        allowedExtensions: List<String>
    ): DocumentSource? {
        // On Android we use Intent-based file picking; the result comes
        // back via onActivityResult, so this returns null here and the
        // actual URI is handled in the Activity layer.
        // A suspend-based wrapper is available through the Activity coroutine.
        // For now this is the hook; the real launch is in the Activity.
        return null
    }

    actual suspend fun pickSaveFile(
        title: String,
        defaultName: String,
        allowedExtensions: List<String>
    ): String? {
        // Same: the Activity handles the ACTION_CREATE_DOCUMENT intent.
        return null
    }

    /**
     * Launch a SAF open-file intent. Call from the Activity.
     * Returns the request code for onActivityResult.
     */
    fun launchOpenIntent(requestCode: Int = REQUEST_OPEN_PDF): Boolean {
        val ctx = context ?: return false
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "Open PDF")
        }
        // Must be launched from an Activity – this is a convenience method
        if (ctx is android.app.Activity) {
            ctx.startActivityForResult(intent, requestCode)
            return true
        }
        return false
    }

    /**
     * Launch a SAF save-file intent.
     */
    fun launchSaveIntent(defaultName: String = "document.pdf", requestCode: Int = REQUEST_SAVE_PDF): Boolean {
        val ctx = context ?: return false
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, defaultName)
        }
        if (ctx is android.app.Activity) {
            ctx.startActivityForResult(intent, requestCode)
            return true
        }
        return false
    }

    actual suspend fun launchExternal(source: DocumentSource): Boolean {
        val ctx = context ?: return false
        val uri = when (source) {
            is DocumentSource.ContentUri -> source.uri
            is DocumentSource.FilePath -> Uri.parse("file://${source.path}")
            is DocumentSource.ByteArraySource -> return false // can't launch raw bytes
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, "application/pdf")
        }
        return try {
            ctx.startActivity(Intent.createChooser(viewIntent, "Open PDF with"))
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun availableStorageBytes(path: String): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    actual fun totalStorageBytes(path: String): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.totalBlocksLong * stat.blockSizeLong
    }

    actual fun registerFileAssociation() {
        // No-op on Android: handled by intent-filter in AndroidManifest.xml
    }

    companion object {
        const val REQUEST_OPEN_PDF = 1001
        const val REQUEST_SAVE_PDF = 1002
    }
}