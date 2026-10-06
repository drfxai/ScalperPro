package ai.drfx.scalperpro.diagnostics

object AppDiagnostics {
    private val buffer = DiagnosticBuffer(capacity = 500)

    fun record(
        eventName: String,
        severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
        attributes: Map<String, String> = emptyMap(),
        requestId: String? = null,
        durationMillis: Long? = null,
        result: String? = null,
        errorCode: String? = null
    ) {
        buffer.add(
            DiagnosticEvent(
                timestampEpochMillis = System.currentTimeMillis(),
                severity = severity,
                eventName = eventName,
                sessionId = null,
                requestId = requestId,
                attributes = attributes,
                durationMillis = durationMillis,
                result = result,
                errorCode = errorCode
            )
        )
    }

    fun snapshot(): List<DiagnosticEvent> =
        buffer.snapshot()

    fun clear() {
        buffer.clear()
    }
}
