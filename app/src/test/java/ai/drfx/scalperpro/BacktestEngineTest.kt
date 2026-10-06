package ai.drfx.scalperpro

import ai.drfx.scalperpro.backtest.BacktestCosts
import ai.drfx.scalperpro.backtest.BacktestStrategy
import ai.drfx.scalperpro.backtest.DeterministicBacktestEngine
import ai.drfx.scalperpro.backtest.EntryDecision
import ai.drfx.scalperpro.backtest.ExitReason
import ai.drfx.scalperpro.backtest.PositionSide
import ai.drfx.scalperpro.market.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BacktestEngineTest {
    private fun candle(
        index: Int,
        open: Double,
        high: Double,
        low: Double,
        close: Double
    ): Candle = Candle(
        symbol = "XAUUSD",
        timeframe = "15m",
        openTimeEpochMillis = index.toLong() * 60_000L,
        open = open,
        high = high,
        low = low,
        close = close
    )

    @Test
    fun sameInputProducesSameResult() {
        val candles = listOf(
            candle(0, 100.0, 101.0, 99.0, 100.0),
            candle(1, 100.0, 101.0, 99.5, 100.5),
            candle(2, 100.5, 103.0, 100.0, 102.5),
            candle(3, 102.5, 103.0, 101.0, 101.5)
        )

        val strategy = object : BacktestStrategy {
            override fun evaluateEntry(history: List<Candle>): EntryDecision? {
                return if (history.size == 1) {
                    EntryDecision(
                        side = PositionSide.LONG,
                        stopDistance = 1.0,
                        targetDistance = 2.0
                    )
                } else {
                    null
                }
            }
        }

        val engine = DeterministicBacktestEngine()
        val first = engine.run(candles, strategy, 10_000.0, 1.0)
        val second = engine.run(candles, strategy, 10_000.0, 1.0)

        assertEquals(first, second)
        assertEquals(1, first.trades.size)
        assertEquals(ExitReason.TARGET, first.trades.first().exitReason)
        assertTrue(first.endingBalance > first.initialBalance)
    }

    @Test
    fun stopWinsWhenStopAndTargetTouchSameBar() {
        val candles = listOf(
            candle(0, 100.0, 101.0, 99.0, 100.0),
            candle(1, 100.0, 101.0, 99.5, 100.0),
            candle(2, 100.0, 103.0, 98.0, 100.0)
        )

        val strategy = object : BacktestStrategy {
            override fun evaluateEntry(history: List<Candle>): EntryDecision? {
                return if (history.size == 1) {
                    EntryDecision(PositionSide.LONG, 1.0, 2.0)
                } else {
                    null
                }
            }
        }

        val result = DeterministicBacktestEngine().run(
            candles = candles,
            strategy = strategy,
            initialBalance = 10_000.0,
            riskPercentPerTrade = 1.0,
            costs = BacktestCosts()
        )

        assertEquals(ExitReason.STOP, result.trades.first().exitReason)
    }
}
