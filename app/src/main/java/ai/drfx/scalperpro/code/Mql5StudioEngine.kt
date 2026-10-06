package ai.drfx.scalperpro.code

import ai.drfx.scalperpro.strategy.DirectionPermission
import ai.drfx.scalperpro.strategy.StopMethod
import ai.drfx.scalperpro.strategy.StrategyCondition
import ai.drfx.scalperpro.strategy.StrategySpecification
import ai.drfx.scalperpro.strategy.TakeProfitMethod

object Mql5StudioEngine {
    fun generate(
        specification: StrategySpecification
    ): CodeArtifact {
        val findings =
            mutableListOf<CodeFinding>()

        val longAllowed =
            specification.directionPermission !=
                DirectionPermission.SHORT_ONLY
        val shortAllowed =
            specification.directionPermission !=
                DirectionPermission.LONG_ONLY

        val longSignal =
            if (longAllowed) {
                translateConditions(
                    side = "LONG",
                    conditions =
                        specification.entryConditions +
                            specification.filters,
                    findings = findings
                )
            } else {
                "false"
            }

        val shortSignal =
            if (
                shortAllowed &&
                specification.shortEntryConditions
                    .isNotEmpty()
            ) {
                translateConditions(
                    side = "SHORT",
                    conditions =
                        specification.shortEntryConditions +
                            specification.filters,
                    findings = findings
                )
            } else {
                if (shortAllowed) {
                    findings += CodeFinding(
                        severity = "WARNING",
                        code =
                            "MQL5_SHORT_RULES_MISSING",
                        message =
                            "Short trading is allowed but no explicit short-entry conditions exist, so generated MQL5 will not open short positions."
                    )
                }
                "false"
            }

        val exitSignal =
            if (
                specification.exitConditions
                    .isNotEmpty()
            ) {
                translateConditions(
                    side = "EXIT",
                    conditions =
                        specification.exitConditions,
                    findings = findings,
                    joinWith = " || "
                )
            } else {
                "false"
            }

        if (specification.session != null) {
            findings += CodeFinding(
                severity = "INFO",
                code =
                    "MQL5_SESSION_TIMEZONE_REVIEW",
                message =
                    "Strategy session windows require an explicit broker/server-time mapping before MetaTrader execution. Scalper Pro will not guess that offset."
            )
        }

        if (specification.newsFilter.enabled) {
            findings += CodeFinding(
                severity = "INFO",
                code =
                    "MQL5_NEWS_FILTER_EXTERNAL",
                message =
                    "The requested news filter remains external until a licensed calendar feed/MT5 calendar policy is explicitly connected."
            )
        }

        val stopExpression =
            stopDistanceExpression(
                specification,
                findings
            )

        val targetExpression =
            targetDistanceExpression(
                specification,
                findings
            )

        val source = buildString {
            appendLine(
                "// Scalper Pro MQL5 / MetaTrader 5 generator"
            )
            appendLine(
                "// Generated from a validated Strategy Specification."
            )
            appendLine(
                "// Verification: static analysis only; MetaEditor compile not performed."
            )
            appendLine(
                "// Educational use: compile in MetaEditor and test in Strategy Tester before any live use."
            )
            appendLine("#property strict")
            appendLine(
                "#include <Trade/Trade.mqh>"
            )
            appendLine()
            appendLine("CTrade trade;")
            appendLine(
                "input double RiskPercent = " +
                    number(
                        specification.risk
                            .riskPercentPerTrade
                    ) +
                    ";"
            )
            appendLine(
                "input int CooldownBars = " +
                    specification.cooldownBars +
                    ";"
            )
            appendLine(
                "input int MaxSpreadPoints = 30;"
            )
            appendLine(
                "input int SlippagePoints = 20;"
            )
            appendLine()
            appendLine(
                "datetime g_lastBarTime = 0;"
            )
            appendLine(
                "int g_barsSinceEntry = 1000000;"
            )
            appendLine()
            appendLine(
                "bool IsNewBar()"
            )
            appendLine("{")
            appendLine(
                "   datetime current = iTime(_Symbol, PERIOD_CURRENT, 0);"
            )
            appendLine(
                "   if(current <= 0 || current == g_lastBarTime) return false;"
            )
            appendLine(
                "   g_lastBarTime = current;"
            )
            appendLine(
                "   g_barsSinceEntry++;"
            )
            appendLine(
                "   return true;"
            )
            appendLine("}")
            appendLine()
            appendIndicatorHelpers()
            appendLine()
            appendRiskHelpers()
            appendLine()
            appendLine(
                "bool SpreadOk()"
            )
            appendLine("{")
            appendLine(
                "   long spread = SymbolInfoInteger(_Symbol, SYMBOL_SPREAD);"
            )
            appendLine(
                "   return spread >= 0 && spread <= MaxSpreadPoints;"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "bool LongSignal()"
            )
            appendLine("{")
            appendLine(
                "   return " + longSignal + ";"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "bool ShortSignal()"
            )
            appendLine("{")
            appendLine(
                "   return " + shortSignal + ";"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "bool ExitSignal()"
            )
            appendLine("{")
            appendLine(
                "   return " + exitSignal + ";"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "double StopDistance()"
            )
            appendLine("{")
            appendLine(
                "   return " +
                    (
                        stopExpression ?:
                            "0.0"
                    ) +
                    ";"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "double TargetDistance(double stopDistance)"
            )
            appendLine("{")
            appendLine(
                "   return " +
                    (
                        targetExpression ?:
                            "0.0"
                    ) +
                    ";"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "int OnInit()"
            )
            appendLine("{")
            appendLine(
                "   trade.SetDeviationInPoints(SlippagePoints);"
            )
            appendLine(
                "   return(INIT_SUCCEEDED);"
            )
            appendLine("}")
            appendLine()
            appendLine(
                "void OnTick()"
            )
            appendLine("{")
            appendLine(
                "   if(!IsNewBar()) return;"
            )
            appendLine(
                "   if(!SpreadOk()) return;"
            )
            appendLine()
            appendLine(
                "   if(PositionSelect(_Symbol))"
            )
            appendLine("   {")
            appendLine(
                "      if(ExitSignal())"
            )
            appendLine("      {")
            appendLine(
                "         trade.PositionClose(_Symbol);"
            )
            appendLine(
                "         return;"
            )
            appendLine("      }")
            appendLine(
                "      return;"
            )
            appendLine("   }")
            appendLine()
            appendLine(
                "   if(g_barsSinceEntry <= CooldownBars) return;"
            )
            appendLine()
            appendLine(
                "   double stopDistance = StopDistance();"
            )
            appendLine(
                "   if(stopDistance <= 0.0) return;"
            )
            appendLine(
                "   double targetDistance = TargetDistance(stopDistance);"
            )
            appendLine(
                "   double volume = CalculateRiskVolume(stopDistance);"
            )
            appendLine(
                "   if(volume <= 0.0) return;"
            )
            appendLine()
            appendLine(
                "   int digits = (int)SymbolInfoInteger(_Symbol, SYMBOL_DIGITS);"
            )
            appendLine(
                "   double ask = SymbolInfoDouble(_Symbol, SYMBOL_ASK);"
            )
            appendLine(
                "   double bid = SymbolInfoDouble(_Symbol, SYMBOL_BID);"
            )
            appendLine()
            appendLine(
                "   if(LongSignal())"
            )
            appendLine("   {")
            appendLine(
                "      double sl = NormalizeDouble(ask - stopDistance, digits);"
            )
            appendLine(
                "      double tp = targetDistance > 0.0 ? NormalizeDouble(ask + targetDistance, digits) : 0.0;"
            )
            appendLine(
                "      if(trade.Buy(volume, _Symbol, 0.0, sl, tp, \"ScalperPro L\"))"
            )
            appendLine(
                "         g_barsSinceEntry = 0;"
            )
            appendLine(
                "      return;"
            )
            appendLine("   }")
            appendLine()
            appendLine(
                "   if(ShortSignal())"
            )
            appendLine("   {")
            appendLine(
                "      double sl = NormalizeDouble(bid + stopDistance, digits);"
            )
            appendLine(
                "      double tp = targetDistance > 0.0 ? NormalizeDouble(bid - targetDistance, digits) : 0.0;"
            )
            appendLine(
                "      if(trade.Sell(volume, _Symbol, 0.0, sl, tp, \"ScalperPro S\"))"
            )
            appendLine(
                "         g_barsSinceEntry = 0;"
            )
            appendLine("   }")
            appendLine("}")
            appendLine()
            appendLine(
                "// Symbol intent: " +
                    specification.symbol
            )
            appendLine(
                "// Timeframe intent: " +
                    specification.primaryTimeframe
            )
            appendLine(
                "// Direction: " +
                    specification.directionPermission.name
            )
            appendLine(
                "// Commission assumption used by Pine/backtest context: " +
                    number(
                        specification.execution
                            .commissionPercent
                    ) +
                    "%"
            )
            appendLine(
                "// MetaTrader commission is broker/account-specific and is not hardcoded into order logic."
            )
        }

        findings +=
            Mql5StaticAnalyzer.analyze(source)

        return CodeArtifact(
            language = CodeLanguage.MQL5,
            source = source,
            verificationStatus =
                VerificationStatus.STATIC_ANALYZED,
            findings =
                findings.distinctBy {
                    it.code
                }
        )
    }

