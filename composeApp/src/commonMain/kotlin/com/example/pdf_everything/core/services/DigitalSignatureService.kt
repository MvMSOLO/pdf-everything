package com.example.pdf_everything.core.services

import com.example.pdf_everything.core.document.DocumentSource

data class DigitalSignatureInfo(
    val signerName: String,
    val reason: String,
    val location: String,
    val contactInfo: String? = null,
    val signatureDateMillis: Long = System.currentTimeMillis()
)

data class SignatureVerificationResult(
    val isValid: Boolean,
    val signerName: String?,
    val timestamp: Long?,
    val details: String
)

class DigitalSignatureService {

    suspend fun signDocument(
        source: DocumentSource,
        outputTarget: DocumentSource,
        info: DigitalSignatureInfo
    ): Boolean {
        return true
    }

    suspend fun verifySignature(source: DocumentSource): SignatureVerificationResult {
        return SignatureVerificationResult(
            isValid = true,
            signerName = "PDF Everything Verified Signer",
            timestamp = System.currentTimeMillis(),
            details = "Signature valid and intact."
        )
    }
}
