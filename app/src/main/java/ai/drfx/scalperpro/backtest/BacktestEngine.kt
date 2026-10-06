package ai.drfx.scalperpro.backtest

import ai.drfx.scalperpro.market.Candle
import kotlin.math.abs
import kotlin.math.sqrt

enum class PositionSide {
    LONG,
    SHORT
}

enum class ExitReason {
    STOP,
    TARGET,
    STRATEGY_EXIT,
    END_OF_DATA
}

data class BacktestCosts(
    val spreadPriceUnits: Double = 0.0,
    val slippagePriceUnits: Double = 0.0,
    val commissionPerUnitPerSide: Double = 0.0
) {
    init {
        require(spreadPriceUnits >= 0.0)
        require(slippagePriceUnits >= 0.0)
        require(commissionPerUnitPerSide >= 0.0)
    }
}

data class EntryDecision(
    val side: PositionSide,
    val stopDistance: Double,
    val targetDistance: Double
) {
    init {
        require(stopDistance > 0.0)
        require(targetDistance > 0.0)
    }
}

data class OpenPosition(
    val side: PositionSide,
    val entryPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val units: Double,
    val initialRiskAmount: Double,
    val entryIndex: Int,
    val entryTimeEpochMillis: Long
)

interface BacktestStrategy {
    fun reset() = Unit

    fun evaluateEntry(history: List<Candle>): EntryDecision?

    fun shouldExit(
        history: List<Candle>,
        position: OpenPosition
    ): Boolean = false

    fun onEntryOpened(
        position: OpenPosition
    ) = Unit

    fun onPositionClosed(
        trade: BacktestTrade
    ) = Unit
}

data class BacktestTrade(
    val side: PositionSide,
    val entryPrice: Double,
    val exitPrice: Double,
    val stopPrice: Double,
    val targetPrice: Double,
    val units: Double,
    val grossPnl: Double,
    val commission: Double,
    val netPnl: Double,
    val rMultiple: Double,
    val entryIndex: Int,
    val exitIndex: Int,
    val entryTimeEpochMillis: Long,
    val exitTimeEpochMillis: Long,
    val exitReason: ExitReason
) {
    val durationBars: Int
        get() = (exitIndex - entryIndex).coerceAtLeast(0)
}

data class BacktestMetrics(
    val netPnl: Double,
    val netReturnPercent: Double,
    val winRatePercent: Double,
    val lossRatePercent: Double,
    val profitFactor: Double?,
    val maxDrawdownAmount: Double,
    val maxDrawdownPercent: Double,
    val expectancyR: Double,
    val averageR: Double,
    val averageWinnerR: Double?,
    val averageLoserR: Double?,
    val sharpeLike: Double?,
    val sortinoLike: Double?,
    val recoveryFactor: Double?,
    val consecutiveWins: Int,
    val consecutiveLosses: Int,
    val tradeCount: Int,
    val exposurePercent: Double,
    val averageTradeDurationBars: Double
)

data class BacktestResult(
    val initialBalance: Double,
    val endingBalance: Double,
    val trades: List<BacktestTrade>,
    val metrics: BacktestMetrics
)

