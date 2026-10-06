package ai.drfx.scalperpro

import ai.drfx.scalperpro.core.RiskEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class RiskEngineTest {
    @Test
    fun riskMathIsDeterministic() {
        val r = RiskEngine.calculate(
            balance = 10_000.0,
            riskPercent = 1.0,
            entry = 2000.0,
            stop = 1990.0,
            target = 2030.0
        )
        assertEquals(100.0, r.maxLoss, 0.0001)
        assertEquals(10.0, r.genericUnits, 0.0001)
        assertEquals(3.0, r.rewardRisk ?: 0.0, 0.0001)
    }
}
