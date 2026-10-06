package ai.drfx.scalperpro.backtest

import ai.drfx.scalperpro.market.Candle
import ai.drfx.scalperpro.strategy.DirectionPermission
import ai.drfx.scalperpro.strategy.StopMethod
import ai.drfx.scalperpro.strategy.StrategyCondition
import ai.drfx.scalperpro.strategy.StrategySpecification
import ai.drfx.scalperpro.strategy.TakeProfitMethod
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

data class StrategyBacktestCompatibility(
    val supported: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

object StrategyBacktestCompatibilityChecker {
    fun check(
        specification: StrategySpecification
    ): StrategyBacktestCompatibility {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        fun inspect(
            label: String,
            conditions: List<StrategyCondition>
        ) {
            conditions.forEachIndexed { index, condition ->
                val supported =
                    StrategyExpressionEngine.isSupported(
                        condition.expression
                    )

                if (!supported) {
                    errors +=
                        label +
                            " condition " +
                            (index + 1) +
                            " is not supported by the internal expression engine: " +
                            condition.expression
                }
            }
        }

        inspect(
            "Long entry",
            specification.entryConditions
        )
        inspect(
            "Short entry",
            specification.shortEntryConditions
        )
        inspect(
            "Filter",
            specification.filters
        )
        inspect(
            "Exit",
            specification.exitConditions
        )

        when (specification.stop.method) {
            StopMethod.FIXED_PRICE_DISTANCE,
            StopMethod.ATR_MULTIPLE -> Unit

            StopMethod.STRUCTURE ->
                errors +=
                    "Structure stops require an explicit deterministic expression before internal backtesting."

            StopMethod.CUSTOM ->
                errors +=
                    "Custom stop expressions are not yet supported by the internal backtest adapter."
        }

        when (
            specification.takeProfit.method
        ) {
            TakeProfitMethod.FIXED_R_MULTIPLE,
            TakeProfitMethod.FIXED_PRICE_DISTANCE -> Unit

            TakeProfitMethod.STRUCTURE ->
                errors +=
                    "Structure targets require an explicit deterministic expression before internal backtesting."

            TakeProfitMethod.TRAILING ->
                errors +=
                    "Trailing targets require an explicit activation and offset contract before internal backtesting."

            TakeProfitMethod.CUSTOM ->
                errors +=
                    "Custom target expressions are not yet supported by the internal backtest adapter."
        }

        if (specification.newsFilter.enabled) {
            warnings +=
                "News filtering is not applied without an economic-calendar dataset."
        }

        if (specification.pyramiding > 0) {
            warnings +=
                "The internal V1 core backtester allows one open position at a time; pyramiding is ignored."
        }

        return StrategyBacktestCompatibility(
            supported = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}

class StrategySpecificationBacktestAdapter(
    private val specification: StrategySpecification
) : BacktestStrategy {
    private var lastEntrySignalIndex: Int? = null

    override fun reset() {
        lastEntrySignalIndex = null
    }

    override fun evaluateEntry(
        history: List<Candle>
    ): EntryDecision? {
        if (history.isEmpty()) return null

        val currentIndex = history.lastIndex

        val lastIndex = lastEntrySignalIndex
        if (
            lastIndex != null &&
            currentIndex - lastIndex <=
                specification.cooldownBars
        ) {
            return null
        }

        if (!sessionAllowed(history.last())) {
            return null
        }

        val filtersOk =
            evaluateAll(
                specification.filters,
                history
            )

        if (!filtersOk) {
            return null
        }

        val longAllowed =
            specification.directionPermission !=
                DirectionPermission.SHORT_ONLY

        val shortAllowed =
            specification.directionPermission !=
                DirectionPermission.LONG_ONLY

        val longSignal =
            longAllowed &&
                specification.entryConditions
                    .isNotEmpty() &&
                evaluateAll(
                    specification.entryConditions,
                    history
                )

        val shortSignal =
            shortAllowed &&
                specification.shortEntryConditions
                    .isNotEmpty() &&
                evaluateAll(
                    specification.shortEntryConditions,
                    history
                )

        val side =
            when {
                longSignal && !shortSignal ->
                    PositionSide.LONG

                shortSignal && !longSignal ->
                    PositionSide.SHORT

                else -> null
            } ?: return null

        val stopDistance =
            stopDistance(history)
                ?: return null

        val targetDistance =
            targetDistance(
                stopDistance
            ) ?: return null

        if (
            !stopDistance.isFinite() ||
            !targetDistance.isFinite() ||
            stopDistance <= 0.0 ||
            targetDistance <= 0.0
        ) {
            return null
        }

        lastEntrySignalIndex =
            currentIndex

        return EntryDecision(
            side = side,
            stopDistance = stopDistance,
            targetDistance = targetDistance
        )
    }

    override fun shouldExit(
        history: List<Candle>,
        position: OpenPosition
    ): Boolean {
        if (
            specification.exitConditions
                .isEmpty()
        ) {
            return false
        }

        return evaluateAny(
            specification.exitConditions,
            history
        )
    }

    private fun evaluateAll(
        conditions: List<StrategyCondition>,
        history: List<Candle>
    ): Boolean =
        conditions.all { condition ->
            StrategyExpressionEngine.evaluateBoolean(
                expression =
                    condition.expression,
                history = history
            ) == true
        }

    private fun evaluateAny(
        conditions: List<StrategyCondition>,
        history: List<Candle>
    ): Boolean =
        conditions.any { condition ->
            StrategyExpressionEngine.evaluateBoolean(
                expression =
                    condition.expression,
                history = history
            ) == true
        }

    private fun stopDistance(
        history: List<Candle>
    ): Double? =
        when (specification.stop.method) {
            StopMethod.FIXED_PRICE_DISTANCE ->
                specification.stop.value

            StopMethod.ATR_MULTIPLE -> {
                val multiplier =
                    specification.stop.value ?:
                        return null

                val atr =
                    StrategyExpressionEngine.atr(
                        history = history,
                        length = 14
                    ) ?: return null

                atr * multiplier
            }

            StopMethod.STRUCTURE,
            StopMethod.CUSTOM ->
                null
        }

    private fun targetDistance(
        stopDistance: Double
    ): Double? =
        when (
            specification.takeProfit.method
        ) {
            TakeProfitMethod.FIXED_R_MULTIPLE ->
                specification.takeProfit.value
                    ?.let {
                        stopDistance * it
                    }

            TakeProfitMethod.FIXED_PRICE_DISTANCE ->
                specification.takeProfit.value

            TakeProfitMethod.STRUCTURE,
            TakeProfitMethod.TRAILING,
            TakeProfitMethod.CUSTOM ->
                null
        }

    private fun sessionAllowed(
        candle: Candle
    ): Boolean {
        val session =
            specification.session ?:
                return true

        if (session.allowedWindows.isEmpty()) {
            return true
        }

        val zone = runCatching {
            ZoneId.of(session.timezone)
        }.getOrNull() ?: return false

        val time =
            ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(
                    candle.openTimeEpochMillis
                ),
                zone
            )

        val minuteOfDay =
            time.hour * 60 +
                time.minute

        return session.allowedWindows.any {
            window ->
            SessionWindowParser.contains(
                window = window,
                minuteOfDay = minuteOfDay
            )
        }
    }
}

private object SessionWindowParser {
    private val pattern =
        Regex(
            """^(\d{2})(\d{2})-(\d{2})(\d{2})$"""
        )

    fun contains(
        window: String,
        minuteOfDay: Int
    ): Boolean {
        val match =
            pattern.matchEntire(
                window.trim()
            ) ?: return false

        val startHour =
            match.groupValues[1].toInt()
        val startMinute =
            match.groupValues[2].toInt()
        val endHour =
            match.groupValues[3].toInt()
        val endMinute =
            match.groupValues[4].toInt()

        if (
            startHour !in 0..23 ||
            endHour !in 0..23 ||
            startMinute !in 0..59 ||
            endMinute !in 0..59
        ) {
            return false
        }

        val start =
            startHour * 60 +
                startMinute
        val end =
            endHour * 60 +
                endMinute

        return if (start <= end) {
            minuteOfDay in start..end
        } else {
            minuteOfDay >= start ||
                minuteOfDay <= end
        }
    }
}

object StrategyExpressionEngine {
    private val comparisonPattern =
        Regex(
            """^(.+?)\s*(>=|<=|==|!=|>|<)\s*(.+)$"""
        )

    private val functionPattern =
        Regex(
            """^ta\.(ema|sma|rsi)\(close\s*,\s*(\d+)\)$""",
            RegexOption.IGNORE_CASE
        )

    private val atrPattern =
        Regex(
            """^ta\.atr\(\s*(\d+)\s*\)$""",
            RegexOption.IGNORE_CASE
        )

    private val crossoverPattern =
        Regex(
            """^ta\.(crossover|crossunder)\((.+),(.+)\)$""",
            RegexOption.IGNORE_CASE
        )

    private val numberPattern =
        Regex(
            """^-?\d+(?:\.\d+)?$"""
        )

    fun isSupported(
        expression: String
    ): Boolean {
        val text =
            stripOuterParentheses(
                expression.trim()
            )

        splitTopLevel(
            text,
            " or "
        )?.let { parts ->
            return parts.all(::isSupported)
        }

        splitTopLevel(
            text,
            " and "
        )?.let { parts ->
            return parts.all(::isSupported)
        }

        if (
            text.equals(
                "true",
                ignoreCase = true
            ) ||
            text.equals(
                "false",
                ignoreCase = true
            ) ||
            text.equals(
                "barstate.isconfirmed",
                ignoreCase = true
            )
        ) {
            return true
        }

        val cross =
            crossoverPattern
                .matchEntire(text)

        if (cross != null) {
            return operandSupported(
                cross.groupValues[2]
            ) &&
                operandSupported(
                    cross.groupValues[3]
                )
        }

        val comparison =
            comparisonPattern
                .matchEntire(text)
                ?: return false

        return operandSupported(
            comparison.groupValues[1]
        ) &&
            operandSupported(
                comparison.groupValues[3]
            )
    }

    fun evaluateBoolean(
        expression: String,
        history: List<Candle>
    ): Boolean? {
        if (history.isEmpty()) return null

        val text =
            stripOuterParentheses(
                expression.trim()
            )

        splitTopLevel(
            text,
            " or "
        )?.let { parts ->
            val values =
                parts.map {
                    evaluateBoolean(
                        it,
                        history
                    )
                }

            if (values.any { it == true }) {
                return true
            }

            return if (
                values.all { it == false }
            ) {
                false
            } else {
                null
            }
        }

        splitTopLevel(
            text,
            " and "
        )?.let { parts ->
            val values =
                parts.map {
                    evaluateBoolean(
                        it,
                        history
                    )
                }

            if (values.any { it == false }) {
                return false
            }

            return if (
                values.all { it == true }
            ) {
                true
            } else {
                null
            }
        }

        if (
            text.equals(
                "true",
                ignoreCase = true
            ) ||
            text.equals(
                "barstate.isconfirmed",
                ignoreCase = true
            )
        ) {
            return true
        }

        if (
            text.equals(
                "false",
                ignoreCase = true
            )
        ) {
            return false
        }

        val cross =
            crossoverPattern
                .matchEntire(text)

        if (cross != null) {
            if (history.size < 2) {
                return null
            }

            val currentLeft =
                value(
                    cross.groupValues[2],
                    history,
                    offset = 0
                ) ?: return null
            val currentRight =
                value(
                    cross.groupValues[3],
                    history,
                    offset = 0
                ) ?: return null
            val previousLeft =
                value(
                    cross.groupValues[2],
                    history,
                    offset = 1
                ) ?: return null
            val previousRight =
                value(
                    cross.groupValues[3],
                    history,
                    offset = 1
                ) ?: return null

            return when (
                cross.groupValues[1]
                    .lowercase()
            ) {
                "crossover" ->
                    previousLeft <=
                        previousRight &&
                        currentLeft >
                        currentRight

                "crossunder" ->
                    previousLeft >=
                        previousRight &&
                        currentLeft <
                        currentRight

                else ->
                    null
            }
        }

        val comparison =
            comparisonPattern
                .matchEntire(text)
                ?: return null

        val left =
            value(
                comparison.groupValues[1],
                history
            ) ?: return null

        val right =
            value(
                comparison.groupValues[3],
                history
            ) ?: return null

        return when (
            comparison.groupValues[2]
        ) {
            ">" -> left > right
            "<" -> left < right
            ">=" -> left >= right
            "<=" -> left <= right
            "==" ->
                abs(left - right) <
                    1e-9

            "!=" ->
                abs(left - right) >=
                    1e-9

            else -> null
        }
    }

    fun atr(
        history: List<Candle>,
        length: Int,
        offset: Int = 0
    ): Double? {
        require(length > 0)

        val endIndex =
            history.lastIndex -
                offset

        if (
            endIndex < 1 ||
            endIndex - length + 1 < 1
        ) {
            return null
        }

        val values =
            ((endIndex - length + 1)..endIndex)
                .map { index ->
                    val candle =
                        history[index]
                    val previousClose =
                        history[index - 1]
                            .close

                    maxOf(
                        candle.high -
                            candle.low,
                        abs(
                            candle.high -
                                previousClose
                        ),
                        abs(
                            candle.low -
                                previousClose
                        )
                    )
                }

        return values.average()
    }

    private fun value(
        raw: String,
        history: List<Candle>,
        offset: Int = 0
    ): Double? {
        val text =
            stripOuterParentheses(
                raw.trim()
            )

        if (numberPattern.matches(text)) {
            return text.toDouble()
        }

        val index =
            history.lastIndex -
                offset

        if (index < 0) return null

        when (text.lowercase()) {
            "close" ->
                return history[index].close
            "open" ->
                return history[index].open
            "high" ->
                return history[index].high
            "low" ->
                return history[index].low
        }

        atrPattern
            .matchEntire(text)
            ?.let { match ->
                return atr(
                    history = history,
                    length =
                        match.groupValues[1]
                            .toInt(),
                    offset = offset
                )
            }

        val function =
            functionPattern
                .matchEntire(text)
                ?: return null

        val length =
            function.groupValues[2]
                .toInt()

        return when (
            function.groupValues[1]
                .lowercase()
        ) {
            "ema" ->
                ema(
                    history = history,
                    length = length,
                    offset = offset
                )

            "sma" ->
                sma(
                    history = history,
                    length = length,
                    offset = offset
                )

            "rsi" ->
                rsi(
                    history = history,
                    length = length,
                    offset = offset
                )

            else -> null
        }
    }

    private fun operandSupported(
        raw: String
    ): Boolean {
        val text =
            stripOuterParentheses(
                raw.trim()
            )

        return numberPattern.matches(text) ||
            text.equals("close", true) ||
            text.equals("open", true) ||
            text.equals("high", true) ||
            text.equals("low", true) ||
            functionPattern.matches(text) ||
            atrPattern.matches(text)
    }

    private fun sma(
        history: List<Candle>,
        length: Int,
        offset: Int
    ): Double? {
        if (length <= 0) return null

        val end =
            history.lastIndex -
                offset
        val start =
            end - length + 1

        if (
            end < 0 ||
            start < 0
        ) {
            return null
        }

        return history
            .subList(
                start,
                end + 1
            )
            .map { it.close }
            .average()
    }

    private fun ema(
        history: List<Candle>,
        length: Int,
        offset: Int
    ): Double? {
        if (length <= 0) return null

        val end =
            history.lastIndex -
                offset

        if (
            end < length - 1
        ) {
            return null
        }

        val closes =
            history
                .subList(
                    0,
                    end + 1
                )
                .map {
                    it.close
                }

        val seed =
            closes
                .take(length)
                .average()

        val alpha =
            2.0 /
                (length + 1.0)

        var ema = seed

        for (
            index in length until
                closes.size
        ) {
            ema =
                closes[index] *
                    alpha +
                    ema *
                    (1.0 - alpha)
        }

        return ema
    }

    private fun rsi(
        history: List<Candle>,
        length: Int,
        offset: Int
    ): Double? {
        if (length <= 0) return null

        val end =
            history.lastIndex -
                offset
        val start =
            end - length

        if (
            end <= 0 ||
            start < 0
        ) {
            return null
        }

        var gains = 0.0
        var losses = 0.0

        for (
            index in
            (start + 1)..end
        ) {
            val delta =
                history[index].close -
                    history[index - 1].close

            if (delta > 0.0) {
                gains += delta
            } else if (delta < 0.0) {
                losses += -delta
            }
        }

        val averageGain =
            gains / length
        val averageLoss =
            losses / length

        if (
            averageLoss == 0.0 &&
            averageGain == 0.0
        ) {
            return 50.0
        }

        if (averageLoss == 0.0) {
            return 100.0
        }

        val rs =
            averageGain /
                averageLoss

        return 100.0 -
            100.0 /
                (1.0 + rs)
    }

    private fun splitTopLevel(
        expression: String,
        delimiter: String
    ): List<String>? {
        val result =
            mutableListOf<String>()

        var depth = 0
        var start = 0
        var index = 0

        while (
            index <=
                expression.length -
                delimiter.length
        ) {
            when (
                expression[index]
            ) {
                '(' -> depth += 1
                ')' -> depth -= 1
            }

            if (
                depth == 0 &&
                expression.regionMatches(
                    index = index,
                    other = delimiter,
                    otherOffset = 0,
                    length =
                        delimiter.length,
                    ignoreCase = true
                )
            ) {
                result +=
                    expression
                        .substring(
                            start,
                            index
                        )
                        .trim()

                start =
                    index +
                        delimiter.length
                index = start
                continue
            }

            index += 1
        }

        if (result.isEmpty()) {
            return null
        }

        result +=
            expression
                .substring(start)
                .trim()

        return result
    }

    private fun stripOuterParentheses(
        value: String
    ): String {
        var current =
            value.trim()

        while (
            current.startsWith("(") &&
            current.endsWith(")") &&
            outerPairWrapsAll(current)
        ) {
            current =
                current
                    .substring(
                        1,
                        current.length - 1
                    )
                    .trim()
        }

        return current
    }

    private fun outerPairWrapsAll(
        value: String
    ): Boolean {
        var depth = 0

        value.forEachIndexed {
                index,
                character ->
            when (character) {
                '(' -> depth += 1
                ')' -> depth -= 1
            }

            if (
                depth == 0 &&
                index < value.lastIndex
            ) {
                return false
            }

            if (depth < 0) {
                return false
            }
        }

        return depth == 0
    }
}