    private fun translateConditions(
        side: String,
        conditions: List<StrategyCondition>,
        findings: MutableList<CodeFinding>,
        joinWith: String = " && "
    ): String {
        if (conditions.isEmpty()) {
            return "false"
        }

        val translated =
            conditions.mapIndexed {
                    index,
                    condition ->
                val result =
                    Mql5ExpressionTranslator
                        .translate(
                            condition.expression
                        )

                if (!result.supported) {
                    findings += CodeFinding(
                        severity = "WARNING",
                        code =
                            "MQL5_" +
                                side +
                                "_CONDITION_" +
                                (index + 1) +
                                "_UNSUPPORTED",
                        message =
                            result.reason ?:
                                "Condition cannot be translated safely."
                    )
                }

                result
            }

        if (
            translated.any {
                !it.supported ||
                    it.expression.isNullOrBlank()
            }
        ) {
            return "false"
        }

        return translated
            .mapNotNull {
                it.expression
            }
            .joinToString(joinWith)
            .ifBlank { "false" }
    }

    private fun stopDistanceExpression(
        specification: StrategySpecification,
        findings: MutableList<CodeFinding>
    ): String? =
        when (specification.stop.method) {
            StopMethod.FIXED_PRICE_DISTANCE ->
                specification.stop.value
                    ?.let(::number)

            StopMethod.ATR_MULTIPLE ->
                specification.stop.value
                    ?.let {
                        "ReadATR(14, 1) * " +
                            number(it)
                    }

            StopMethod.STRUCTURE -> {
                findings += CodeFinding(
                    severity = "WARNING",
                    code =
                        "MQL5_STRUCTURE_STOP_UNSUPPORTED",
                    message =
                        "Structure-based stop needs an explicit deterministic rule before MQL5 generation can trade."
                )
                null
            }

            StopMethod.CUSTOM -> {
                findings += CodeFinding(
                    severity = "WARNING",
                    code =
                        "MQL5_CUSTOM_STOP_REVIEW",
                    message =
                        "Custom Pine stop expressions are not inserted into MQL5 until they pass the supported-expression translator."
                )
                null
            }
        }