class DeterministicBacktestEngine {
    fun run(
        candles: List<Candle>,
        strategy: BacktestStrategy,
        initialBalance: Double,
        riskPercentPerTrade: Double,
        costs: BacktestCosts = BacktestCosts()
    ): BacktestResult {
        require(candles.size >= 2)
        require(initialBalance > 0.0)
        require(riskPercentPerTrade > 0.0)
        require(riskPercentPerTrade <= 10.0)

        strategy.reset()

        var balance = initialBalance
        var position: OpenPosition? = null
        val trades = mutableListOf<BacktestTrade>()

        for (index in 1 until candles.size) {
            val candle = candles[index]

            if (position != null) {
                val exit = evaluateIntrabarExit(
                    candle = candle,
                    position = position,
                    costs = costs
                )

                if (exit != null) {
                    val trade = closeTrade(
                        position = position,
                        exitPrice = exit.first,
                        exitReason = exit.second,
                        exitIndex = index,
                        exitTimeEpochMillis = candle.openTimeEpochMillis,
                        costs = costs
                    )
                    trades += trade
                    balance += trade.netPnl
                    strategy.onPositionClosed(trade)
                    position = null
                } else {
                    val history = candles.subList(0, index + 1)
                    if (strategy.shouldExit(history, position)) {
                        val exitPrice = applyExitCosts(
                            rawPrice = candle.close,
                            side = position.side,
                            costs = costs
                        )
                        val trade = closeTrade(
                            position = position,
                            exitPrice = exitPrice,
                            exitReason = ExitReason.STRATEGY_EXIT,
                            exitIndex = index,
                            exitTimeEpochMillis = candle.openTimeEpochMillis,
                            costs = costs
                        )
                        trades += trade
                        balance += trade.netPnl
                        strategy.onPositionClosed(trade)
                        position = null
                    }
                }
            }

            if (position == null) {
                val historyBeforeEntry = candles.subList(0, index)
                val decision = strategy.evaluateEntry(historyBeforeEntry)

                if (decision != null) {
                    val entryPrice = applyEntryCosts(
                        rawPrice = candle.open,
                        side = decision.side,
                        costs = costs
                    )
                    val riskAmount = balance * riskPercentPerTrade / 100.0
                    val stopPrice = when (decision.side) {
                        PositionSide.LONG -> entryPrice - decision.stopDistance
                        PositionSide.SHORT -> entryPrice + decision.stopDistance
                    }
                    val targetPrice = when (decision.side) {
                        PositionSide.LONG -> entryPrice + decision.targetDistance
                        PositionSide.SHORT -> entryPrice - decision.targetDistance
                    }
                    val units = riskAmount / abs(entryPrice - stopPrice)

                    position = OpenPosition(
                        side = decision.side,
                        entryPrice = entryPrice,
                        stopPrice = stopPrice,
                        targetPrice = targetPrice,
                        units = units,
                        initialRiskAmount = riskAmount,
                        entryIndex = index,
                        entryTimeEpochMillis = candle.openTimeEpochMillis
                    )
                    strategy.onEntryOpened(position)
                }
            }
        }

        if (position != null) {
            val lastIndex = candles.lastIndex
            val candle = candles[lastIndex]
            val exitPrice = applyExitCosts(
                rawPrice = candle.close,
                side = position.side,
                costs = costs
            )
            val trade = closeTrade(
                position = position,
                exitPrice = exitPrice,
                exitReason = ExitReason.END_OF_DATA,
                exitIndex = lastIndex,
                exitTimeEpochMillis = candle.openTimeEpochMillis,
                costs = costs
            )
            trades += trade
            balance += trade.netPnl
            strategy.onPositionClosed(trade)
        }

        return BacktestResult(
            initialBalance = initialBalance,
            endingBalance = balance,
            trades = trades.toList(),
            metrics = metrics(
                trades = trades,
                initialBalance = initialBalance,
                endingBalance = balance,
                candleCount = candles.size
            )
        )
    }

    private fun evaluateIntrabarExit(
        candle: Candle,
        position: OpenPosition,
        costs: BacktestCosts
    ): Pair<Double, ExitReason>? {
        return when (position.side) {
            PositionSide.LONG -> {
                val stopTouched = candle.low <= position.stopPrice
                val targetTouched = candle.high >= position.targetPrice

                when {
                    stopTouched -> applyExitCosts(position.stopPrice, position.side, costs) to ExitReason.STOP
                    targetTouched -> applyExitCosts(position.targetPrice, position.side, costs) to ExitReason.TARGET
                    else -> null
                }
            }

            PositionSide.SHORT -> {
                val stopTouched = candle.high >= position.stopPrice
                val targetTouched = candle.low <= position.targetPrice

                when {
                    stopTouched -> applyExitCosts(position.stopPrice, position.side, costs) to ExitReason.STOP
                    targetTouched -> applyExitCosts(position.targetPrice, position.side, costs) to ExitReason.TARGET
                    else -> null
                }
            }
        }
    }

    private fun applyEntryCosts(
        rawPrice: Double,
        side: PositionSide,
        costs: BacktestCosts
    ): Double {
        val halfSpread = costs.spreadPriceUnits / 2.0
        return when (side) {
            PositionSide.LONG -> rawPrice + halfSpread + costs.slippagePriceUnits
            PositionSide.SHORT -> rawPrice - halfSpread - costs.slippagePriceUnits
        }
    }

    private fun applyExitCosts(
        rawPrice: Double,
        side: PositionSide,
        costs: BacktestCosts
    ): Double {
        val halfSpread = costs.spreadPriceUnits / 2.0
        return when (side) {
            PositionSide.LONG -> rawPrice - halfSpread - costs.slippagePriceUnits
            PositionSide.SHORT -> rawPrice + halfSpread + costs.slippagePriceUnits
        }
    }

    private fun closeTrade(
        position: OpenPosition,
        exitPrice: Double,
        exitReason: ExitReason,
        exitIndex: Int,
        exitTimeEpochMillis: Long,
        costs: BacktestCosts
    ): BacktestTrade {
        val priceMove = when (position.side) {
            PositionSide.LONG -> exitPrice - position.entryPrice
            PositionSide.SHORT -> position.entryPrice - exitPrice
        }
        val grossPnl = priceMove * position.units
        val commission = costs.commissionPerUnitPerSide * position.units * 2.0
        val netPnl = grossPnl - commission
        val r = if (position.initialRiskAmount > 0.0) {
            netPnl / position.initialRiskAmount
        } else {
            0.0
        }

        return BacktestTrade(
            side = position.side,
            entryPrice = position.entryPrice,
            exitPrice = exitPrice,
            stopPrice = position.stopPrice,
            targetPrice = position.targetPrice,
            units = position.units,
            grossPnl = grossPnl,
            commission = commission,
            netPnl = netPnl,
            rMultiple = r,
            entryIndex = position.entryIndex,
            exitIndex = exitIndex,
            entryTimeEpochMillis = position.entryTimeEpochMillis,
            exitTimeEpochMillis = exitTimeEpochMillis,
            exitReason = exitReason
        )
    }

