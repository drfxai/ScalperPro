package ai.drfx.scalperpro.ai

enum class AgentRole {
    SUPERVISOR,
    REQUIREMENTS_ANALYST,
    INDICATOR_ARCHITECT,
    STRATEGY_STRATEGIST,
    PINE_ENGINEER,
    TRADINGVIEW_QA,
    QUANT_RUNTIME_REVIEWER,
    BACKTEST_ANALYST,
    MQL5_TRANSLATOR,
    BEGINNER_COACH
}

enum class LabTaskType {
    INDICATOR_BUILD,
    STRATEGY_BUILD,
    PINE_REVIEW,
    PINE_REPAIR,
    MQL5_TRANSLATE,
    EXPLAIN
}

data class AgentDefinition(
    val role: AgentRole,
    val title: String,
    val mission: String
)

data class AgentStep(
    val index: Int,
    val role: AgentRole,
    val objective: String,
    val required: Boolean = true
)

data class AgentWorkflowPlan(
    val taskType: LabTaskType,
    val steps: List<AgentStep>
)

object ScalperAgentTeam {
    val agents = listOf(
        AgentDefinition(
            role = AgentRole.SUPERVISOR,
            title = "Scalper AI Supervisor",
            mission = "Routes the task, preserves context and prevents specialists from contradicting the approved design."
        ),
        AgentDefinition(
            role = AgentRole.REQUIREMENTS_ANALYST,
            title = "Requirements Analyst",
            mission = "Converts beginner language into explicit market, timeframe, signal, visualization and risk requirements."
        ),
        AgentDefinition(
            role = AgentRole.INDICATOR_ARCHITECT,
            title = "Indicator Architect",
            mission = "Designs indicator inputs, calculations, plots, alerts, repaint policy and usability."
        ),
        AgentDefinition(
            role = AgentRole.STRATEGY_STRATEGIST,
            title = "Strategy Strategist",
            mission = "Designs entries, exits, filters, sessions, stops, targets, cooldown and long/short permissions."
        ),
        AgentDefinition(
            role = AgentRole.PINE_ENGINEER,
            title = "Pine Engineer",
            mission = "Produces readable Pine Script v6 from the approved specification."
        ),
        AgentDefinition(
            role = AgentRole.TRADINGVIEW_QA,
            title = "TradingView QA",
            mission = "Reviews Pine code for lookahead, repainting, request.security, bar confirmation, state hygiene and alert correctness."
        ),
        AgentDefinition(
            role = AgentRole.QUANT_RUNTIME_REVIEWER,
            title = "Quant Runtime Reviewer",
            mission = "Runs the local DrFXQuant-derived compatibility/runtime test and explains unsupported constructs."
        ),
        AgentDefinition(
            role = AgentRole.BACKTEST_ANALYST,
            title = "Backtest Analyst",
            mission = "Reviews deterministic backtest assumptions, costs and result quality without inventing performance."
        ),
        AgentDefinition(
            role = AgentRole.MQL5_TRANSLATOR,
            title = "MQL5 Translator",
            mission = "Maps approved strategy logic to MetaTrader 5 concepts while preserving risk and execution semantics."
        ),
        AgentDefinition(
            role = AgentRole.BEGINNER_COACH,
            title = "Beginner Coach",
            mission = "Explains what was built, how to use it and what each parameter changes in plain language."
        )
    )
}

object ScalperAgentWorkflowPlanner {
    fun plan(taskType: LabTaskType): AgentWorkflowPlan {
        val roles = when (taskType) {
            LabTaskType.INDICATOR_BUILD -> listOf(
                AgentRole.REQUIREMENTS_ANALYST to "Clarify indicator purpose, inputs, timeframe and visual outputs.",
                AgentRole.INDICATOR_ARCHITECT to "Create the indicator design specification.",
                AgentRole.PINE_ENGINEER to "Generate Pine v6 from the approved design.",
                AgentRole.TRADINGVIEW_QA to "Audit TradingView/Pine correctness and repaint risk.",
                AgentRole.QUANT_RUNTIME_REVIEWER to "Run local compatibility and output-activity checks.",
                AgentRole.BEGINNER_COACH to "Explain usage, inputs, limitations and next steps."
            )
            LabTaskType.STRATEGY_BUILD -> listOf(
                AgentRole.REQUIREMENTS_ANALYST to "Clarify market, timeframe, trade direction and user intent.",
                AgentRole.STRATEGY_STRATEGIST to "Create the structured Strategy Specification.",
                AgentRole.PINE_ENGINEER to "Generate Pine v6 strategy code.",
                AgentRole.TRADINGVIEW_QA to "Audit repaint/lookahead, execution timing and state management.",
                AgentRole.BACKTEST_ANALYST to "Review deterministic test assumptions and costs.",
                AgentRole.QUANT_RUNTIME_REVIEWER to "Check chart-facing Pine compatibility and diagnostics.",
                AgentRole.BEGINNER_COACH to "Explain the strategy in plain language."
            )
            LabTaskType.PINE_REVIEW -> listOf(
                AgentRole.REQUIREMENTS_ANALYST to "Identify the script intent and expected behavior.",
                AgentRole.TRADINGVIEW_QA to "Audit Pine correctness and risky constructs.",
                AgentRole.QUANT_RUNTIME_REVIEWER to "Run local compatibility/output checks.",
                AgentRole.BEGINNER_COACH to "Explain findings and fixes."
            )
            LabTaskType.PINE_REPAIR -> listOf(
                AgentRole.TRADINGVIEW_QA to "Identify concrete defects before editing.",
                AgentRole.PINE_ENGINEER to "Repair only the confirmed defects.",
                AgentRole.TRADINGVIEW_QA to "Re-review the repaired source.",
                AgentRole.QUANT_RUNTIME_REVIEWER to "Re-run local compatibility/output checks.",
                AgentRole.BEGINNER_COACH to "Summarize what changed and why."
            )
            LabTaskType.MQL5_TRANSLATE -> listOf(
                AgentRole.REQUIREMENTS_ANALYST to "Identify strategy semantics that must be preserved.",
                AgentRole.STRATEGY_STRATEGIST to "Normalize the source into a structured strategy model.",
                AgentRole.MQL5_TRANSLATOR to "Generate MQL5-oriented implementation.",
                AgentRole.BEGINNER_COACH to "Explain MetaTrader setup and compile-verification limits."
            )
            LabTaskType.EXPLAIN -> listOf(
                AgentRole.BEGINNER_COACH to "Teach the requested concept with examples and safe assumptions."
            )
        }

        return AgentWorkflowPlan(
            taskType = taskType,
            steps = roles.mapIndexed { index, pair ->
                AgentStep(
                    index = index + 1,
                    role = pair.first,
                    objective = pair.second
                )
            }
        )
    }
}
