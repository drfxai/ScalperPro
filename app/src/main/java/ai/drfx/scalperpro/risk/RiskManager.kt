package ai.drfx.scalperpro.risk

import kotlin.math.abs
import kotlin.math.floor

data class InstrumentRiskSpec(
    val symbol: String,
    val pnlPerPriceUnitPerQuantity: Double,
    val quantityStep: Double,
    val minQuantity: Double,
    val maxQuantity: Double
) {
    init {
        require(symbol.isNotBlank())
        require(pnlPerPriceUnitPerQuantity > 0.0)
        require(quantityStep > 0.0)
        require(minQuantity > 0.0)
        require(maxQuantity >= minQuantity)
    }
}

data class RiskCalculationRequest(
    val balance: Double,
    val riskPercent: Double,
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double?,
    val instrument: InstrumentRiskSpec
)

data class RiskCalculationResult(
    val maxLossAmount: Double,
    val stopDistance: Double,
    val rawQuantity: Double,
    val quantity: Double,
    val estimatedLossAtStop: Double,
    val rewardRisk: Double?,
    val withinInstrumentBounds: Boolean
)

object InstrumentRiskCalculator {
    fun calculate(request: RiskCalculationRequest): RiskCalculationResult {
        require(request.balance > 0.0)
        require(request.riskPercent > 0.0)
        require(request.riskPercent <= 10.0)

        val stopDistance = abs(request.entryPrice - request.stopPrice)
        require(stopDistance > 0.0)

        val maxLoss = request.balance * request.riskPercent / 100.0
        val lossPerQuantity =
            stopDistance * request.instrument.pnlPerPriceUnitPerQuantity
        val rawQuantity = maxLoss / lossPerQuantity

        val stepped =
            floor(rawQuantity / request.instrument.quantityStep) *
                request.instrument.quantityStep

        val bounded = stepped.coerceIn(
            request.instrument.minQuantity,
            request.instrument.maxQuantity
        )

        val estimatedLoss = bounded * lossPerQuantity
        val rr = request.targetPrice?.let { target ->
            abs(target - request.entryPrice) / stopDistance
        }

        return RiskCalculationResult(
            maxLossAmount = maxLoss,
            stopDistance = stopDistance,
            rawQuantity = rawQuantity,
            quantity = bounded,
            estimatedLossAtStop = estimatedLoss,
            rewardRisk = rr,
            withinInstrumentBounds =
                stepped in request.instrument.minQuantity..request.instrument.maxQuantity
        )
    }
}

data class ExposurePosition(
    val symbol: String,
    val riskAmount: Double,
    val correlationGroup: String?
)

data class ExposureSummary(
    val totalOpenRisk: Double,
    val groupRisk: Map<String, Double>
)

object PortfolioRiskAnalyzer {
    fun summarize(positions: List<ExposurePosition>): ExposureSummary {
        val grouped = positions
            .filter { !it.correlationGroup.isNullOrBlank() }
            .groupBy { it.correlationGroup.orEmpty() }
            .mapValues { (_, group) -> group.sumOf { it.riskAmount } }

        return ExposureSummary(
            totalOpenRisk = positions.sumOf { it.riskAmount },
            groupRisk = grouped
        )
    }
}
