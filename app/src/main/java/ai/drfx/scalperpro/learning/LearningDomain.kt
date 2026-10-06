package ai.drfx.scalperpro.learning

enum class LearningCategory {
    FOREX_BASICS,
    MARKET_STRUCTURE,
    TECHNICAL_ANALYSIS,
    INDICATORS,
    PRICE_ACTION,
    SMC,
    ICT,
    RISK,
    PSYCHOLOGY,
    MACRO,
    NEWS_TRADING,
    GOLD,
    CRYPTO,
    PINE_SCRIPT,
    MQL5,
    QUANTITATIVE_TRADING
}

enum class ToolDeepLink {
    STRATEGY_LAB,
    RISK_MANAGER,
    PINE_STUDIO,
    MQL5_STUDIO,
    BACKTEST,
    NEWS,
    MARKETS,
    JOURNAL
}

data class LearningArticle(
    val id: String,
    val title: String,
    val category: LearningCategory,
    val summary: String,
    val toolDeepLink: ToolDeepLink?
)

object LearningCatalog {
    val articles = listOf(
        LearningArticle(
            id = "atr-risk",
            title = "ATR for Volatility-Aware Stops",
            category = LearningCategory.INDICATORS,
            summary = "Use ATR to normalize stop distance to current volatility.",
            toolDeepLink = ToolDeepLink.STRATEGY_LAB
        ),
        LearningArticle(
            id = "risk-reward",
            title = "Risk / Reward and Expectancy",
            category = LearningCategory.RISK,
            summary = "Connect position risk, R multiples and expectancy.",
            toolDeepLink = ToolDeepLink.RISK_MANAGER
        ),
        LearningArticle(
            id = "pine-repaint",
            title = "Repainting and Lookahead in Pine Script",
            category = LearningCategory.PINE_SCRIPT,
            summary = "Understand future leakage, bar confirmation and request.security.",
            toolDeepLink = ToolDeepLink.PINE_STUDIO
        ),
        LearningArticle(
            id = "mql5-order-safety",
            title = "MQL5 Order Safety",
            category = LearningCategory.MQL5,
            summary = "Review spread filters, duplicate-entry controls and risk checks.",
            toolDeepLink = ToolDeepLink.MQL5_STUDIO
        ),
        LearningArticle(
            id = "backtest-costs",
            title = "Why Spread, Slippage and Commission Matter",
            category = LearningCategory.QUANTITATIVE_TRADING,
            summary = "Avoid unrealistic perfect-execution backtests.",
            toolDeepLink = ToolDeepLink.BACKTEST
        )
    )
}
