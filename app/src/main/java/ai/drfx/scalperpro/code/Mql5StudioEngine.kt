package ai.drfx.scalperpro.code

import ai.drfx.scalperpro.strategy.StrategySpecification

object Mql5StudioEngine {
    fun generate(
        specification: StrategySpecification
    ): CodeArtifact {
        val source = buildString {
            appendLine("// Scalper Pro MQL5 generator")
            appendLine("// Generated from Strategy Specification.")
            appendLine("// Verification: static analysis only; MetaEditor compile not performed.")
            appendLine("#property strict")
            appendLine()
            appendLine("input double RiskPercent = " + specification.risk.riskPercentPerTrade + ";")
            appendLine("input int CooldownBars = " + specification.cooldownBars + ";")
            appendLine()
            appendLine("int OnInit()")
            appendLine("{")
            appendLine("   return(INIT_SUCCEEDED);")
            appendLine("}")
            appendLine()
            appendLine("void OnTick()")
            appendLine("{")
            appendLine("   // Symbol: " + specification.symbol)
            appendLine("   // Primary timeframe: " + specification.primaryTimeframe)
            specification.entryConditions.forEachIndexed { index, condition ->
                appendLine("   // Entry condition " + (index + 1) + ": " + condition.description)
                appendLine("   // Expression: " + condition.expression)
            }
            appendLine("   // TODO: map validated Strategy Specification expressions to native MQL5 indicator/buffer calls.")
            appendLine("   // Never send an order until spread, duplicate-entry, session and risk checks pass.")
            appendLine("}")
        }

        return CodeArtifact(
            language = CodeLanguage.MQL5,
            source = source,
            verificationStatus = VerificationStatus.STATIC_ANALYZED,
            findings = Mql5StaticAnalyzer.analyze(source)
        )
    }
}

object Mql5StaticAnalyzer {
    fun analyze(source: String): List<CodeFinding> {
        val findings = mutableListOf<CodeFinding>()
        val lowered = source.lowercase()

        if ("ordersend" in lowered && "riskpercent" !in lowered) {
            findings += CodeFinding(
                severity = "ERROR",
                code = "MQL5_RISK_CHECK_MISSING",
                message = "OrderSend-like logic detected without an obvious risk input."
            )
        }

        if ("ordersend" in lowered && "spread" !in lowered) {
            findings += CodeFinding(
                severity = "WARNING",
                code = "MQL5_SPREAD_FILTER_REVIEW",
                message = "Trading logic should include an explicit spread filter."
            )
        }

        if ("ontick" !in lowered) {
            findings += CodeFinding(
                severity = "INFO",
                code = "MQL5_NO_ONTICK",
                message = "No OnTick handler detected."
            )
        }

        return findings
    }
}
