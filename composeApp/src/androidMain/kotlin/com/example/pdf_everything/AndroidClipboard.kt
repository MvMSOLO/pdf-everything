package com.example.pdf_everything.core.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.pdf_everything.core.document.DocumentSource
import android.provider.MediaStore
import java.io.ByteArrayOutputStream

actual object PlatformClipboard {
    private const val OBJECT_PREFIX = "PDF_EVERYTHING_OBJECT_v1:"
    private fun manager(): ClipboardManager? = (ActivityHolder.activity as? Context)?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    actual fun read(): ClipboardSnapshot {
        val clip = manager()?.primaryClip ?: return ClipboardSnapshot()
        val item = clip.getItemAt(0)
        val text = item.coerceToText(ActivityHolder.activity).toString()
        return ClipboardSnapshot(text = text.takeUnless { it.startsWith(OBJECT_PREFIX) })
    }

    actual fun writeText(text: String) { manager()?.setPrimaryClip(ClipData.newPlainText("PDF Everything", text)) }
    actual fun writeObject(payload: ObjectClipboard) { manager()?.setPrimaryClip(ClipData.newPlainText("PDF Everything Object", "PDF Everything object — paste inside PDF Everything")) }
    actual fun writeImage(png: ByteArray) {
        val activity = ActivityHolder.activity ?: return
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.size) ?: return
        val uri = MediaStore.Images.Media.insertImage(activity.contentResolver, bitmap, "PDF Everything clipboard", null)?.let(Uri::parse) ?: return
        manager()?.setPrimaryClip(ClipData.newUri(activity.contentResolver, "PDF Everything Image", uri))
        bitmap.recycle()
    }
    actual fun materializeImage(png: ByteArray): DocumentSource? = runCatching {
        val context = ActivityHolder.activity ?: return@runCatching null
        val file = java.io.File.createTempFile("clipboard-", ".png", context.cacheDir)
        file.writeBytes(png)
        DocumentSource.FilePath(file.absolutePath)
    }.getOrNull()

}
