package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.DocumentSource

enum class CloudProvider {
    GOOGLE_DRIVE,
    DROPBOX,
    ONEDRIVE
}

data class CloudFileItem(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val provider: CloudProvider
)

class CloudSyncService {

    suspend fun listRemoteFiles(provider: CloudProvider): List<CloudFileItem> {
        return listOf(
            CloudFileItem("file-1", "Sample_Cloud_Doc.pdf", 1024500, provider),
            CloudFileItem("file-2", "Contract_Agreement.pdf", 2500000, provider)
        )
    }

    suspend fun uploadToCloud(provider: CloudProvider, source: DocumentSource, remoteName: String): CloudFileItem {
        return CloudFileItem(
            id = "file-new-${System.currentTimeMillis()}",
            name = remoteName,
            sizeBytes = 1048576,
            provider = provider
        )
    }

    suspend fun downloadFromCloud(provider: CloudProvider, fileId: String): ByteArray {
        return ByteArray(0)
    }
}
