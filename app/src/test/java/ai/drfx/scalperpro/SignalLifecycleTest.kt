package ai.drfx.scalperpro

import ai.drfx.scalperpro.signals.SignalAuditEvent
import ai.drfx.scalperpro.signals.SignalDefinition
import ai.drfx.scalperpro.signals.SignalDirection
import ai.drfx.scalperpro.signals.SignalLifecycle
import ai.drfx.scalperpro.signals.SignalRecord
import ai.drfx.scalperpro.signals.SignalStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalLifecycleTest {
    private fun draftRecord(): SignalRecord {
        val definition = SignalDefinition(
            instrument = "XAUUSD",
            direction = SignalDirection.LONG,
            entry = 2000.0,
            stop = 1990.0,
            tp1 = 2020.0,
            timeframe = "15m",
            strategy = "test",
            source = "unit-test",
            confidenceMethodology = "deterministic test fixture",
            riskPercent = 1.0,
            newsRisk = null,
            createdAtEpochMillis = 1L
        )

        return SignalRecord(
            definition = definition,
            auditTrail = listOf(
                SignalAuditEvent(
                    status = SignalStatus.DRAFT,
                    timestampEpochMillis = 1L
                )
            )
        )
    }

    @Test
    fun validTransitionAppendsAuditEvent() {
        val next = SignalLifecycle.transition(
            record = draftRecord(),
            to = SignalStatus.WAITING,
            timestampEpochMillis = 2L
        )

        assertEquals(SignalStatus.WAITING, next.status)
        assertEquals(2, next.auditTrail.size)
    }

    @Test
    fun terminalLossCannotBeReopened() {
        assertFalse(
            SignalLifecycle.canTransition(
                SignalStatus.STOPPED,
                SignalStatus.ACTIVE
            )
        )
    }

    @Test
    fun draftCanEnterWaitingState() {
        assertTrue(
            SignalLifecycle.canTransition(
                SignalStatus.DRAFT,
                SignalStatus.WAITING
            )
        )
    }
}
