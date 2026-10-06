package ai.drfx.scalperpro.strategy

import java.util.UUID

enum class MarketType {
    FOREX,
    METALS,
    CRYPTO,
    INDICES,
    COMMODITIES,
    OTHER
}

enum class DirectionPermission {
    LONG_ONLY,
    SHORT_ONLY,
    BOTH
}

enum class StopMethod {
    FIXED_PRICE_DISTANCE,
    ATR_MULTIPLE,
    STRUCTURE,
    CUSTOM
}

enum class TakeProfitMethod {
    FIXED_R_MULTIPLE,
    FIXED_PRICE_DISTANCE,
    STRUCTURE,
    TRAILING,
    CUSTOM
}

data class IndicatorDefinition(
    val id: String,
    val name: String,
    val parameters: Map<String, String>
)

data class StrategyCondition(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val expression: String
)

data class StrategySession(
    val timezone: String,
    val allowedWindows: List<String>
)

data class StrategyRisk(
    val riskPercentPerTrade: Double,
    val maxConcurrentPositions: Int = 1,
    val maxDailyLossPercent: Double? = null
)

data class StopDefinition(
    val method: StopMethod,
    val value: Double? = null,
    val expression: String? = null
)

data class TakeProfitDefinition(
    val method: TakeProfitMethod,
    val value: Double? = null,
    val expression: String? = null
)

data class TrailingDefinition(
    val enabled: Boolean,
    val expression: String? = null
)

data class NewsFilterDefinition(
    val enabled: Boolean,
    val blockMinutesBeforeHighImpact: Int = 0,
    val blockMinutesAfterHighImpact: Int = 0
)

data class ExecutionAssumptions(
    val commissionPercent: Double = 0.04,
    val slippageTicks: Int = 1,
    val processOrdersOnClose: Boolean = false
) {
    init {
        require(commissionPercent >= 0.0)
        require(slippageTicks >= 0)
    }
}

data class StrategySpecification(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val market: MarketType,
    val symbol: String,
    val primaryTimeframe: String,
    val confirmationTimeframes: List<String> = emptyList(),
    val indicators: List<IndicatorDefinition> = emptyList(),
    val entryConditions: List<StrategyCondition>,
    val shortEntryConditions: List<StrategyCondition> = emptyList(),
    val exitConditions: List<StrategyCondition> = emptyList(),
    val filters: List<StrategyCondition> = emptyList(),
    val session: StrategySession?,
    val risk: StrategyRisk,
    val stop: StopDefinition,
    val takeProfit: TakeProfitDefinition,
    val trailing: TrailingDefinition = TrailingDefinition(false),
    val newsFilter: NewsFilterDefinition = NewsFilterDefinition(false),
    val execution: ExecutionAssumptions = ExecutionAssumptions(),
    val cooldownBars: Int = 0,
    val pyramiding: Int = 0,
    val directionPermission: DirectionPermission = DirectionPermission.BOTH
)

data class StrategyRevision(
    val revision: Int,
    val createdAtEpochMillis: Long,
    val specification: StrategySpecification,
    val changeSummary: String
)

data class StrategyValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

object StrategySpecificationValidator {
    fun validate(spec: StrategySpecification): StrategyValidationResult {
        val errors = mutableListOf<String>()

        if (spec.name.isBlank()) errors += "Strategy name is required."
        if (spec.symbol.isBlank()) errors += "Symbol is required."
        if (spec.primaryTimeframe.isBlank()) errors += "Primary timeframe is required."
        if (
            spec.directionPermission != DirectionPermission.SHORT_ONLY &&
            spec.entryConditions.isEmpty()
        ) {
            errors += "At least one long entry condition is required."
        }
        if (
            spec.directionPermission == DirectionPermission.SHORT_ONLY &&
            spec.shortEntryConditions.isEmpty()
        ) {
            errors += "Short-only strategies require at least one short entry condition."
        }
        if (spec.risk.riskPercentPerTrade <= 0.0) errors += "Risk per trade must be greater than zero."
        if (spec.risk.riskPercentPerTrade > 10.0) errors += "Risk per trade above 10% is rejected."
        if (spec.risk.maxConcurrentPositions < 1) errors += "Max concurrent positions must be at least one."
        if (spec.cooldownBars < 0) errors += "Cooldown bars cannot be negative."
        if (spec.pyramiding < 0) errors += "Pyramiding cannot be negative."
        if (spec.execution.commissionPercent < 0.0) {
            errors += "Commission cannot be negative."
        }
        if (spec.execution.slippageTicks < 0) {
            errors += "Slippage ticks cannot be negative."
        }

        when (spec.stop.method) {
            StopMethod.FIXED_PRICE_DISTANCE,
            StopMethod.ATR_MULTIPLE -> {
                if (spec.stop.value == null || spec.stop.value <= 0.0) {
                    errors += "Selected stop method requires a positive value."
                }
            }
            StopMethod.CUSTOM -> {
                if (spec.stop.expression.isNullOrBlank()) {
                    errors += "Custom stop method requires an expression."
                }
            }
            StopMethod.STRUCTURE -> Unit
        }

        when (spec.takeProfit.method) {
            TakeProfitMethod.FIXED_R_MULTIPLE,
            TakeProfitMethod.FIXED_PRICE_DISTANCE -> {
                if (spec.takeProfit.value == null || spec.takeProfit.value <= 0.0) {
                    errors += "Selected take-profit method requires a positive value."
                }
            }
            TakeProfitMethod.CUSTOM -> {
                if (spec.takeProfit.expression.isNullOrBlank()) {
                    errors += "Custom take-profit method requires an expression."
                }
            }
            TakeProfitMethod.STRUCTURE,
            TakeProfitMethod.TRAILING -> Unit
        }

        return StrategyValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
}

class StrategyRevisionHistory(
    initial: StrategyRevision
) {
    private val revisions = mutableListOf(initial)

    fun all(): List<StrategyRevision> = revisions.toList()

    fun latest(): StrategyRevision = revisions.last()

    fun append(
        specification: StrategySpecification,
        createdAtEpochMillis: Long,
        changeSummary: String
    ): StrategyRevision {
        require(changeSummary.isNotBlank())
        val next = StrategyRevision(
            revision = latest().revision + 1,
            createdAtEpochMillis = createdAtEpochMillis,
            specification = specification,
            changeSummary = changeSummary
        )
        revisions += next
        return next
    }
}
