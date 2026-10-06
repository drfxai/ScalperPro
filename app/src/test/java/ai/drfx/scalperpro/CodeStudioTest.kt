package ai.drfx.scalperpro

import ai.drfx.scalperpro.code.Mql5StudioEngine
import ai.drfx.scalperpro.code.PineStaticAnalyzer
import ai.drfx.scalperpro.code.PineStudioEngine
import ai.drfx.scalperpro.code.VerificationStatus
import ai.drfx.scalperpro.strategy.MarketType
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
    fun mqlArtifactIsNotFalselyCompileVerified() {
        val artifact = Mql5StudioEngine.generate(spec())
        assertEquals(VerificationStatus.STATIC_ANALYZED, artifact.verificationStatus)
        assertTrue(artifact.source.contains("#property strict"))
    }

    @Test
    fun pineLookaheadOnIsFlagged() {
        val findings = PineStaticAnalyzer.analyze(
            "request.security(syminfo.tickerid, \"1D\", close, lookahead=barmerge.lookahead_on)"
        )
        assertTrue(findings.any { it.code == "PINE_LOOKAHEAD_ON" })
    }
}
