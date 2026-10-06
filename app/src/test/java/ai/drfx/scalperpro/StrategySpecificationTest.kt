package ai.drfx.scalperpro

import ai.drfx.scalperpro.strategy.DirectionPermission
import ai.drfx.scalperpro.strategy.MarketType
import ai.drfx.scalperpro.strategy.StopDefinition
import ai.drfx.scalperpro.strategy.StopMethod
import ai.drfx.scalperpro.strategy.StrategyCondition
import ai.drfx.scalperpro.strategy.StrategyRisk
import ai.drfx.scalperpro.strategy.StrategySpecification
import ai.drfx.scalperpro.strategy.StrategySpecificationValidator
import ai.drfx.scalperpro.strategy.TakeProfitDefinition
import ai.drfx.scalperpro.strategy.TakeProfitMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategySpecificationTest {
    @Test
    fun validSpecificationPassesValidation() {
        val spec = StrategySpecification(
            name = "Gold Trend",
            market = MarketType.METALS,
            symbol = "XAUUSD",
            primaryTimeframe = "15m",
            entryConditions = listOf(
                StrategyCondition(
                    description = "Trend confirmation",
                    expression = "ema50 > ema200"
                )
            ),
            session = null,
            risk = StrategyRisk(riskPercentPerTrade = 1.0),
            stop = StopDefinition(
                method = StopMethod.ATR_MULTIPLE,
                value = 1.5
            ),
            takeProfit = TakeProfitDefinition(
                method = TakeProfitMethod.FIXED_R_MULTIPLE,
                value = 2.0
            ),
            directionPermission = DirectionPermission.BOTH
        )

        assertTrue(StrategySpecificationValidator.validate(spec).valid)
    }

    @Test
    fun excessiveRiskIsRejected() {
        val spec = StrategySpecification(
            name = "Unsafe",
            market = MarketType.FOREX,
            symbol = "EURUSD",
            primaryTimeframe = "1h",
            entryConditions = listOf(
                StrategyCondition("Always", "true")
            ),
            session = null,
            risk = StrategyRisk(riskPercentPerTrade = 25.0),
            stop = StopDefinition(StopMethod.FIXED_PRICE_DISTANCE, 0.01),
            takeProfit = TakeProfitDefinition(TakeProfitMethod.FIXED_R_MULTIPLE, 2.0)
        )

        assertFalse(StrategySpecificationValidator.validate(spec).valid)
    }
}
