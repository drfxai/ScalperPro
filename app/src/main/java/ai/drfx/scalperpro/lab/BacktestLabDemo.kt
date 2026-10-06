package ai.drfx.scalperpro.lab

import ai.drfx.scalperpro.backtest.BacktestCosts
import ai.drfx.scalperpro.backtest.BacktestResult
import ai.drfx.scalperpro.backtest.DeterministicBacktestEngine
import ai.drfx.scalperpro.backtest.StrategyBacktestCompatibility
import ai.drfx.scalperpro.backtest.StrategyBacktestCompatibilityChecker
import ai.drfx.scalperpro.backtest.StrategySpecificationBacktestAdapter
import ai.drfx.scalperpro.market.Candle
import ai.drfx.scalperpro.strategy.StrategySpecification
import kotlin.math.sin

data class BacktestLabReport(
    val compatibility: StrategyBacktestCompatibility,
    val result: BacktestResult?,
    val candleCount: Int,
    val datasetLabel: String,
    val costs: BacktestCosts
)

object BacktestLabDemo {
    fun run(
        specification: StrategySpecification
    ): BacktestLabReport {
        val compatibility =
            StrategyBacktestCompatibilityChecker
                .check(specification)

        val candles =
            syntheticCandles(
                symbol =
                    specification.symbol,
                timeframe =
                    specification.primaryTimeframe,
                count = 620
            )

        val costs =
            BacktestCosts(
                spreadPriceUnits = 0.18,
                slippagePriceUnits = 0.04,
                commissionPerUnitPerSide = 0.0
            )

        if (!compatibility.supported) {
            return BacktestLabReport(
                compatibility =
                    compatibility,
                result = null,
                candleCount =
                    candles.size,
                datasetLabel =
                    "Deterministic synthetic research dataset",
                costs = costs
            )
        }

        val result =
            DeterministicBacktestEngine()
                .run(
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
                    costs = costs
                )

        return BacktestLabReport(
            compatibility =
                compatibility,
            result = result,
            candleCount =
                candles.size,
            datasetLabel =
                "Deterministic synthetic research dataset",
            costs = costs
        )
    }

    private fun syntheticCandles(
        symbol: String,
        timeframe: String,
        count: Int
    ): List<Candle> {
        var close = 2_350.0

        return (0 until count)
            .map { index ->
                val cycle =
                    index % 180

                val regime =
                    when {
                        cycle < 65 ->
                            0.62
                        cycle < 105 ->
                            -0.18
                        cycle < 155 ->
                            -0.58
                        else ->
                            0.24
                    }

                val wave =
                    sin(
                        index / 7.0
                    ) * 1.45 +
                        sin(
                            index / 23.0
                        ) * 0.82

                val impulse =
                    when {
                        index % 97 == 0 ->
                            3.4
                        index % 131 == 0 ->
                            -3.1
                        else ->
                            0.0
                    }

                val next =
                    close +
                        regime +
                        wave +
                        impulse

                val wick =
                    1.1 +
                        kotlin.math.abs(
                            sin(
                                index / 5.0
                            )
                        ) *
                        1.25

                val candle =
                    Candle(
                        symbol = symbol,
                        timeframe =
                            timeframe,
                        openTimeEpochMillis =
                            1_735_689_600_000L +
                                index *
                                900_000L,
                        open = close,
                        high =
                            maxOf(
                                close,
                                next
                            ) +
                                wick,
                        low =
                            minOf(
                                close,
                                next
                            ) -
                                wick,
                        close = next,
                        volume =
                            900.0 +
                                (index % 80) *
                                18.0
                    )

                close = next
                candle
            }
    }
}
