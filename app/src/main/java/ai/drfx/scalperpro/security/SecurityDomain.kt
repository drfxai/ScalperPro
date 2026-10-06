package ai.drfx.scalperpro.security

data class SecretReference(
    val configured: Boolean,
    val maskedId: String?
) {
    init {
        require(!configured || !maskedId.isNullOrBlank())
    }
}

data class UploadSecurityPolicy(
    val allowedMimeTypes: Set<String>,
    val maxBytes: Long
) {
    init {
        require(allowedMimeTypes.isNotEmpty())
        require(maxBytes > 0L)
    }
}

data class UploadValidationResult(
    val accepted: Boolean,
    val reason: String?
)

object UploadValidator {
    fun validate(
        mimeType: String,
        sizeBytes: Long,
        policy: UploadSecurityPolicy
    ): UploadValidationResult {
        if (mimeType !in policy.allowedMimeTypes) {
            return UploadValidationResult(
                accepted = false,
                reason = "Unsupported MIME type."
            )
        }

        if (sizeBytes <= 0L) {
            return UploadValidationResult(
                accepted = false,
                reason = "Upload is empty."
            )
        }

        if (sizeBytes > policy.maxBytes) {
            return UploadValidationResult(
                accepted = false,
                reason = "Upload exceeds the configured size limit."
            )
        }

        return UploadValidationResult(
            accepted = true,
            reason = null
        )
    }
}

object SecurityDefaults {
    val chartImageUpload = UploadSecurityPolicy(
        allowedMimeTypes = setOf(
            "image/jpeg",
            "image/png",
            "image/webp"
        ),
        maxBytes = 10L * 1024L * 1024L
    )
}
