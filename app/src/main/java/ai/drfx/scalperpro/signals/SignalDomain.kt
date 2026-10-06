package ai.drfx.scalperpro.signals

import java.util.UUID

enum class SignalDirection { LONG, SHORT }

enum class SignalStatus {
    DRAFT, WAITING, TRIGGERED, ACTIVE, TP1, TP2, TP3,
    BREAKEVEN, STOPPED, CANCELLED, CLOSED
}

data class SignalDefinition(
    val id: String = UUID.randomUUID().toString(),
    val instrument: String,
    val direction: SignalDirection,
    val entry: Double,
    val entryZoneHigh: Double? = null,
    val stop: Double,
    val tp1: Double? = null,
    val tp2: Double? = null,
    val tp3: Double? = null,
    val timeframe: String,
    val strategy: String,
    val source: String,
    val confidenceMethodology: String,
    val riskPercent: Double?,
    val newsRisk: String?,
    val createdAtEpochMillis: Long
)

data class SignalAuditEvent(
    val status: SignalStatus,
    val timestampEpochMillis: Long,
    val note: String? = null
)

data class SignalRecord(
    val definition: SignalDefinition,
    val auditTrail: List<SignalAuditEvent>
) {
    val status: SignalStatus
        get() = auditTrail.lastOrNull()?.status ?: SignalStatus.DRAFT
}

object SignalLifecycle {
    private val transitions: Map<SignalStatus, Set<SignalStatus>> = mapOf(
        SignalStatus.DRAFT to setOf(SignalStatus.WAITING, SignalStatus.CANCELLED),
        SignalStatus.WAITING to setOf(SignalStatus.TRIGGERED, SignalStatus.CANCELLED),
        SignalStatus.TRIGGERED to setOf(SignalStatus.ACTIVE, SignalStatus.CANCELLED),
        SignalStatus.ACTIVE to setOf(
            SignalStatus.TP1, SignalStatus.BREAKEVEN,
            SignalStatus.STOPPED, SignalStatus.CLOSED
        ),
        SignalStatus.TP1 to setOf(
            SignalStatus.TP2, SignalStatus.BREAKEVEN,
            SignalStatus.STOPPED, SignalStatus.CLOSED
        ),
        SignalStatus.TP2 to setOf(
            SignalStatus.TP3, SignalStatus.BREAKEVEN,
            SignalStatus.STOPPED, SignalStatus.CLOSED
        ),
        SignalStatus.TP3 to setOf(SignalStatus.CLOSED),
        SignalStatus.BREAKEVEN to setOf(
            SignalStatus.TP1, SignalStatus.TP2,
            SignalStatus.TP3, SignalStatus.CLOSED
        ),
        SignalStatus.STOPPED to emptySet(),
        SignalStatus.CANCELLED to emptySet(),
        SignalStatus.CLOSED to emptySet()
    )

    fun canTransition(from: SignalStatus, to: SignalStatus): Boolean =
        transitions[from].orEmpty().contains(to)

    fun transition(
        record: SignalRecord,
        to: SignalStatus,
        timestampEpochMillis: Long,
        note: String? = null
    ): SignalRecord {
        val from = record.status
        require(canTransition(from, to)) { "Invalid signal transition: $from -> $to" }

        return record.copy(
            auditTrail = record.auditTrail + SignalAuditEvent(
                status = to,
                timestampEpochMillis = timestampEpochMillis,
                note = note
            )
        )
    }
}
