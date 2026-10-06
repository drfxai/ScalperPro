package ai.drfx.scalperpro.journal

import java.util.UUID

enum class TradeEmotion {
    CALM,
    CONFIDENT,
    ANXIOUS,
    FOMO,
    REVENGE,
    IMPULSIVE,
    TIRED,
    OTHER
}

data class JournalImageRef(
    val id: String,
    val description: String?
)

data class JournalEntry(
    val id: String = UUID.randomUUID().toString(),
    val instrument: String,
    val direction: String,
    val entryPrice: Double,
    val exitPrice: Double?,
    val stopPrice: Double?,
    val targetPrice: Double?,
    val rResult: Double?,
    val strategyId: String?,
    val strategyName: String?,
    val session: String?,
    val openedAtEpochMillis: Long,
    val closedAtEpochMillis: Long?,
    val plannedRiskPercent: Double?,
    val beforeImage: JournalImageRef?,
    val afterImage: JournalImageRef?,
    val newsContext: String?,
    val emotion: TradeEmotion?,
    val mistakes: Set<String>,
    val notes: String?
)

data class JournalStats(
    val sampleSize: Int,
    val closedTrades: Int,
    val winRatePercent: Double?,
    val averageR: Double?,
    val bestSession: String?,
    val worstSession: String?,
    val strategyAverageR: Map<String, Double>
)

object JournalStatistics {
    fun calculate(entries: List<JournalEntry>): JournalStats {
        val closed = entries.filter { it.rResult != null }
        val rs = closed.mapNotNull { it.rResult }
        val wins = rs.count { it > 0.0 }

        val sessions = closed
            .filter { !it.session.isNullOrBlank() }
            .groupBy { it.session.orEmpty() }
            .mapValues { (_, trades) -> trades.mapNotNull { it.rResult }.averageOrNull() }
            .filterValues { it != null }
            .mapValues { it.value ?: 0.0 }

        val strategies = closed
            .filter { !it.strategyName.isNullOrBlank() }
            .groupBy { it.strategyName.orEmpty() }
            .mapValues { (_, trades) -> trades.mapNotNull { it.rResult }.averageOrNull() }
            .filterValues { it != null }
            .mapValues { it.value ?: 0.0 }

        return JournalStats(
            sampleSize = entries.size,
            closedTrades = closed.size,
            winRatePercent = if (rs.isNotEmpty()) {
                wins.toDouble() / rs.size * 100.0
            } else {
                null
            },
            averageR = rs.averageOrNull(),
            bestSession = sessions.maxByOrNull { it.value }?.key,
            worstSession = sessions.minByOrNull { it.value }?.key,
            strategyAverageR = strategies
        )
    }

    private fun List<Double>.averageOrNull(): Double? =
        if (isEmpty()) null else average()
}

data class JournalInsight(
    val code: String,
    val message: String,
    val sampleSize: Int
)

object JournalInsightEngine {
    const val MIN_SAMPLE_SIZE = 10

    fun derive(entries: List<JournalEntry>): List<JournalInsight> {
        if (entries.size < MIN_SAMPLE_SIZE) {
            return listOf(
                JournalInsight(
                    code = "INSUFFICIENT_SAMPLE",
                    message = "More journal history is required before strong behavioral conclusions are shown.",
                    sampleSize = entries.size
                )
            )
        }

        val insights = mutableListOf<JournalInsight>()
        val revengeCount = entries.count { it.emotion == TradeEmotion.REVENGE }

        if (revengeCount.toDouble() / entries.size >= 0.2) {
            insights += JournalInsight(
                code = "REVENGE_PATTERN",
                message = "Revenge-trading emotion appears in at least 20% of recorded entries.",
                sampleSize = entries.size
            )
        }

        return insights
    }
}
