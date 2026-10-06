package ai.drfx.scalperpro.code

enum class CodeLanguage {
    PINE,
    MQL5
}

enum class VerificationStatus {
    GENERATED_UNVERIFIED,
    STATIC_ANALYZED,
    COMPILE_VERIFIED
}

data class CodeFinding(
    val severity: String,
    val code: String,
    val message: String
)

data class CodeArtifact(
    val language: CodeLanguage,
    val source: String,
    val verificationStatus: VerificationStatus,
    val findings: List<CodeFinding>
)
