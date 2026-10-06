package ai.drfx.scalperpro.core

data class MarketQuote(
    val symbol: String,
    val price: String,
    val changePercent: Double,
    val status: String = "DEMO"
)

data class Signal(
    val symbol: String,
    val side: String,
    val entry: String,
    val stop: String,
    val target: String,
    val rr: String,
    val status: String
)

data class EconomicEvent(
    val time: String,
    val currency: String,
    val impact: String,
    val title: String,
    val actual: String? = null,
    val forecast: String? = null,
    val previous: String? = null
)

data class StrategySpec(
    val name: String,
    val symbol: String,
    val timeframe: String,
    val trendRule: String,
    val entryRule: String,
    val stopRule: String,
    val targetRule: String,
    val session: String,
    val riskPercent: Double
)
