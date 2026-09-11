package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.DocumentSource
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop (JVM) actual for [PlatformService].
 *
 * File picking uses Swing JFileChooser.
 * File association registration writes a .desktop entry or Windows registry
 * key (best-effort; requires elevated permissions on Windows).
 */
actual class PlatformService actual constructor() {

    actual val platformName: String = "desktop"
    actual val isDesktop: Boolean = true
    actual val isMobile: Boolean = false

    actual suspend fun pickOpenFile(
        title: String,
        allowedExtensions: List<String>
    ): DocumentSource? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileFilter = FileNameExtensionFilter(
                "PDF Documents (*.pdf)",
                *allowedExtensions.toTypedArray()
            )
            isMultiSelectionEnabled = false
        }
        val result = chooser.showOpenDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        val file = chooser.selectedFile ?: return null
        return DocumentSource.FilePath(file.absolutePath)
    }

    actual suspend fun pickSaveFile(
        title: String,
        defaultName: String,
        allowedExtensions: List<String>
    ): String? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileFilter = FileNameExtensionFilter(
                "PDF Documents (*.pdf)",
                *allowedExtensions.toTypedArray()
            )
            selectedFile = File(defaultName)
        }
        val result = chooser.showSaveDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.absolutePath
    }

    actual suspend fun launchExternal(source: DocumentSource): Boolean {
        if (!Desktop.isDesktopSupported()) return false
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.OPEN)) return false
        return try {
            when (source) {
                is DocumentSource.FilePath -> {
                    desktop.open(File(source.path))
                    true
                }
                is DocumentSource.ByteArraySource -> {
                    // Write to temp file and open that
                    val tempFile = File.createTempFile("pdf_everything_", ".pdf")
                    tempFile.writeBytes(source.bytes)
                    tempFile.deleteOnExit()
                    desktop.open(tempFile)
                    true
                }
                is DocumentSource.ContentUri -> {
                    // Content URIs are Android-only; try path extraction
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    actual fun availableStorageBytes(path: String): Long {
        val file = File(path).takeIf { it.exists() } ?: File(".")
        return file.usableSpace
    }

    actual fun totalStorageBytes(path: String): Long {
        val file = File(path).takeIf { it.exists() } ?: File(".")
        return file.totalSpace
    }

    actual fun registerFileAssociation() {
        // Best-effort: write a .desktop file on Linux or add registry on Windows
        val osName = System.getProperty("os.name", "").lowercase()
        when {
            osName.contains("linux") -> registerLinuxAssociation()
            osName.contains("windows") -> registerWindowsAssociation()
            // macOS uses Info.plist inside the app bundle -- handled at build time
        }
    }

    private fun registerLinuxAssociation() {
        val desktopEntry = """
            [Desktop Entry]
            Type=Application
            Name=PDF Everything
            Comment=View and Edit PDF files
            Exec=pdf-everything %f
            Icon=pdf-everything
            Terminal=false
            Categories=Office;Graphics;Viewer;
            MimeType=application/pdf;
        """.trimIndent()
        val targetDir = File(System.getProperty("user.home"), ".local/share/applications")
        targetDir.mkdirs()
        val targetFile = File(targetDir, "pdf-everything.desktop")
        targetFile.writeText(desktopEntry)
        // Update MIME database
        try {
            Runtime.getRuntime().exec(arrayOf("update-desktop-database", targetDir.absolutePath))
        } catch (_: Exception) { /* non-critical */ }
    }

    private fun registerWindowsAssociation() {
        // Windows registry manipulation requires elevated privileges;
        // this is a best-effort attempt that may silently fail.
        try {
            val exePath = System.getProperty("compose.application.main") ?: "pdf-everything.exe"
            val commands = listOf(
                "reg add HKCR\\.pdf /ve /d PDFEverything.Assoc.File /f",
                "reg add HKCR\\PDFEverything.Assoc.File /ve /d \"PDF Everything Document\" /f",
                "reg add HKCR\\PDFEverything.Assoc.File\\shell\\open\\command /ve /d \"$exePath\" \"%1\" /f"
            )
            for (cmd in commands) {
                Runtime.getRuntime().exec(arrayOf("cmd", "/c", cmd))
            }
        } catch (_: Exception) { /* non-critical on failure */ }
    }
}