package ai.drfx.scalperpro

import ai.drfx.scalperpro.risk.InstrumentRiskCalculator
import ai.drfx.scalperpro.risk.InstrumentRiskSpec
import ai.drfx.scalperpro.risk.RiskCalculationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstrumentRiskCalculatorTest {
    @Test
    fun goldLotSizingIsDeterministic() {
        val result = InstrumentRiskCalculator.calculate(
            RiskCalculationRequest(
                balance = 10_000.0,
                riskPercent = 1.0,
                entryPrice = 2000.0,
                stopPrice = 1990.0,
                targetPrice = 2020.0,
                instrument = InstrumentRiskSpec(
                    symbol = "XAUUSD",
                    pnlPerPriceUnitPerQuantity = 100.0,
                    quantityStep = 0.01,
                    minQuantity = 0.01,
                    maxQuantity = 100.0
                )
            )
        )

        assertEquals(100.0, result.maxLossAmount, 0.0001)
        assertEquals(0.1, result.quantity, 0.0001)
        assertEquals(2.0, result.rewardRisk ?: 0.0, 0.0001)
        assertTrue(result.withinInstrumentBounds)
    }
}
