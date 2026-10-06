package ai.drfx.scalperpro

import ai.drfx.scalperpro.journal.JournalEntry
import ai.drfx.scalperpro.journal.JournalInsightEngine
import ai.drfx.scalperpro.journal.JournalStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalStatisticsTest {
    private fun entry(id: String, r: Double, session: String): JournalEntry =
        JournalEntry(
            id = id,
            instrument = "XAUUSD",
            direction = "LONG",
            entryPrice = 2000.0,
            exitPrice = 2010.0,
            stopPrice = 1995.0,
            targetPrice = 2010.0,
            rResult = r,
            strategyId = "s1",
            strategyName = "Gold Trend",
            session = session,
            openedAtEpochMillis = 1L,
            closedAtEpochMillis = 2L,
            plannedRiskPercent = 1.0,
            beforeImage = null,
            afterImage = null,
            newsContext = null,
            emotion = null,
            mistakes = emptySet(),
            notes = null
        )

    @Test
    fun statisticsUseClosedTrades() {
        val stats = JournalStatistics.calculate(
            listOf(
                entry("1", 2.0, "London"),
                entry("2", -1.0, "New York")
            )
        )

        assertEquals(2, stats.closedTrades)
        assertEquals(50.0, stats.winRatePercent ?: 0.0, 0.0001)
        assertEquals(0.5, stats.averageR ?: 0.0, 0.0001)
    }

    @Test
    fun smallSamplesDoNotProduceStrongBehavioralClaims() {
        val insights = JournalInsightEngine.derive(
            listOf(entry("1", 1.0, "London"))
        )

        assertTrue(insights.any { it.code == "INSUFFICIENT_SAMPLE" })
    }
}
