package ai.drfx.scalperpro.lab

enum class LabToolCategory {
    AI_DESIGN,
    INDICATOR,
    STRATEGY,
    PINE,
    BACKTEST,
    MQL5,
    QA,
    EDUCATION
}

data class LabTool(
    val id: String,
    val title: String,
    val description: String,
    val category: LabToolCategory,
    val beginnerFriendly: Boolean = true
)

data class StrategyArchetype(
    val id: String,
    val title: String,
    val summary: String,
    val usefulIndicators: List<String>,
    val caution: String
)

data class TimeframePreset(
    val code: String,
    val title: String,
    val useCase: String
)

data class PineQualityRule(
    val id: String,
    val title: String,
    val description: String,
    val severity: String
)

object QuantLabCatalog {
    val tools = listOf(
        LabTool(
            id = "ai-planner",
            title = "AI Planner",
            description = "Turns a beginner idea into a structured indicator or strategy plan.",
            category = LabToolCategory.AI_DESIGN
        ),
        LabTool(
            id = "indicator-forge",
            title = "Indicator Forge",
            description = "Design trend, momentum, volatility, volume, structure and hybrid indicators.",
            category = LabToolCategory.INDICATOR
        ),
        LabTool(
            id = "strategy-forge",
            title = "Strategy Forge",
            description = "Build entries, exits, filters, sessions, stops, targets and cooldown rules.",
            category = LabToolCategory.STRATEGY
        ),
        LabTool(
            id = "pine-studio",
            title = "Pine Studio",
            description = "Generate, explain, repair and review Pine Script v6.",
            category = LabToolCategory.PINE
        ),
        LabTool(
            id = "repaint-lab",
            title = "Repaint Lab",
            description = "Checks lookahead, higher-timeframe confirmation and bar-state risks.",
            category = LabToolCategory.QA
        ),
        LabTool(
            id = "quant-runtime",
            title = "Quant Runtime",
            description = "Runs supported Pine-style visual logic locally against candle data.",
            category = LabToolCategory.QA
        ),
        LabTool(
            id = "chart-sandbox",
            title = "Chart Sandbox",
            description = "Interactive TradingView Lightweight Charts preview for generated tools.",
            category = LabToolCategory.BACKTEST
        ),
        LabTool(
            id = "backtest-core",
            title = "Backtest Core",
            description = "Deterministic Strategy Specification simulation with realistic costs.",
            category = LabToolCategory.BACKTEST
        ),
        LabTool(
            id = "mql5-translator",
            title = "MQL5 Translator",
            description = "Converts approved strategy logic into MetaTrader 5-oriented MQL5.",
            category = LabToolCategory.MQL5
        ),
        LabTool(
            id = "beginner-coach",
            title = "Beginner Coach",
            description = "Explains every rule, parameter and warning in plain language.",
            category = LabToolCategory.EDUCATION
        )
    )

