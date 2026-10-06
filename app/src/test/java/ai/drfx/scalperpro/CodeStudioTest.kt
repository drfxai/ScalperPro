package ai.drfx.scalperpro

import ai.drfx.scalperpro.code.Mql5ExpressionTranslator
import ai.drfx.scalperpro.code.Mql5StudioEngine
import ai.drfx.scalperpro.code.PineStaticAnalyzer
import ai.drfx.scalperpro.code.PineStudioEngine
import ai.drfx.scalperpro.code.VerificationStatus
import ai.drfx.scalperpro.strategy.DirectionPermission
import ai.drfx.scalperpro.strategy.ExecutionAssumptions
import ai.drfx.scalperpro.strategy.MarketType
import ai.drfx.scalperpro.strategy.StrategySession
import ai.drfx.scalperpro.strategy.StopDefinition
import ai.drfx.scalperpro.strategy.StopMethod
import ai.drfx.scalperpro.strategy.StrategyCondition
import ai.drfx.scalperpro.strategy.StrategyRisk
import ai.drfx.scalperpro.strategy.StrategySpecification
import ai.drfx.scalperpro.strategy.TakeProfitDefinition
import ai.drfx.scalperpro.strategy.TakeProfitMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeStudioTest {
    private fun spec() = StrategySpecification(
        name = "Gold EMA RSI",
        market = MarketType.METALS,
        symbol = "XAUUSD",
        primaryTimeframe = "15m",
        entryConditions = listOf(
            StrategyCondition(
                description = "EMA trend",
                expression = "ta.ema(close, 50) > ta.ema(close, 200)"
            )
        ),
        session = null,
        risk = StrategyRisk(riskPercentPerTrade = 1.0),
        stop = StopDefinition(StopMethod.ATR_MULTIPLE, 1.5),
        takeProfit = TakeProfitDefinition(TakeProfitMethod.FIXED_R_MULTIPLE, 2.0)
    )

    @Test
    fun pineArtifactIsNotFalselyCompileVerified() {
        val artifact = PineStudioEngine.generate(spec())
        assertEquals(VerificationStatus.STATIC_ANALYZED, artifact.verificationStatus)
        assertTrue(artifact.source.contains("//@version=6"))
    }

    @Test
    fun generatedStrategyIncludesCostsSessionCooldownStopsAndTargets() {
        val strategy = StrategySpecification(
            name = "Gold Long Short",
            market = MarketType.METALS,
            symbol = "XAUUSD",
            primaryTimeframe = "15m",
            entryConditions = listOf(
                StrategyCondition(
                    description = "Long trend",
                    expression = "ta.ema(close, 20) > ta.ema(close, 50)"
                )
            ),
            shortEntryConditions = listOf(
                StrategyCondition(
                    description = "Short trend",
                    expression = "ta.ema(close, 20) < ta.ema(close, 50)"
                )
            ),
            session = StrategySession(
                timezone = "Europe/London",
                allowedWindows = listOf("0800-1700")
            ),
            risk = StrategyRisk(riskPercentPerTrade = 1.0),
            stop = StopDefinition(StopMethod.ATR_MULTIPLE, 1.5),
            takeProfit = TakeProfitDefinition(
                TakeProfitMethod.FIXED_R_MULTIPLE,
                2.0
            ),
            execution = ExecutionAssumptions(
                commissionPercent = 0.05,
                slippageTicks = 2
            ),
            cooldownBars = 3,
            directionPermission = DirectionPermission.BOTH
        )

        val artifact = PineStudioEngine.generate(strategy)

        assertTrue(artifact.source.contains("commission_value=0.05"))
        assertTrue(artifact.source.contains("slippage=2"))
        assertTrue(artifact.source.contains("Europe/London"))
        assertTrue(artifact.source.contains("cooldownBars"))
        assertTrue(artifact.source.contains("strategy.entry(\"L\""))
        assertTrue(artifact.source.contains("strategy.entry(\"S\""))
        assertTrue(artifact.source.contains("strategy.exit(\"L-X\""))
        assertTrue(artifact.source.contains("strategy.exit(\"S-X\""))
        assertTrue(artifact.source.contains("barstate.isconfirmed"))
        assertTrue(
            artifact.findings.none {
                it.code == "PINE_COMMISSION_REVIEW" ||
                    it.code == "PINE_SLIPPAGE_REVIEW"
            }
        )
    }

    @Test
    fun missingShortRulesNeverInventsShortEntries() {
        val artifact = PineStudioEngine.generate(spec())

        assertTrue(
            artifact.findings.any {
                it.code == "STRATEGY_SHORT_RULES_MISSING"
            }
        )
        assertTrue(artifact.source.contains("shortSignal = false"))
    }

    @Test
    fun mqlArtifactIsNotFalselyCompileVerified() {
        val artifact = Mql5StudioEngine.generate(spec())
        assertEquals(VerificationStatus.STATIC_ANALYZED, artifact.verificationStatus)
        assertTrue(artifact.source.contains("#property strict"))
    }

    @Test
    fun mqlTranslatorSupportsCommonConfirmedBarOperands() {
        val result = Mql5ExpressionTranslator.translate(
            "ta.ema(close, 50) > ta.ema(close, 200)"
        )

        assertTrue(result.supported)
        assertTrue(
            result.expression.orEmpty().contains(
                "ReadMA(50, 1, MODE_EMA)"
            )
        )
        assertTrue(
            result.expression.orEmpty().contains(
                "ReadMA(200, 1, MODE_EMA)"
            )
        )
    }

    @Test
    fun generatedMqlIncludesRiskSpreadPositionAndBufferGuards() {
        val artifact = Mql5StudioEngine.generate(spec())
        val source = artifact.source

        assertTrue(source.contains("#include <Trade/Trade.mqh>"))
        assertTrue(source.contains("CalculateRiskVolume"))
        assertTrue(source.contains("SpreadOk"))
        assertTrue(source.contains("PositionSelect(_Symbol)"))
        assertTrue(source.contains("CopyBuffer"))
        assertTrue(source.contains("IndicatorRelease"))
        assertTrue(source.contains("IsNewBar"))
        assertTrue(
            artifact.findings.none {
                it.code == "MQL5_RISK_CHECK_MISSING" ||
                    it.code == "MQL5_SPREAD_FILTER_REVIEW" ||
                    it.code == "MQL5_DUPLICATE_POSITION_REVIEW"
            }
        )
    }

    @Test
    fun pineLookaheadOnIsFlagged() {
        val findings = PineStaticAnalyzer.analyze(
            "request.security(syminfo.tickerid, \"1D\", close, lookahead=barmerge.lookahead_on)"
        )
        assertTrue(findings.any { it.code == "PINE_LOOKAHEAD_CONTEXT_REVIEW" })
    }
}
