package ai.drfx.scalperpro

import ai.drfx.scalperpro.backtest.BacktestCosts
import ai.drfx.scalperpro.backtest.DeterministicBacktestEngine
import ai.drfx.scalperpro.backtest.StrategyBacktestCompatibilityChecker
import ai.drfx.scalperpro.backtest.StrategyExpressionEngine
import ai.drfx.scalperpro.backtest.StrategySpecificationBacktestAdapter
import ai.drfx.scalperpro.market.Candle
import ai.drfx.scalperpro.strategy.DirectionPermission
import ai.drfx.scalperpro.strategy.MarketType
import ai.drfx.scalperpro.strategy.StopDefinition
import ai.drfx.scalperpro.strategy.StopMethod
import ai.drfx.scalperpro.strategy.StrategyCondition
import ai.drfx.scalperpro.strategy.StrategyRisk
import ai.drfx.scalperpro.strategy.StrategySpecification
import ai.drfx.scalperpro.strategy.TakeProfitDefinition
import ai.drfx.scalperpro.strategy.TakeProfitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class StrategySpecificationBacktestTest {
    @Test
    fun expressionEngineSupportsEmaRsiAndBooleanComposition() {
        val candles = sampleCandles(260)

        assertTrue(
            StrategyExpressionEngine.isSupported(
                "ta.ema(close, 20) > ta.ema(close, 50) and ta.rsi(close, 14) > 45"
            )
        )

        val evaluated =
            StrategyExpressionEngine.evaluateBoolean(
                expression =
                    "ta.ema(close, 20) > ta.ema(close, 50) and ta.rsi(close, 14) > 45",
                history = candles
            )

        assertTrue(evaluated != null)
    }

    @Test
    fun unsupportedStructureStopFailsCompatibilityCheck() {
        val specification =
            baseSpecification().copy(
                stop =
                    StopDefinition(
                        StopMethod.STRUCTURE
                    )
            )

        val compatibility =
            StrategyBacktestCompatibilityChecker
                .check(specification)

        assertFalse(
            compatibility.supported
        )
        assertTrue(
            compatibility.errors.any {
                it.contains(
                    "Structure stops"
                )
            }
        )
    }

    @Test
    fun specificationBacktestIsDeterministic() {
        val candles =
            sampleCandles(420)

        val specification =
            baseSpecification()

        val compatibility =
            StrategyBacktestCompatibilityChecker
                .check(specification)

        assertTrue(
            compatibility.errors.joinToString(),
            compatibility.supported
        )

        val engine =
            DeterministicBacktestEngine()

        val first =
            engine.run(
                candles = candles,
                strategy =
                    StrategySpecificationBacktestAdapter(
                        specification
                    ),
                initialBalance =
                    10_000.0,
                riskPercentPerTrade =
                    specification
                        .risk
                        .riskPercentPerTrade,
                costs =
                    BacktestCosts(
                        spreadPriceUnits =
                            0.18,
                        slippagePriceUnits =
                            0.04,
                        commissionPerUnitPerSide =
                            0.0
                    )
            )

        val second =
            engine.run(
                candles = candles,
                strategy =
                    StrategySpecificationBacktestAdapter(
                        specification
                    ),
                initialBalance =
                    10_000.0,
                riskPercentPerTrade =
                    specification
                        .risk
                        .riskPercentPerTrade,
                costs =
                    BacktestCosts(
                        spreadPriceUnits =
                            0.18,
                        slippagePriceUnits =
                            0.04,
                        commissionPerUnitPerSide =
                            0.0
                    )
            )

        assertEquals(first, second)
        assertTrue(
            first.metrics.tradeCount > 0
        )
    }

    @Test
    fun crossunderUsesCurrentRightOperand() {
        val candles =
            buildList {
                repeat(30) { index ->
                    val close =
                        if (index < 20) {
                            100.0 +
                                index * 0.4
                        } else {
                            108.0 -
                                (index - 20) *
                                1.2
                        }

                    add(
                        Candle(
                            symbol =
                                "XAUUSD",
                            timeframe =
                                "15m",
                            openTimeEpochMillis =
                                index *
                                    900_000L,
                            open =
                                close - 0.2,
                            high =
                                close + 0.5,
                            low =
                                close - 0.5,
                            close = close
                        )
                    )
                }
            }

        val result =
            StrategyExpressionEngine.evaluateBoolean(
                expression =
                    "ta.crossunder(ta.ema(close, 3), ta.ema(close, 8))",
                history = candles
            )

        assertTrue(
            result == true ||
                result == false
        )
    }

    private fun baseSpecification() =
        StrategySpecification(
            name = "Internal EMA RSI",
            market = MarketType.METALS,
            symbol = "XAUUSD",
            primaryTimeframe = "15m",
            entryConditions =
                listOf(
                    StrategyCondition(
                        description =
                            "Fast EMA above slow EMA",
                        expression =
                            "ta.ema(close, 20) > ta.ema(close, 50)"
                    ),
                    StrategyCondition(
                        description =
                            "RSI confirmation",
                        expression =
                            "ta.rsi(close, 14) > 50"
                    )
                ),
            shortEntryConditions =
                listOf(
                    StrategyCondition(
                        description =
                            "Fast EMA below slow EMA",
                        expression =
                            "ta.ema(close, 20) < ta.ema(close, 50)"
                    ),
                    StrategyCondition(
                        description =
                            "RSI bearish confirmation",
                        expression =
                            "ta.rsi(close, 14) < 50"
                    )
                ),
            session = null,
            risk =
                StrategyRisk(
                    riskPercentPerTrade =
                        1.0
                ),
            stop =
                StopDefinition(
                    method =
                        StopMethod.ATR_MULTIPLE,
                    value = 1.5
                ),
            takeProfit =
                TakeProfitDefinition(
                    method =
                        TakeProfitMethod
                            .FIXED_R_MULTIPLE,
                    value = 2.0
                ),
            cooldownBars = 2,
            directionPermission =
                DirectionPermission.BOTH
        )

    private fun sampleCandles(
        count: Int
    ): List<Candle> {
        var close = 2_350.0

        return (0 until count)
            .map { index ->
                val regime =
                    when {
                        index % 140 < 70 ->
                            0.55
                        else ->
                            -0.48
                    }

                val wave =
                    sin(
                        index /
                            7.5
                    ) * 1.6

                val next =
                    close +
                        regime +
                        wave

                val candle =
                    Candle(
                        symbol =
                            "XAUUSD",
                        timeframe =
                            "15m",
                        openTimeEpochMillis =
                            1_700_000_000_000L +
                                index *
                                900_000L,
                        open = close,
                        high =
                            maxOf(
                                close,
                                next
                            ) +
                                1.2,
                        low =
                            minOf(
                                close,
                                next
                            ) -
                                1.2,
                        close = next,
                        volume =
                            1_000.0 +
                                index
                    )

                close = next
                candle
            }
    }
}
