package com.example.pdf_everything.core.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
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
        return null
    }

    actual suspend fun pickSaveFile(
        title: String,
        defaultName: String,
        allowedExtensions: List<String>
    ): String? {
        return null
    }

    fun launchOpenIntent(requestCode: Int = REQUEST_OPEN_PDF): Boolean {
        val ctx = context ?: return false
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            putExtra(Intent.EXTRA_TITLE, "Open PDF")
        }
        if (ctx is android.app.Activity) {
            ctx.startActivityForResult(intent, requestCode)
            return true
        }
        return false
    }

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
        val parsedUri = when (source) {
            is DocumentSource.ContentUri -> Uri.parse(source.uri)
            is DocumentSource.FilePath -> Uri.parse("file://${source.path}")
            is DocumentSource.ByteArraySource -> return false
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(parsedUri, "application/pdf")
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
        return stat.blockCountLong * stat.blockSizeLong
    }

    actual fun registerFileAssociation() {
        // No-op on Android: handled by intent-filter in AndroidManifest.xml
    }

    companion object {
        const val REQUEST_OPEN_PDF = 1001
        const val REQUEST_SAVE_PDF = 1002
    }
}