    private fun metrics(
        trades: List<BacktestTrade>,
        initialBalance: Double,
        endingBalance: Double,
        candleCount: Int
    ): BacktestMetrics {
        if (trades.isEmpty()) {
            return BacktestMetrics(
                netPnl = 0.0,
                netReturnPercent = 0.0,
                winRatePercent = 0.0,
                lossRatePercent = 0.0,
                profitFactor = null,
                maxDrawdownAmount = 0.0,
                maxDrawdownPercent = 0.0,
                expectancyR = 0.0,
                averageR = 0.0,
                averageWinnerR = null,
                averageLoserR = null,
                sharpeLike = null,
                sortinoLike = null,
                recoveryFactor = null,
                consecutiveWins = 0,
                consecutiveLosses = 0,
                tradeCount = 0,
                exposurePercent = 0.0,
                averageTradeDurationBars = 0.0
            )
        }

        val winners = trades.filter { it.netPnl > 0.0 }
        val losers = trades.filter { it.netPnl < 0.0 }
        val grossProfit = winners.sumOf { it.netPnl }
        val grossLoss = abs(losers.sumOf { it.netPnl })
        val netPnl = endingBalance - initialBalance

        var equity = initialBalance
        var peak = initialBalance
        var maxDrawdownAmount = 0.0
        var maxDrawdownPercent = 0.0

        trades.forEach { trade ->
            equity += trade.netPnl
            if (equity > peak) peak = equity
            val drawdown = peak - equity
            val drawdownPercent = if (peak > 0.0) drawdown / peak * 100.0 else 0.0
            if (drawdown > maxDrawdownAmount) maxDrawdownAmount = drawdown
            if (drawdownPercent > maxDrawdownPercent) maxDrawdownPercent = drawdownPercent
        }

        var currentWins = 0
        var currentLosses = 0
        var maxWins = 0
        var maxLosses = 0

        trades.forEach { trade ->
            when {
                trade.netPnl > 0.0 -> {
                    currentWins += 1
                    currentLosses = 0
                    if (currentWins > maxWins) maxWins = currentWins
                }
                trade.netPnl < 0.0 -> {
                    currentLosses += 1
                    currentWins = 0
                    if (currentLosses > maxLosses) maxLosses = currentLosses
                }
                else -> {
                    currentWins = 0
                    currentLosses = 0
                }
            }
        }

        val rs = trades.map { it.rMultiple }
        val averageR = rs.average()
        val winRate = winners.size.toDouble() / trades.size * 100.0
        val lossRate = losers.size.toDouble() / trades.size * 100.0
        val expectancyR = averageR
        val exposureBars = trades.sumOf { it.durationBars }.toDouble()
        val exposurePercent = (exposureBars / candleCount.toDouble() * 100.0).coerceIn(0.0, 100.0)

        return BacktestMetrics(
            netPnl = netPnl,
            netReturnPercent = netPnl / initialBalance * 100.0,
            winRatePercent = winRate,
            lossRatePercent = lossRate,
            profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else null,
            maxDrawdownAmount = maxDrawdownAmount,
            maxDrawdownPercent = maxDrawdownPercent,
            expectancyR = expectancyR,
            averageR = averageR,
            averageWinnerR = winners.map { it.rMultiple }.takeIf { it.isNotEmpty() }?.average(),
            averageLoserR = losers.map { it.rMultiple }.takeIf { it.isNotEmpty() }?.average(),
            sharpeLike = annualizationFreeSharpe(rs),
            sortinoLike = annualizationFreeSortino(rs),
            recoveryFactor = if (maxDrawdownAmount > 0.0) netPnl / maxDrawdownAmount else null,
            consecutiveWins = maxWins,
            consecutiveLosses = maxLosses,
            tradeCount = trades.size,
            exposurePercent = exposurePercent,
            averageTradeDurationBars = trades.map { it.durationBars.toDouble() }.average()
        )
    }

    private fun annualizationFreeSharpe(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        val variance = values.sumOf { value ->
            val delta = value - mean
            delta * delta
        } / (values.size - 1).toDouble()
        val deviation = sqrt(variance)
        if (deviation == 0.0) return null
        return mean / deviation * sqrt(values.size.toDouble())
    }

    private fun annualizationFreeSortino(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        val negatives = values.filter { it < 0.0 }
        if (negatives.isEmpty()) return null
        val downsideVariance = negatives.sumOf { it * it } / negatives.size.toDouble()
        val downsideDeviation = sqrt(downsideVariance)
        if (downsideDeviation == 0.0) return null
        return mean / downsideDeviation * sqrt(values.size.toDouble())
    }
}
