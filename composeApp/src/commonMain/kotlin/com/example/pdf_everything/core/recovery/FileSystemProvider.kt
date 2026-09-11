package com.example.pdf_everything.core.recovery

/**
 * Platform abstraction for file-system operations needed by RecoveryService.
 *
 * Each platform (Desktop, Android) provides its own `actual` implementation
 * that writes to a platform-specific recovery directory.
 */
interface FileSystemProvider {
    fun writeText(fileName: String, text: String)
    fun readText(fileName: String): String?
    fun delete(fileName: String)
}