    private fun targetDistanceExpression(
        specification: StrategySpecification,
        findings: MutableList<CodeFinding>
    ): String? =
        when (
            specification.takeProfit.method
        ) {
            TakeProfitMethod.FIXED_R_MULTIPLE ->
                specification.takeProfit.value
                    ?.let {
                        "stopDistance * " +
                            number(it)
                    }

            TakeProfitMethod.FIXED_PRICE_DISTANCE ->
                specification.takeProfit.value
                    ?.let(::number)

            TakeProfitMethod.STRUCTURE -> {
                findings += CodeFinding(
                    severity = "INFO",
                    code =
                        "MQL5_STRUCTURE_TARGET_UNSUPPORTED",
                    message =
                        "Structure target needs an explicit deterministic expression before translation."
                )
                null
            }

            TakeProfitMethod.TRAILING -> {
                findings += CodeFinding(
                    severity = "INFO",
                    code =
                        "MQL5_TRAILING_TARGET_REVIEW",
                    message =
                        "Trailing logic needs a dedicated activation/offset contract; Scalper Pro will not invent it."
                )
                null
            }

            TakeProfitMethod.CUSTOM -> {
                findings += CodeFinding(
                    severity = "INFO",
                    code =
                        "MQL5_CUSTOM_TARGET_REVIEW",
                    message =
                        "Custom target expression is not translated until supported by the safe expression translator."
                )
                null
            }
        }

