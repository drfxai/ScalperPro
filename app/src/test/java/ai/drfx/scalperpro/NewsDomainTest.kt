package ai.drfx.scalperpro

import ai.drfx.scalperpro.news.EventImpact
import ai.drfx.scalperpro.news.NewsAssessment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NewsDomainTest {
    @Test
    fun factAndAssessmentRemainSeparateFields() {
        val analysis = NewsAssessment(
            factualSummary = "CPI actual exceeded forecast.",
            aiAssessment = "USD may initially receive support.",
            potentialAffectedAssets = listOf("DXY", "EURUSD", "XAUUSD"),
            riskLevel = EventImpact.HIGH
        )

        assertNotEquals(analysis.factualSummary, analysis.aiAssessment)
        assertEquals(EventImpact.HIGH, analysis.riskLevel)
    }
}
