package ai.drfx.scalperpro.code

data class Mql5TranslationResult(
    val supported: Boolean,
    val expression: String?,
    val reason: String?
)

object Mql5ExpressionTranslator {
    private val comparisonRegex =
        Regex(
            """^(.+?)\s*(>=|<=|==|!=|>|<)\s*(.+)$"""
        )

    private val emaRegex =
        Regex(
            """^ta\.ema\(close\s*,\s*(\d+)\)$""",
            RegexOption.IGNORE_CASE
        )

    private val smaRegex =
        Regex(
            """^ta\.sma\(close\s*,\s*(\d+)\)$""",
            RegexOption.IGNORE_CASE
        )

    private val rsiRegex =
        Regex(
            """^ta\.rsi\(close\s*,\s*(\d+)\)$""",
            RegexOption.IGNORE_CASE
        )

    private val atrRegex =
        Regex(
            """^ta\.atr\(\s*(\d+)\s*\)$""",
            RegexOption.IGNORE_CASE
        )

    private val numberRegex =
        Regex(
            """^-?\d+(?:\.\d+)?$"""
        )

    fun translate(
        pineExpression: String
    ): Mql5TranslationResult {
        val expression =
            stripOuterParentheses(
                pineExpression.trim()
            )

        if (expression.equals("true", true)) {
            return supported("true")
        }

        if (expression.equals("false", true)) {
            return supported("false")
        }

        if (
            expression.equals(
                "barstate.isconfirmed",
                true
            )
        ) {
            return supported("true")
        }

        val comparison =
            comparisonRegex
                .matchEntire(expression)

        if (comparison != null) {
            val left =
                translateOperand(
                    comparison.groupValues[1]
                )
            val operator =
                comparison.groupValues[2]
            val right =
                translateOperand(
                    comparison.groupValues[3]
                )

            if (
                left != null &&
                right != null
            ) {
                return supported(
                    "($left $operator $right)"
                )
            }

            return unsupported(
                "Unsupported Pine operand in comparison: " +
                    expression
            )
        }

        return unsupported(
            "Unsupported Pine condition: " +
                expression
        )
    }

    private fun translateOperand(
        raw: String
    ): String? {
        val value =
            stripOuterParentheses(
                raw.trim()
            )

        emaRegex.matchEntire(value)
            ?.groupValues
            ?.get(1)
            ?.let {
                return "ReadMA($it, 1, MODE_EMA)"
            }

        smaRegex.matchEntire(value)
            ?.groupValues
            ?.get(1)
            ?.let {
                return "ReadMA($it, 1, MODE_SMA)"
            }

        rsiRegex.matchEntire(value)
            ?.groupValues
            ?.get(1)
            ?.let {
                return "ReadRSI($it, 1)"
            }

        atrRegex.matchEntire(value)
            ?.groupValues
            ?.get(1)
            ?.let {
                return "ReadATR($it, 1)"
            }

        return when {
            value.equals("close", true) ->
                "iClose(_Symbol, PERIOD_CURRENT, 1)"
            value.equals("open", true) ->
                "iOpen(_Symbol, PERIOD_CURRENT, 1)"
            value.equals("high", true) ->
                "iHigh(_Symbol, PERIOD_CURRENT, 1)"
            value.equals("low", true) ->
                "iLow(_Symbol, PERIOD_CURRENT, 1)"
            numberRegex.matches(value) ->
                value
            else ->
                null
        }
    }

    private fun stripOuterParentheses(
        value: String
    ): String {
        var current = value.trim()

        while (
            current.startsWith("(") &&
            current.endsWith(")") &&
            balancedOuterPair(current)
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

    private fun balancedOuterPair(
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

    private fun supported(
        expression: String
    ) =
        Mql5TranslationResult(
            supported = true,
            expression = expression,
            reason = null
        )

    private fun unsupported(
        reason: String
    ) =
        Mql5TranslationResult(
            supported = false,
            expression = null,
            reason = reason
        )
}