    val strategyArchetypes = listOf(
        StrategyArchetype(
            id = "trend",
            title = "Trend Following",
            summary = "Trade in the direction of a persistent trend.",
            usefulIndicators = listOf("EMA", "SMA", "Supertrend", "ADX", "Ichimoku"),
            caution = "Trend systems can whipsaw in sideways markets."
        ),
        StrategyArchetype(
            id = "breakout",
            title = "Breakout",
            summary = "Trade expansion beyond a defined range, level or volatility envelope.",
            usefulIndicators = listOf("Donchian", "ATR", "Volume", "Support/Resistance"),
            caution = "False breakouts require confirmation and invalidation rules."
        ),
        StrategyArchetype(
            id = "mean-reversion",
            title = "Mean Reversion",
            summary = "Trade stretched price back toward a statistical or dynamic mean.",
            usefulIndicators = listOf("RSI", "Bollinger Bands", "Z-Score", "VWAP"),
            caution = "Strong trends can keep price extended much longer than expected."
        ),
        StrategyArchetype(
            id = "momentum",
            title = "Momentum",
            summary = "Trade acceleration and directional strength.",
            usefulIndicators = listOf("RSI", "MACD", "ROC", "Stochastic", "ADX"),
            caution = "Late momentum entries can suffer from exhaustion."
        ),
        StrategyArchetype(
            id = "structure",
            title = "Market Structure / SMC",
            summary = "Use swing structure, BOS/CHoCH, liquidity and displacement concepts.",
            usefulIndicators = listOf("Swings", "BOS/CHoCH", "FVG", "Order Blocks", "ATR"),
            caution = "Definitions must be explicit to avoid discretionary hindsight."
        ),
        StrategyArchetype(
            id = "scalping",
            title = "Scalping",
            summary = "Short-horizon setups with strict execution and session filters.",
            usefulIndicators = listOf("EMA", "VWAP", "RSI", "ATR", "Volume"),
            caution = "Spread, slippage and overtrading matter more on short timeframes."
        ),
        StrategyArchetype(
            id = "multi-factor",
            title = "Multi-Factor Consensus",
            summary = "Combine independent systems and require a minimum agreement threshold.",
            usefulIndicators = listOf("Trend", "Momentum", "Volatility", "Structure"),
            caution = "Too many correlated filters can create false confidence and overfitting."
        )
    )

    val timeframes = listOf(
        TimeframePreset("1m", "1 Minute", "Fast scalping and microstructure"),
        TimeframePreset("3m", "3 Minutes", "Fast intraday confirmation"),
        TimeframePreset("5m", "5 Minutes", "Scalping and intraday"),
        TimeframePreset("15m", "15 Minutes", "Balanced intraday strategy development"),
        TimeframePreset("30m", "30 Minutes", "Intraday trend and structure"),
        TimeframePreset("1h", "1 Hour", "Swing/intraday context"),
        TimeframePreset("4h", "4 Hours", "Higher-timeframe trend and swing setups"),
        TimeframePreset("1D", "Daily", "Swing and macro structure"),
        TimeframePreset("1W", "Weekly", "Long-horizon context")
    )

    val pineQualityRules = listOf(
        PineQualityRule(
            id = "pine-v6",
            title = "Target Pine Script v6",
            description = "Generate current-style Pine v6 syntax unless compatibility requires otherwise.",
            severity = "INFO"
        ),
        PineQualityRule(
            id = "no-future-leak",
            title = "No future leakage",
            description = "Review request.security context carefully. For genuine HTF non-repainting values, TradingView documents a historical expression offset such as [1] together with barmerge.lookahead_on; unoffset HTF lookahead_on can leak future data.",
            severity = "ERROR"
        ),
        PineQualityRule(
            id = "confirmed-htf",
            title = "Confirmed higher-timeframe data",
            description = "HTF logic should deliberately avoid using unconfirmed future values.",
            severity = "WARNING"
        ),
        PineQualityRule(
            id = "bar-confirmation",
            title = "Bar-close confirmation",
            description = "Signal timing must state whether intrabar updates or confirmed bars are intended.",
            severity = "WARNING"
        ),
        PineQualityRule(
            id = "state-hygiene",
            title = "State hygiene",
            description = "Persistent var state must be reset on every relevant close/invalidation path.",
            severity = "WARNING"
        ),
        PineQualityRule(
            id = "alerts",
            title = "Alert contract",
            description = "Alerts should be structured, deterministic and aligned with signal timing.",
            severity = "INFO"
        ),
        PineQualityRule(
            id = "cost-awareness",
            title = "Runtime cost awareness",
            description = "Avoid uncontrolled nested loops and unnecessarily expensive per-bar work.",
            severity = "WARNING"
        ),
        PineQualityRule(
            id = "beginner-inputs",
            title = "Beginner-friendly inputs",
            description = "Use clear groups, tooltips, safe defaults and constrained parameter ranges.",
            severity = "INFO"
        )
    )
}
