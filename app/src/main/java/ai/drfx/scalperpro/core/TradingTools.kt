package ai.drfx.scalperpro.core

import kotlin.math.abs

data class RiskResult(
    val maxLoss: Double,
    val riskDistance: Double,
    val genericUnits: Double,
    val rewardRisk: Double?
)

object RiskEngine {
    fun calculate(balance: Double, riskPercent: Double, entry: Double, stop: Double, target: Double? = null): RiskResult {
        require(balance > 0.0)
        require(riskPercent > 0.0)
        val distance = abs(entry - stop)
        require(distance > 0.0)
        val maxLoss = balance * (riskPercent / 100.0)
        val units = maxLoss / distance
        val rr = target?.let { abs(it - entry) / distance }
        return RiskResult(maxLoss, distance, units, rr)
    }
}

object StrategyCodeGenerator {
    fun pine(spec: StrategySpec): String = buildString {
        appendLine("//@version=6")
        appendLine("strategy(\"" + spec.name + "\", overlay=true)")
        appendLine("// Generated from Scalper Pro Strategy Specification")
        appendLine("// Symbol: " + spec.symbol + "  TF: " + spec.timeframe)
        appendLine("// Trend: " + spec.trendRule)
        appendLine("// Entry: " + spec.entryRule)
        appendLine("// Stop: " + spec.stopRule)
        appendLine("// Target: " + spec.targetRule)
        appendLine("// Session: " + spec.session)
        appendLine("// Risk: " + spec.riskPercent + "%")
        appendLine("fast = ta.ema(close, 50)")
        appendLine("slow = ta.ema(close, 200)")
        appendLine("longCondition = fast > slow and ta.crossover(ta.rsi(close, 14), 50)")
        appendLine("if longCondition")
        appendLine("    strategy.entry(\"L\", strategy.long)")
        appendLine("plot(fast)")
        appendLine("plot(slow)")
    }

    fun mql5(spec: StrategySpec): String = buildString {
        appendLine("// Scalper Pro generated MQL5 skeleton")
        appendLine("#property strict")
        appendLine("input double RiskPercent = " + spec.riskPercent + ";")
        appendLine("int OnInit(){ return(INIT_SUCCEEDED); }")
        appendLine("void OnTick(){")
        appendLine("  // " + spec.symbol + " " + spec.timeframe)
        appendLine("  // Trend: " + spec.trendRule)
        appendLine("  // Entry: " + spec.entryRule)
        appendLine("  // Stop: " + spec.stopRule)
        appendLine("  // Target: " + spec.targetRule)
        appendLine("}")
    }
}