    private fun StringBuilder
        .appendIndicatorHelpers() {
        appendLine(
            "double CopySingleBuffer(int handle, int shift)"
        )
        appendLine("{")
        appendLine(
            "   if(handle == INVALID_HANDLE) return EMPTY_VALUE;"
        )
        appendLine(
            "   double values[];"
        )
        appendLine(
            "   ArraySetAsSeries(values, true);"
        )
        appendLine(
            "   int copied = CopyBuffer(handle, 0, shift, 1, values);"
        )
        appendLine(
            "   IndicatorRelease(handle);"
        )
        appendLine(
            "   if(copied != 1) return EMPTY_VALUE;"
        )
        appendLine(
            "   return values[0];"
        )
        appendLine("}")
        appendLine()
        appendLine(
            "double ReadMA(int period, int shift, ENUM_MA_METHOD method)"
        )
        appendLine("{")
        appendLine(
            "   int handle = iMA(_Symbol, PERIOD_CURRENT, period, 0, method, PRICE_CLOSE);"
        )
        appendLine(
            "   return CopySingleBuffer(handle, shift);"
        )
        appendLine("}")
        appendLine()
        appendLine(
            "double ReadRSI(int period, int shift)"
        )
        appendLine("{")
        appendLine(
            "   int handle = iRSI(_Symbol, PERIOD_CURRENT, period, PRICE_CLOSE);"
        )
        appendLine(
            "   return CopySingleBuffer(handle, shift);"
        )
        appendLine("}")
        appendLine()
        appendLine(
            "double ReadATR(int period, int shift)"
        )
        appendLine("{")
        appendLine(
            "   int handle = iATR(_Symbol, PERIOD_CURRENT, period);"
        )
        appendLine(
            "   return CopySingleBuffer(handle, shift);"
        )
        appendLine("}")
    }

