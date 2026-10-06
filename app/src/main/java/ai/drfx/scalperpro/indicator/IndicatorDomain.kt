package ai.drfx.scalperpro.indicator

import java.util.UUID

enum class IndicatorKind {
    TREND,
    MOMENTUM,
    VOLATILITY,
    VOLUME,
    SUPPORT_RESISTANCE,
    MARKET_STRUCTURE,
    SIGNAL,
    HYBRID
}

enum class RepaintPolicy {
    CONFIRMED_BARS_ONLY,
    INTRABAR_ALLOWED,
    VISUAL_ONLY
}

data class IndicatorInput(
    val id: String,
    val title: String,
    val type: String,
    val defaultValue: String,
    val minValue: String? = null,
    val maxValue: String? = null,
    val tooltip: String? = null,
    val group: String? = null
)

data class IndicatorCalculation(
    val id: String,
    val variable: String,
    val expression: String,
    val explanation: String
)

data class IndicatorPlot(
    val id: String,
    val title: String,
    val expression: String,
    val color: String,
    val width: Int = 2
)

data class IndicatorAlert(
    val id: String,
    val title: String,
    val conditionExpression: String,
    val messageTemplate: String
)

data class IndicatorSpecification(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val shortTitle: String,
    val kind: IndicatorKind,
    val overlay: Boolean,
    val designedTimeframes: List<String>,
    val inputs: List<IndicatorInput>,
    val calculations: List<IndicatorCalculation>,
    val plots: List<IndicatorPlot>,
    val alerts: List<IndicatorAlert>,
    val repaintPolicy: RepaintPolicy,
    val beginnerExplanation: String
)

data class IndicatorValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

object IndicatorSpecificationValidator {
    fun validate(
        specification: IndicatorSpecification
    ): IndicatorValidationResult {
        val errors = mutableListOf<String>()

        if (specification.name.isBlank()) errors += "Indicator name is required."
        if (specification.shortTitle.isBlank()) errors += "Short title is required."
        if (specification.shortTitle.length > 30) {
            errors += "Short title should remain compact."
        }
        if (specification.calculations.isEmpty() && specification.plots.isEmpty()) {
            errors += "Indicator needs at least one calculation or plot."
        }
        if (specification.designedTimeframes.isEmpty()) {
            errors += "At least one designed timeframe is required."
        }

        val duplicateVariables = specification.calculations
            .groupBy { it.variable }
            .filterValues { it.size > 1 }
            .keys

        if (duplicateVariables.isNotEmpty()) {
            errors += "Duplicate calculation variables: " +
                duplicateVariables.joinToString()
        }

        return IndicatorValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
}

object IndicatorTemplateCatalog {
    val emaRsiTrend = IndicatorSpecification(
        name = "EMA RSI Trend Assistant",
        shortTitle = "EMA RSI Trend",
        kind = IndicatorKind.HYBRID,
        overlay = true,
        designedTimeframes = listOf("5m", "15m", "1h", "4h"),
        inputs = listOf(
            IndicatorInput(
                id = "fast",
                title = "Fast EMA",
                type = "int",
                defaultValue = "50",
                minValue = "1",
                maxValue = "500",
                tooltip = "Fast trend average.",
                group = "Trend"
            ),
            IndicatorInput(
                id = "slow",
                title = "Slow EMA",
                type = "int",
                defaultValue = "200",
                minValue = "2",
                maxValue = "1000",
                tooltip = "Slow trend average.",
                group = "Trend"
            ),
            IndicatorInput(
                id = "rsi",
                title = "RSI Length",
                type = "int",
                defaultValue = "14",
                minValue = "2",
                maxValue = "100",
                tooltip = "Momentum confirmation period.",
                group = "Momentum"
            )
        ),
        calculations = listOf(
            IndicatorCalculation(
                id = "emaFast",
                variable = "emaFast",
                expression = "ta.ema(close, fastLen)",
                explanation = "Fast exponential moving average."
            ),
            IndicatorCalculation(
                id = "emaSlow",
                variable = "emaSlow",
                expression = "ta.ema(close, slowLen)",
                explanation = "Slow exponential moving average."
            ),
            IndicatorCalculation(
                id = "rsiValue",
                variable = "rsiValue",
                expression = "ta.rsi(close, rsiLen)",
                explanation = "RSI momentum."
            )
        ),
        plots = listOf(
            IndicatorPlot(
                id = "fastPlot",
                title = "Fast EMA",
                expression = "emaFast",
                color = "color.aqua"
            ),
            IndicatorPlot(
                id = "slowPlot",
                title = "Slow EMA",
                expression = "emaSlow",
                color = "color.purple"
            )
        ),
        alerts = listOf(
            IndicatorAlert(
                id = "bull",
                title = "Bullish confirmation",
                conditionExpression = "emaFast > emaSlow and rsiValue > 50 and barstate.isconfirmed",
                messageTemplate = "{{ticker}} bullish EMA/RSI confirmation"
            ),
            IndicatorAlert(
                id = "bear",
                title = "Bearish confirmation",
                conditionExpression = "emaFast < emaSlow and rsiValue < 50 and barstate.isconfirmed",
                messageTemplate = "{{ticker}} bearish EMA/RSI confirmation"
            )
        ),
        repaintPolicy = RepaintPolicy.CONFIRMED_BARS_ONLY,
        beginnerExplanation = "The fast EMA shows the shorter trend, the slow EMA shows the broader trend, and RSI confirms momentum."
    )
}
