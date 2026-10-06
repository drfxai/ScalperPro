package ai.drfx.scalperpro

import ai.drfx.scalperpro.ai.AgentRole
import ai.drfx.scalperpro.ai.LabTaskType
import ai.drfx.scalperpro.ai.ScalperAgentWorkflowPlanner
import ai.drfx.scalperpro.code.PineIndicatorGenerator
import ai.drfx.scalperpro.indicator.IndicatorSpecificationValidator
import ai.drfx.scalperpro.indicator.IndicatorTemplateCatalog
import ai.drfx.scalperpro.lab.QuantLabCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantLabPriorityTest {
    @Test
    fun indicatorWorkflowSeparatesPlanningEngineeringAndQa() {
        val plan = ScalperAgentWorkflowPlanner.plan(
            LabTaskType.INDICATOR_BUILD
        )

        assertTrue(
            plan.steps.any {
                it.role == AgentRole.REQUIREMENTS_ANALYST
            }
        )
        assertTrue(
            plan.steps.any {
                it.role == AgentRole.INDICATOR_ARCHITECT
            }
        )
        assertTrue(
            plan.steps.any {
                it.role == AgentRole.PINE_ENGINEER
            }
        )
        assertTrue(
            plan.steps.any {
                it.role == AgentRole.TRADINGVIEW_QA
            }
        )
        assertTrue(
            plan.steps.any {
                it.role == AgentRole.QUANT_RUNTIME_REVIEWER
            }
        )
    }

    @Test
    fun strategyWorkflowIncludesBacktestReview() {
        val plan = ScalperAgentWorkflowPlanner.plan(
            LabTaskType.STRATEGY_BUILD
        )

        assertTrue(
            plan.steps.any {
                it.role == AgentRole.STRATEGY_STRATEGIST
            }
        )
        assertTrue(
            plan.steps.any {
                it.role == AgentRole.BACKTEST_ANALYST
            }
        )
    }

    @Test
    fun starterIndicatorSpecificationIsValid() {
        val validation =
            IndicatorSpecificationValidator.validate(
                IndicatorTemplateCatalog.emaRsiTrend
            )

        assertTrue(validation.errors.joinToString(), validation.valid)
    }

    @Test
    fun indicatorGeneratorTargetsPineV6AndConfirmedAlerts() {
        val artifact = PineIndicatorGenerator.generate(
            IndicatorTemplateCatalog.emaRsiTrend
        )

        assertTrue(artifact.source.startsWith("//@version=6"))
        assertTrue(artifact.source.contains("indicator("))
        assertTrue(artifact.source.contains("input.int("))
        assertTrue(artifact.source.contains("ta.ema("))
        assertTrue(artifact.source.contains("ta.rsi("))
        assertTrue(artifact.source.contains("alertcondition("))
        assertTrue(artifact.source.contains("barstate.isconfirmed"))
        assertFalse(
            artifact.findings.any {
                it.code == "PINE_LOOKAHEAD_ON"
            }
        )
    }

    @Test
    fun catalogProvidesBeginnerToolingStrategiesAndTimeframes() {
        assertTrue(
            QuantLabCatalog.tools.any {
                it.id == "indicator-forge"
            }
        )
        assertTrue(
            QuantLabCatalog.strategyArchetypes.size >= 6
        )
        assertTrue(
            QuantLabCatalog.timeframes.any {
                it.code == "15m"
            }
        )
        assertEquals(
            QuantLabCatalog.timeframes
                .map { it.code }
                .distinct()
                .size,
            QuantLabCatalog.timeframes.size
        )
    }
}