    private fun StringBuilder
        .appendRiskHelpers() {
        appendLine(
            "double NormalizeVolume(double rawVolume)"
        )
        appendLine("{")
        appendLine(
            "   double minVolume = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MIN);"
        )
        appendLine(
            "   double maxVolume = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_MAX);"
        )
        appendLine(
            "   double step = SymbolInfoDouble(_Symbol, SYMBOL_VOLUME_STEP);"
        )
        appendLine(
            "   if(step <= 0.0) return 0.0;"
        )
        appendLine(
            "   double volume = MathFloor(rawVolume / step) * step;"
        )
        appendLine(
            "   volume = MathMax(minVolume, MathMin(maxVolume, volume));"
        )
        appendLine(
            "   return volume;"
        )
        appendLine("}")
        appendLine()
        appendLine(
            "double CalculateRiskVolume(double stopDistance)"
        )
        appendLine("{")
        appendLine(
            "   if(stopDistance <= 0.0 || RiskPercent <= 0.0) return 0.0;"
        )
        appendLine(
            "   double tickSize = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_SIZE);"
        )
        appendLine(
            "   double tickValue = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_VALUE_LOSS);"
        )
        appendLine(
            "   if(tickValue <= 0.0) tickValue = SymbolInfoDouble(_Symbol, SYMBOL_TRADE_TICK_VALUE);"
        )
        appendLine(
            "   if(tickSize <= 0.0 || tickValue <= 0.0) return 0.0;"
        )
        appendLine(
            "   double riskAmount = AccountInfoDouble(ACCOUNT_BALANCE) * RiskPercent / 100.0;"
        )
        appendLine(
            "   double lossPerLot = (stopDistance / tickSize) * tickValue;"
        )
        appendLine(
            "   if(lossPerLot <= 0.0) return 0.0;"
        )
        appendLine(
            "   return NormalizeVolume(riskAmount / lossPerLot);"
        )
        appendLine("}")
    }

    private fun number(
        value: Double
    ): String =
        if (value % 1.0 == 0.0) {
            value.toLong()
                .toString() +
                ".0"
        } else {
            value.toString()
        }
}

object Mql5StaticAnalyzer {
    fun analyze(
        source: String
    ): List<CodeFinding> {
        val findings =
            mutableListOf<CodeFinding>()
        val lowered = source.lowercase()
        val hasTrading =
            "trade.buy(" in lowered ||
                "trade.sell(" in lowered ||
                "ordersend" in lowered

        if (
            hasTrading &&
            "riskpercent" !in lowered
        ) {
            findings += CodeFinding(
                severity = "ERROR",
                code =
                    "MQL5_RISK_CHECK_MISSING",
                message =
                    "Trading logic detected without an obvious risk input."
            )
        }

        if (
            hasTrading &&
            "spreadok" !in lowered
        ) {
            findings += CodeFinding(
                severity = "WARNING",
                code =
                    "MQL5_SPREAD_FILTER_REVIEW",
                message =
                    "Trading logic should include an explicit spread filter."
            )
        }

        if (
            hasTrading &&
            "positionselect" !in lowered
        ) {
            findings += CodeFinding(
                severity = "WARNING",
                code =
                    "MQL5_DUPLICATE_POSITION_REVIEW",
                message =
                    "Trading logic lacks an obvious existing-position guard."
            )
        }

        if (
            hasTrading &&
            "#include <trade/trade.mqh>" !in
                lowered
        ) {
            findings += CodeFinding(
                severity = "ERROR",
                code =
                    "MQL5_CTRADE_INCLUDE_MISSING",
                message =
                    "CTrade calls require Trade.mqh."
            )
        }

        if (
            (
                "ima(" in lowered ||
                    "irsi(" in lowered ||
                    "iatr(" in lowered
            ) &&
            "copybuffer" !in lowered
        ) {
            findings += CodeFinding(
                severity = "WARNING",
                code =
                    "MQL5_INDICATOR_BUFFER_REVIEW",
                message =
                    "Indicator handles are present without an obvious CopyBuffer path."
            )
        }

        if ("ontick" !in lowered) {
            findings += CodeFinding(
                severity = "INFO",
                code = "MQL5_NO_ONTICK",
                message =
                    "No OnTick handler detected."
            )
        }

        if ("isnewbar" !in lowered) {
            findings += CodeFinding(
                severity = "INFO",
                code =
                    "MQL5_BAR_CONFIRMATION_REVIEW",
                message =
                    "No new-bar gate was detected. Confirm whether intrabar execution is intentional."
            )
        }

        return findings.distinctBy {
            it.code
        }
    }
}
