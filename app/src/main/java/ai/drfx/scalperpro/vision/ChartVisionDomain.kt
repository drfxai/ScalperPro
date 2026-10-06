package ai.drfx.scalperpro.vision

import ai.drfx.scalperpro.market.Candle
import ai.drfx.scalperpro.news.EconomicEvent
import ai.drfx.scalperpro.news.NewsItem

enum class ChartImageMimeType(val value: String) {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp")
}

data class ChartImageDescriptor(
    val id: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String?
)

data class VisionRiskContext(
    val accountCurrency: String,
    val riskPercentPerTrade: Double,
    val maxDailyLossPercent: Double?
)

data class ChartVisionContext(
    val symbol: String,
    val timeframe: String,
    val recentCandles: List<Candle>,
    val higherTimeframeCandles: List<Candle> = emptyList(),
    val economicEvents: List<EconomicEvent> = emptyList(),
    val relevantNews: List<NewsItem> = emptyList(),
    val volatilityLabel: String?,
    val marketSession: String?,
    val userRisk: VisionRiskContext?
)

data class ChartVisionRequest(
    val image: ChartImageDescriptor,
    val context: ChartVisionContext
)

data class ChartVisionSection(
    val title: String,
    val content: String,
    val evidence: List<String> = emptyList()
)

data class ChartVisionAnalysis(
    val marketStructure: ChartVisionSection,
    val trend: ChartVisionSection,
    val supportResistance: ChartVisionSection,
    val liquidity: ChartVisionSection,
    val bosChoch: ChartVisionSection?,
    val fairValueGaps: ChartVisionSection?,
    val momentum: ChartVisionSection,
    val volatility: ChartVisionSection,
    val session: ChartVisionSection,
    val newsContext: ChartVisionSection,
    val possibleScenario: ChartVisionSection,
    val invalidation: ChartVisionSection,
    val riskNotes: ChartVisionSection
)

data class VisionValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

object ChartVisionRequestValidator {
    const val MAX_IMAGE_BYTES: Long = 10L * 1024L * 1024L

    fun validate(request: ChartVisionRequest): VisionValidationResult {
        val errors = mutableListOf<String>()
        val allowedTypes = ChartImageMimeType.entries.map { it.value }.toSet()

        if (request.image.id.isBlank()) errors += "Image id is required."
        if (request.image.mimeType !in allowedTypes) errors += "Unsupported image MIME type."
        if (request.image.sizeBytes <= 0L) errors += "Image size must be greater than zero."
        if (request.image.sizeBytes > MAX_IMAGE_BYTES) errors += "Image exceeds 10 MB limit."
        if (request.context.symbol.isBlank()) errors += "Symbol is required."
        if (request.context.timeframe.isBlank()) errors += "Timeframe is required."

        return VisionValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
}
