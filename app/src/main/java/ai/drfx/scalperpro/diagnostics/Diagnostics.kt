package ai.drfx.scalperpro.diagnostics

enum class DiagnosticSeverity {
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

data class DiagnosticEvent(
    val timestampEpochMillis: Long,
    val severity: DiagnosticSeverity,
    val eventName: String,
    val sessionId: String?,
    val requestId: String?,
    val attributes: Map<String, String>,
    val durationMillis: Long?,
    val result: String?,
    val errorCode: String?
)

object DiagnosticRedactor {
    private val sensitiveKeyFragments = listOf(
        "authorization",
        "token",
        "api_key",
        "apikey",
        "secret",
        "password",
        "credential",
        "cookie"
    )

    private val bearerRegex = Regex(
        pattern = "(?i)bearer\\s+[a-z0-9._~+\\-/=]+"
    )

    private val apiKeyLikeRegex = Regex(
        pattern = "(?i)(key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+"
    )

    fun redactAttributes(
        attributes: Map<String, String>
    ): Map<String, String> =
        attributes.mapValues { (key, value) ->
            if (isSensitiveKey(key)) {
                "[REDACTED]"
            } else {
                redactText(value)
            }
        }

    fun redactText(value: String): String =
        value
            .replace(bearerRegex, "Bearer [REDACTED]")
            .replace(apiKeyLikeRegex) { match ->
                match.value.substringBeforeAny(':', '=') + "=[REDACTED]"
            }
            .take(MAX_ATTRIBUTE_LENGTH)

    private fun isSensitiveKey(key: String): Boolean {
        val normalized = key.lowercase()
        return sensitiveKeyFragments.any { it in normalized }
    }

    private fun String.substringBeforeAny(
        first: Char,
        second: Char
    ): String {
        val firstIndex = indexOf(first).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val secondIndex = indexOf(second).takeIf { it >= 0 } ?: Int.MAX_VALUE
        val index = minOf(firstIndex, secondIndex)

        return if (index == Int.MAX_VALUE) this else substring(0, index)
    }

    private const val MAX_ATTRIBUTE_LENGTH = 2_000
}

class DiagnosticBuffer(
    private val capacity: Int = 500
) {
    init {
        require(capacity in 1..10_000)
    }

    private val events = ArrayDeque<DiagnosticEvent>()

    @Synchronized
    fun add(event: DiagnosticEvent) {
        val sanitized = event.copy(
            attributes = DiagnosticRedactor.redactAttributes(event.attributes)
        )

        while (events.size >= capacity) {
            events.removeFirst()
        }
        events.addLast(sanitized)
    }

    @Synchronized
    fun snapshot(): List<DiagnosticEvent> =
        events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}
